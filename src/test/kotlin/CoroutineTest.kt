import com.mongodb.housekeeping.batchMerge
import com.mongodb.housekeeping.model.Rate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class CoroutineTest {

    @Test
    fun testDocMerge(): Unit = runBlocking {
        val rateState = flowOf(Rate(3)).stateIn(this)

        val actual = flowOf(
            mapOf(
                "a" to listOf(4, 5),
                "b" to listOf(1, 2, 3)
            ),
            mapOf(
                "a" to 5,
                "b" to emptyList<Int>()
            ),
            mapOf(
                "a" to 6,
                "b" to listOf(111, 222, 333)
            ),
            mapOf(
                "a" to 7,
                "b" to listOf(4, 5, 6)
            )
        ).batchMerge(rateState).toList()

        val expected = listOf(
            mapOf(
                "a" to setOf(4, 5, 6),
                "b" to setOf(1, 2, 3, 111, 222, 333)
            ),
            mapOf(
                "a" to setOf(7),
                "b" to setOf(4, 5, 6)
            ),
        )

        assertEquals(expected, actual)

    }

}