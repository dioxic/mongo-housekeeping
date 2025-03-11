package com.mongodb.housekeeping.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class HousekeepingState(
    val enabled: Boolean,
    val window: String?,
    val rate: Int,
    val dbMetrics: ServerStatus.Opcounters?,
    @Contextual
    val lastUpdated: LocalDateTime?
) {
    companion object {
        val default = HousekeepingState(
            enabled = false,
            window = null,
            rate = 0,
            dbMetrics = null,
            lastUpdated = null
        )
    }
}
