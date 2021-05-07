package dk.sdu.cloud

import dk.sdu.cloud.calls.client.urlEncode
import kotlin.test.Test
import kotlin.test.assertEquals

class UrlEncodeTest {
    @Test
    fun `test url encoding`() {
        assertEquals(
            "%E2%82%ACHello%20World%E2%82%AC".toLowerCase(),
            urlEncode("€Hello World€").toLowerCase()
        )
    }
}
