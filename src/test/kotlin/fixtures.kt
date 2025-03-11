import com.mongodb.housekeeping.model.Config
import kotlinx.coroutines.flow.flow
import org.bson.Document
import org.bson.types.ObjectId
import kotlin.random.Random


val basicConfig: Config = Config.default

private val status = listOf("OPEN", "CLOSED")

fun createTestDataA(id: Any = ObjectId.get()) =
    Document(
        mapOf(
            "_id" to id,
            "field" to Random.nextInt(10),
            "status" to status.random()
        )
    )

fun createTestDataB(id: Any = ObjectId.get()) =
    Document(
        mapOf(
            "fk" to id,
            "field" to Random.nextInt(10),
            "status" to status.random()
        )
    )

fun testDataFlowA() = flow {
    var id = 0
    while (true) {
        emit(createTestDataA(id))
        id++
    }
}

fun testDataFlowB() = flow {
    var id = 0
    while (true) {
        emit(createTestDataB(id))
        id++
    }
}