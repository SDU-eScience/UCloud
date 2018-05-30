package dk.sdu.cloud.client

import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeoutException

class TimeoutTest {
    @Test(expected = TimeoutException::class)
    fun testTimeout() {
        runBlocking {
            HttpClient.get("https://httpbin.org/delay/3", readTimeout = 1000)
        }
    }

    @Test
    fun testNoTimeout() {
        runBlocking {
            HttpClient.get("https://httpbin.org/delay/3")
        }
    }

    @Test
    fun testDripping() {
        runBlocking {
            HttpClient.get("https://httpbin.org/drip")
        }
    }

    @Ignore
    @Test
    fun testDownloadOfFile() {
        // TODO Host our own test file
        runBlocking {
            val file = Files.createTempFile("", ".bin").toFile()
            val start = System.currentTimeMillis()
            HttpClient.get("http://client.akamai.com/install/test-objects/10MB.bin", readTimeout = 100)
                .responseBodyAsStream.copyTo(file.outputStream())
            assertTrue(file.length() >= 1000 * 1000 * 10)
            val time = System.currentTimeMillis() - start
            assertTrue(time > 100)
        }
    }

    @Ignore
    @Test(expected = TimeoutException::class)
    fun testDownloadOfFileRequestTimeout() {
        // TODO Host our own test file
        runBlocking {
            HttpClient.get(
                "http://client.akamai.com/install/test-objects/10MB.bin",
                requestTimeout = 10,
                readTimeout = 500
            )
        }
    }
}