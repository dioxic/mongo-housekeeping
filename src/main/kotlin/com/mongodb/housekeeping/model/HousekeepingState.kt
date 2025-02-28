package com.mongodb.housekeeping.model

import kotlinx.serialization.Serializable

@Serializable
data class HousekeepingState(
    val enabled: Boolean,
    val window: String,
    val rate: Int
)
