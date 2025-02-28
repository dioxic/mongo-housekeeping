import com.mongodb.housekeeping.model.Config
import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class ModelTest {

    @Test
    fun writeConfig(): Unit = runBlocking {
        val client = MongoClient.create()

        client
            .getDatabase("housekeeping")
            .getCollection<Config>("config")
            .insertOne(basicConfig)

    }

}