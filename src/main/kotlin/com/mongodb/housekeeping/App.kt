package com.mongodb.housekeeping

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.mongodb.client.model.Filters
import com.mongodb.housekeeping.model.Config
import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.runBlocking
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
        val serverStatus = serverStatusState(db, 10.seconds).withLogging(logger, includeStateChanges = false)
        val opcounterState = opcounterState(serverStatus).withLogging(logger)
        val rateState = rateState(cfgState, opcounterState).withLogging(logger)
        val hkEnabled = housekeepingEnabled(cfgState, windowState, rateState).withLogging(logger)
        housekeepingState(hkEnabled, windowState, rateState, opcounterState).collect {
            db.saveState(it)
        }

    }
}

suspend fun main(args: Array<String>) {
    App().main(args)
}