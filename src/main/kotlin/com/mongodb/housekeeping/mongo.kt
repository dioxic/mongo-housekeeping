package com.mongodb.housekeeping

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.housekeeping.model.HousekeepingState
import com.mongodb.housekeeping.model.Log
import com.mongodb.housekeeping.model.ServerStatus
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.datetime.Clock
import org.bson.Document

suspend fun MongoDatabase.log(msg: String) {
    getCollection<Log>("log").insertOne(Log(Clock.System.now(), msg))
}

suspend fun MongoDatabase.saveState(state: HousekeepingState) {
    getCollection<HousekeepingState>("state")
        .replaceOne(Filters.eq("_id", "STATE"), state, ReplaceOptions().upsert(true))
}

suspend fun MongoDatabase.serverStatus() =
    runCommand<ServerStatus>(
        Document(
            mapOf(
                "serverStatus" to 1,
                "repl" to 0,
                "metrics" to 0,
                "locks" to 0,
                "wiredTiger" to 0,
                "tcmalloc" to 0,
                "twoPhaseCommitCoordinator" to 0,
                "transportSecurity" to 0
            )
        )
    )