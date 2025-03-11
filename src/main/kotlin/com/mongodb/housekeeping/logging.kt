package com.mongodb.housekeeping

import com.mongodb.housekeeping.model.Config
import com.mongodb.housekeeping.model.Enabled
import com.mongodb.housekeeping.model.Rate
import com.mongodb.housekeeping.model.ServerStatus
import com.mongodb.housekeeping.model.Window
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger("LOG")

class HousekeepingLogger(
    val coroutineScope: CoroutineScope,
    val db: MongoDatabase
) {
    inline fun <reified T : Any> monitor(state: StateFlow<T>, includeStateChanges: Boolean, name: String?) {
        coroutineScope.launch {
            val name = name ?: T::class.simpleName
            state
                .onStart { log("Monitoring started for $name") }
                .onCompletion { log("Monitoring terminated for $name") }
                .filter { includeStateChanges }
                .map { toMessage(it) }
                .collect(::log)
        }
    }

    fun toMessage(input: Any) =
        when (input) {
            is Window -> "Housekeeping window: $input"
            is Rate -> "Housekeeping rate change: $input"
            is Config -> "Config changed: $input"
            is Enabled -> "Housekeeping: $input"
            is ServerStatus.Opcounters -> "Opcounters: $input"
            else -> throw IllegalArgumentException("Message generation not supported for ${input::class}")
        }

    suspend fun log(msg: String) {
        db.log(msg)
        logger.info { msg }
    }
}

inline fun <reified T : Any> StateFlow<T>.withLogging(
    logger: HousekeepingLogger,
    includeStateChanges: Boolean = true,
    name: String? = null
) = also {
    logger.monitor(this, includeStateChanges, name)
}