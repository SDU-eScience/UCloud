package dk.sdu.cloud.sync
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import kotlin.test.assertEquals
import kotlin.test.assertFalse


class CappedInputTest {

    @Test
    fun `read() test`() {
        val input = "Th"
        val inputStream = ByteArrayInputStream(input.toByteArray(Charset.defaultCharset()))
        val cap = CappedInputStream(inputStream, 2000)
        val returned = cap.read()
        assertEquals(input[0].toInt(), returned)
        val returned2 = cap.read()
        assertEquals(input[1].toInt(), returned2)
        val returned3 = cap.read()
        assertEquals(-1, returned3)
    }

    @Test
    fun `read(bytearray) test`() {
        val input = "This is a string"
        val inputStream = ByteArrayInputStream(input.toByteArray(Charset.defaultCharset()))
        val cap = CappedInputStream(inputStream, 2000)
        val returned = cap.read(inputStream.readBytes())
        assertEquals(-1, returned)
    }

    @Test
    fun `skip(Long) test`() {
        val input = "This is a string"
        val inputStream = ByteArrayInputStream(input.toByteArray(Charset.defaultCharset()))
        val cap = CappedInputStream(inputStream, 2000)
        val returned = cap.read()
        assertEquals(input[0].toInt(), returned)
        cap.skip(3)
        val returned2 = cap.read()
        //Should have moved 3 indexes forward
        assertEquals(input[4].toInt(), returned2)
    }

    @Test
    fun `available() and close test`() {
        val input = "This is a string"
        val inputStream = ByteArrayInputStream(input.toByteArray(Charset.defaultCharset()))
        val cap = CappedInputStream(inputStream, 2000)
        val returned = cap.available()
        assertEquals(input.length, returned)
        cap.skip(3)
        val returned2 = cap.available()
        assertEquals(input.length-3, returned2)
        cap.close()
    }

    @Test (expected = UnsupportedOperationException::class)
    fun `reset() test`() {
        val input = "This is a string"
        val inputStream = ByteArrayInputStream(input.toByteArray(Charset.defaultCharset()))
        val cap = CappedInputStream(inputStream, 2000)
        cap.reset()
    }

    @Test (expected = UnsupportedOperationException::class)
    fun `mark() test`() {
        val input = "This is a string"
        val inputStream = ByteArrayInputStream(input.toByteArray(Charset.defaultCharset()))
        val cap = CappedInputStream(inputStream, 2000)
        val isSupported = cap.markSupported()
        assertFalse(isSupported)
        cap.mark(2)
    }

    @Test
    fun `isEmpty() test`() {
        val input = "This is a string"
        val inputStream = ByteArrayInputStream(input.toByteArray(Charset.defaultCharset()))
        val cap = CappedInputStream(inputStream, 2000)
        val isSupported = cap.isEmpty
        assertFalse(isSupported)
    }

    @Test
    fun `skipRemaining() test`() {
        val input = "This is a string"
        val inputStream = ByteArrayInputStream(input.toByteArray(Charset.defaultCharset()))
        val cap = CappedInputStream(inputStream, 2000)
        val availBefore = cap.available()
        assertEquals(input.length, availBefore)
        cap.skipRemaining()
        val availAfter = cap.available()
        assertEquals(0, availAfter)
    }


}