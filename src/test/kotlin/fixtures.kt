import com.mongodb.client.model.Filters
import com.mongodb.housekeeping.CollectionConfig
import com.mongodb.housekeeping.Config
import com.mongodb.housekeeping.HousekeepingWindow
import com.mongodb.housekeeping.RateConfig
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime


val basicConfig: Config = Config(
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
    window = listOf(
        HousekeepingWindow(
            from = LocalTime(0, 0, 0, 0),
            to = LocalTime(5, 0, 0, 0),
            days = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        )
    ),
)