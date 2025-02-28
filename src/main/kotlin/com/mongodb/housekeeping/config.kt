package com.mongodb.housekeeping

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.BsonDocument

@Serializable
data class Config(
    val collections: List<CollectionConfig>,
    val archiveEnabled: Boolean,
    val rates: List<RateConfig>,
    val window: List<HousekeepingWindow>
)

@Serializable
data class HousekeepingWindow(
    val from: LocalTime,
    val to: LocalTime,
    val days: List<DayOfWeek>
)

@Serializable
data class RateConfig(
    val rate: Int,
    val criteria: List<MetricThreshold>
) {
    @Serializable
    data class MetricThreshold(
        val metric: String,
        val min: Int,
        val max: Int
    )
}

@Serializable
data class CollectionConfig(
    val namespace: String,
    @Contextual
    val criteria: BsonDocument
)