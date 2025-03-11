import com.mongodb.client.model.Indexes
import com.mongodb.housekeeping.model.Config
import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import org.bson.Document
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelTest {

    @Test
    fun writeConfig(): Unit = runBlocking {
        val client = MongoClient.create()

        client
            .getDatabase("housekeeping")
            .getCollection<Config>("config")
            .insertOne(basicConfig)

    }

    @Test
    fun writeTestData() = runBlocking {
        val db = MongoClient.create().getDatabase("test")
        val collA = db.getCollection<Document>("a")
        val collB = db.getCollection<Document>("b")

        collA.drop()
        collB.drop()
        collA.createIndex(Indexes.ascending("field", "status"))
        collB.createIndex(Indexes.ascending("fk", "status"))
        collB.createIndex(Indexes.ascending("field", "status"))

        testDataFlowA()
            .take(1000)
            .chunked(100)
            .collect {
                collA.insertMany(it)
            }

        testDataFlowB()
            .take(1000)
            .chunked(100)
            .collect {
                collB.insertMany(it)
            }

    }

}