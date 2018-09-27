package dk.sdu.cloud.auth.services

import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNotNull
import org.junit.Test
import kotlin.test.assertTrue

class QRServiceTest {
    @Test
    fun `test encoding of message`() {
        val service = ZXingQRService()
        val encode = service.encode("Hello, World!", 100, 100)
        assertNotNull(encode)
        assertEquals(100, encode.width)
        assertEquals(100, encode.height)
    }

    @Test
    fun `test conversion to data URI`() {
        val service = ZXingQRService()
        val encode = service.encode("Hello, World!", 100, 100)
        val dataUri = encode.toDataURI()
        assertTrue(dataUri.startsWith("data:image/png;base64,"))
    }
}