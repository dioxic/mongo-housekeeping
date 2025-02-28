package com.mongodb.housekeeping.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class Log(
    @Contextual
    val ts: Instant,
    val msg: String
)
