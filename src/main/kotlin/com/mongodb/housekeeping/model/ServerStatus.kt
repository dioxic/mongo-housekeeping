package com.mongodb.housekeeping.model

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.time.Duration

@Serializable
data class ServerStatus(
    val opcounters: Opcounters
) {
    @Serializable
    data class Opcounters(
        val insert: Long,
        val query: Long,
        val update: Long,
        val delete: Long,
        val getmore: Long,
        val command: Long
    ) {
        fun getByKey(key: String) =
            when(key) {
                "insert" -> insert
                "query" -> query
                "update" -> update
                "delete" -> delete
                "getmore" -> getmore
                "command" -> command
                else -> throw IllegalArgumentException("$key is not a valid key")
            }

        fun toRate(other: Opcounters, duration: Duration) =
            Opcounters(
                insert = abs(other.insert - insert) / duration.inWholeSeconds,
                query = abs(other.query - query) / duration.inWholeSeconds,
                update = abs(other.update - update) / duration.inWholeSeconds,
                delete = abs(other.delete - delete) / duration.inWholeSeconds,
                getmore = abs(other.getmore - getmore) / duration.inWholeSeconds,
                command = abs(other.command - command) / duration.inWholeSeconds,
            )
    }
}
