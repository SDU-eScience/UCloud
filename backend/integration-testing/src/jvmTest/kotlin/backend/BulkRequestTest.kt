package dk.sdu.cloud.integration.backend

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.defaultMapper
import kotlin.test.Test
import kotlin.test.assertEquals

class BulkRequestTest {
    data class Wrapper(val number: Int)

    @Test
    fun testSerialization() {
        val req = bulkRequestOf(Wrapper(42))
        val content = defaultMapper.writeValueAsString(req)
        println(content)
        assertEquals(req, defaultMapper.readValue(content))

        assertEquals(req, defaultMapper.readValue("""{"number": 42}"""))
    }
}