package com.mongodb.housekeeping

import com.mongodb.ExplainVerbosity
import com.mongodb.MongoNamespace
import com.mongodb.client.model.Filters
import com.mongodb.housekeeping.model.CriteriaConfig
import com.mongodb.housekeeping.model.Rate
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.bson.BsonDocument
import org.bson.Document
import org.bson.json.JsonWriterSettings
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun CoroutineScope.housekeepingJob(
    client: MongoClient,
    criteria: CriteriaConfig,
    rate: StateFlow<Rate>,
    jobStatus: MutableStateFlow<String?>,
    logger: HousekeepingLogger
) {
    criteria.simple.forEach { cfg ->
        jobStatus.value = "Processing simple criteria for ${cfg.namespace}"
        logger.log("Processing simple criteria for ${cfg.namespace}")
        val collection = client.getMongoCollection<Document>(cfg.namespace)
        val exPlan = collection.explainPlan(cfg.query)

        if (exPlan.hasSupportingIndex()) {
            collection
                .find(cfg.query)
                .chunked(rate)
                .onEach { delay(1.seconds) }
                .collect { docs ->
                    val ids = docs.map { it["_id"] }
                    val filter = Filters.and(cfg.query, Filters.`in`("_id", ids))
                    val count = collection.deleteMany(filter).deletedCount
                    logger.log("deleted $count documents from ${cfg.namespace}")
                }
            logger.log("Processing complete for ${cfg.namespace}")
        } else {
            val jws = JsonWriterSettings.builder().indent(true).build()
            val exJson = exPlan.toJson(jws)
            logger.log(
                """
                |housekeeping terminated for ${cfg.namespace} - no index for criteria
                |explain plan: $exJson
                """.trimMargin()
            )
        }
    }

    criteria.pipeline.forEach { cfg ->
        jobStatus.value = "Processing agg criteria for ${cfg.db}.${cfg.rootCollection}"
        logger.log("Processing agg criteria for ${cfg.db}.${cfg.rootCollection}")
        val db = client.getDatabase(cfg.db)
        val rootCollection = db.getCollection<Document>(cfg.rootCollection)
        val exPlan = rootCollection.explainPlan(cfg.query)

        if (exPlan.hasSupportingIndex()) {
            rootCollection
                .aggregate(cfg.query)
                .map { it.filterKeys { it != "_id" } }
                .batchMerge(rate)
                .onEach { delay(1.seconds) }
                .collect { idMap ->
                    val session = client.startSession()
                    try {
                        session.startTransaction()
                        idMap.map { (coll, ids) ->
                            val mdbCollection = db.getCollection<Document>(coll)
                            coll to mdbCollection.deleteMany(session, Filters.`in`("_id", ids)).deletedCount
                        }.also {
                            session.commitTransaction()
                        }
                    } catch (e: Exception) {
                        session.abortTransaction()
                        logger.log("Transaction aborted due to ${e.message}")
                        null
                    }?.also {
                        it.forEach { (collName, count) ->
                            logger.log("deleted $count documents from $collName")
                        }
                    }
                }
            logger.log("Processing complete for ${cfg.db}.${cfg.rootCollection} agg")
        } else {
            val jws = JsonWriterSettings.builder().indent(true).build()
            val exJson = exPlan.toJson(jws)
            logger.log(
                """
                |housekeeping terminated for ${cfg.db}.${cfg.rootCollection} - no index for criteria
                |explain plan: $exJson
                """.trimMargin()
            )
        }
    }
    jobStatus.value = "Processing complete"
}

typealias ExPlan = Document

suspend fun MongoCollection<Document>.explainPlan(criteria: BsonDocument): ExPlan =
    find(criteria).explain(ExplainVerbosity.QUERY_PLANNER)

suspend fun MongoCollection<Document>.explainPlan(criteria: List<BsonDocument>): ExPlan =
    aggregate(criteria).explain(ExplainVerbosity.QUERY_PLANNER)

fun ExPlan.hasSupportingIndex(): Boolean =
    get<Document>("queryPlanner", Document::class.java)
        ?.get<Document>("winningPlan", Document::class.java)
        ?.getString("stage") != "COLLSCAN"

inline fun <reified T : Any> MongoClient.getMongoCollection(ns: String) =
    MongoNamespace(ns).let { ns ->
        getDatabase(ns.databaseName).getCollection<T>(ns.collectionName)
    }