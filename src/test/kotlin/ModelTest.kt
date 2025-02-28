import com.mongodb.housekeeping.Config
import com.mongodb.housekeeping.HousekeepingWindow
import com.mongodb.kotlin.client.MongoClient
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test

class ModelTest {

    @Test
    fun writeConfig() {
        val client = MongoClient.create()

        client
            .getDatabase("test")
            .getCollection<Config>("config")
            .insertOne(basicConfig)

//        client.getDatabase("test")
//            .getCollection<HousekeepingWindow>("window")
//            .insertOne(
//                HousekeepingWindow(
//                    from = LocalTime(0, 0, 0, 0),
//                    to = LocalTime(5, 0, 0, 0),
//                    days = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
//                )
//            )

    }

}