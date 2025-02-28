import com.mongodb.client.model.Filters
import com.mongodb.housekeeping.model.CollectionConfig
import com.mongodb.housekeeping.model.Config
import com.mongodb.housekeeping.model.RateConfig
import com.mongodb.housekeeping.model.WindowConfig
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime


val basicConfig: Config = Config(
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
                    metric = "inserts",
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