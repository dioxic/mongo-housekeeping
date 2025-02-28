package com.mongodb.housekeeping

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.mongodb.housekeeping.model.Config
import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class App : SuspendingCliktCommand() {
    val mongoUri by option(help = "MongoDB connection string").default("mongodb://localhost:27017")
    val housekeepingDatabase by option("--db", help = "The housekeeping database").default("housekeeping")

    override suspend fun run() = runBlocking(Dispatchers.IO) {
        val client = MongoClient.create(mongoUri)
        val db = client.getDatabase(housekeepingDatabase)
        val cfgCollection = db.getCollection<Config>("config")
        val monitorScope = CoroutineScope(Dispatchers.IO)
        val logger = HousekeepingLogger(monitorScope, db)

        val cfgState = configState(cfgCollection).withLogging(logger)
        val windowState = windowState(cfgState).withLogging(logger)
        val serverStatus = serverStatusState(db)
        val opcounterState = opcounterState(serverStatus)
        val rateState = rateState(cfgState, opcounterState).withLogging(logger)
        val hkEnabled = housekeepingEnabled(cfgState, windowState, rateState).withLogging(logger)
        val hkState = housekeepingState(hkEnabled, windowState, rateState).collect {
            db.saveState(it)
        }

    }
}

suspend fun main(args: Array<String>) {
    App().main(args)
}