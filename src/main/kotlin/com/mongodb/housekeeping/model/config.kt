package com.mongodb.housekeeping.model

import com.mongodb.client.model.Filters
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.BsonDocument

@Serializable
data class Config(
    val collections: List<CollectionConfig>,
    val archiveEnabled: Boolean,
    val housekeepingEnabled: Boolean,
    val rates: List<RateConfig>,
    val windows: List<WindowConfig>? = null
) {
    @SerialName("_id")
    val id: String = "CONFIG"

    companion object {
        val default = Config(
            housekeepingEnabled = false,
            collections = listOf(
                CollectionConfig(
                    namespace = "test.a",
                    criteria = Filters.gt("field", 5).toBsonDocument()
                )
            ),
            archiveEnabled = false,
            rates = listOf(
                RateConfig(
                    rate = 5,
                    criteria = listOf(
                        RateConfig.MetricThreshold(
                            metric = "insert",
                            min = 0,
                            max = 100
                        )
                    )
                )
            ),
            windows = listOf(
                WindowConfig(
                    from = LocalTime(0, 0, 0, 0),
                    to = LocalTime(5, 0, 0, 0),
                    days = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                )
            ),
        )
    }
}

@Serializable
data class WindowConfig(
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