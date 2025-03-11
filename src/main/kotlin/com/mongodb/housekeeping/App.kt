package com.mongodb.housekeeping

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.mongodb.client.model.Filters
import com.mongodb.housekeeping.model.Config
import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.count
import kotlin.time.Duration.Companion.seconds

class App : SuspendingCliktCommand() {
    val mongoUri by option(help = "MongoDB connection string").default("mongodb://localhost:27017")
    val housekeepingDatabase by option("--db", help = "The housekeeping database").default("housekeeping")

    override suspend fun run() = runBlocking(Dispatchers.IO) {
        val client = MongoClient.create(mongoUri)
        val db = client.getDatabase(housekeepingDatabase)
        val cfgCollection = db.getCollection<Config>("config")
        val monitorScope = CoroutineScope(Dispatchers.IO)
        val logger = HousekeepingLogger(monitorScope, db)

        if (cfgCollection.find(Filters.eq("_id", Config.default.id)).count() == 0) {
            logger.log("No config found - creating default config")
            cfgCollection.insertOne(Config.default)
        }

        val cfgState = configState(cfgCollection).withLogging(logger)
        val windowState = windowState(cfgState).withLogging(logger)
        val serverStatusState = this.serverStatusState(db, 5.seconds).withLogging(logger, includeStateChanges = false)
        val opcounterState = opcounterState(serverStatusState)
        val rateState = rateState(cfgState, opcounterState).withLogging(logger)
        val enabledState = housekeepingEnabled(cfgState, windowState, rateState).withLogging(logger)
        val enabledAndCfgState = enabledAndCfgCombined(cfgState, enabledState)

        var hkJob: Job? = null

        launch {
            enabledAndCfgState.collect { (collCfg, enabled) ->
                if (hkJob.isRunning()) {
                    logger.log("Stopping housekeeping job")
                    hkJob?.cancel()
                }
                if (enabled.value) {
                    logger.log("Starting housekeeping job")
                    hkJob = launch {
                        housekeepingJob(client, collCfg, rateState, logger)
                    }
                }
            }
        }

        // save state
        housekeepingState(enabledState, windowState, rateState, opcounterState).collect {
            db.saveState(it)
        }
    }
}

fun Job?.isRunning() = this?.isActive == true

suspend fun main(args: Array<String>) {
    App().main(args)
}