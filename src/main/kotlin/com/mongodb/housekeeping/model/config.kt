package com.mongodb.housekeeping.model

import com.mongodb.client.model.Aggregates.*
import com.mongodb.client.model.Aggregates.match
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.gt
import com.mongodb.client.model.Projections.computed
import com.mongodb.client.model.Projections.fields
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.BsonDocument

@Serializable
data class Config(
    val criteria: CriteriaConfig,
    val archive: Boolean?,
    val enabled: Boolean,
    val rates: List<RateConfig>,
    val windows: List<WindowConfig>?
) {
    @SerialName("_id")
    val id: String = "CONFIG"

    companion object {
        val default = Config(
            enabled = false,
            criteria = CriteriaConfig(
                simple = listOf(
                    SimpleCriteria(
                        namespace = "test.a",
                        query = and(gt("field", 9), eq("status", "CLOSED")).toBsonDocument()
                    ),
                    SimpleCriteria(
                        namespace = "test.b",
                        query = and(gt("field", 8), eq("status", "CLOSED")).toBsonDocument()
                    )
                ),
                pipeline = listOf(
                    AggCriteria(
                        db = "test",
                        rootCollection = "a",
                        query = listOf(
                            match(gt("field", 5)),
                            lookup("b", "_id", "fk", "b"),
                            project(
                                fields(
                                    computed("a", "\$_id"),
                                    computed("b", "\$b._id")
                                )
                            )
                        ).map { it.toBsonDocument() }
                    )
                )
            ),
            archive = null,
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
                    to = LocalTime(14, 0, 0, 0),
                    days = listOf(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY)
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
data class CriteriaConfig(
    val simple: List<SimpleCriteria>,
    val pipeline: List<AggCriteria>
)

@Serializable
data class SimpleCriteria(
    val namespace: String,
    @Contextual val query: BsonDocument
)

@Serializable
data class AggCriteria(
    val db: String,
    val rootCollection: String,
    val query: List<@Contextual BsonDocument>
)