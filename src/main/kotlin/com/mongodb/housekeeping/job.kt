package com.mongodb.housekeeping

import com.mongodb.ExplainVerbosity
import com.mongodb.MongoNamespace
import com.mongodb.client.model.Filters
import com.mongodb.housekeeping.model.CollectionConfig
import com.mongodb.housekeeping.model.Config
import com.mongodb.housekeeping.model.Enabled
import com.mongodb.housekeeping.model.Rate
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.bson.BsonDocument
import org.bson.Document
import org.bson.json.JsonWriterSettings
import kotlin.time.Duration.Companion.seconds

fun CoroutineScope.launchHousekeepingJob(
    client: MongoClient,
    cfgState: StateFlow<Config>,
    rateState: StateFlow<Rate>,
    enabledState: StateFlow<Enabled>,
    logger: HousekeepingLogger
) = launch {
    var housekeepingJob: Job? = null
    enabledState.collect { enabled ->
        if (enabled.value) {
            if (housekeepingJob.isRunning() == false) {
                logger.log("Starting housekeeping job")
                housekeepingJob = launch {
                    housekeepingJob(client, cfgState.value.collections, rateState, logger)
                }
            }
        } else if (housekeepingJob.isRunning()) {
            logger.log("Stopping housekeeping job")
            housekeepingJob?.cancel()
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun CoroutineScope.housekeepingJob(
    client: MongoClient,
    collectionConfigs: List<CollectionConfig>,
    rate: StateFlow<Rate>,
    logger: HousekeepingLogger
) {
    collectionConfigs.forEach { cfg ->
        logger.log("Processing ${cfg.namespace}")
        val collection = client.getMongoCollection<Document>(cfg.namespace)
        val exPlan = collection.explainPlan(cfg.criteria)

        if (exPlan.hasSupportingIndex(cfg.criteria)) {
            collection
                .find(cfg.criteria)
                .chunked(rate)
                .onEach { delay(1.seconds) }
                .collect { docs ->
                    val ids = docs.map { it["_id"] }
                    val count = collection.deleteMany(Filters.`in`("_id", ids)).deletedCount
                    println("deleted $count documents from ${cfg.namespace}")
                }
            logger.log("Processing complete for ${cfg.namespace}")
        } else {
            val jws = JsonWriterSettings.builder().indent(true).build()
            val exJson = exPlan.toJson(jws)
            logger.log("""
                |housekeeping terminated for ${cfg.namespace} - no index for criteria
                |explain plan: $exJson
                """.trimMargin()
            )
        }

    }
}

typealias ExPlan = Document

suspend fun MongoCollection<Document>.explainPlan(criteria: BsonDocument): ExPlan =
    find(criteria).explain(ExplainVerbosity.QUERY_PLANNER)

fun ExPlan.hasSupportingIndex(criteria: BsonDocument): Boolean =
    get<Document>("queryPlanner", Document::class.java)
        ?.get<Document>("winningPlan", Document::class.java)
        ?.getString("stage") != "COLLSCAN"

suspend fun MongoCollection<Document>.hasSupportingIndex(criteria: BsonDocument): Boolean {
    val queryPlan = find(criteria).explain(ExplainVerbosity.QUERY_PLANNER)
    val isCollscan = queryPlan
        .get<Document>("queryPlanner", Document::class.java)
        ?.get<Document>("winningPlan", Document::class.java)
        ?.getString("stage") == "COLLSCAN"

//    val jws = JsonWriterSettings.builder().indent(true).build()
//    println(queryPlan.toBsonDocument().toJson(jws))
//    println("isCollscan = $isCollscan")

    return !isCollscan
}

inline fun <reified T : Any> MongoClient.getMongoCollection(ns: String) =
    MongoNamespace(ns).let { ns ->
        getDatabase(ns.databaseName).getCollection<T>(ns.collectionName)
    }