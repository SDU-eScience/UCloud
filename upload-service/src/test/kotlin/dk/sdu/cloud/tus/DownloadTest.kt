package dk.sdu.cloud.tus

import dk.sdu.cloud.tus.services.DownloadService
import dk.sdu.cloud.tus.services.NotFoundObjectStoreException
import dk.sdu.cloud.tus.services.ObjectStore
import dk.sdu.cloud.tus.services.RadosStorage
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.io.ByteChannel
import kotlinx.coroutines.experimental.io.readRemaining
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.io.core.readBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import kotlin.math.min

class DownloadTest {
    @Test
    fun simpleDownloadTest() {
        val store = mockk<ObjectStore>(relaxed = true)
        val files = mapOf(
            "single" to 1,
            "small" to 1000,
            "exact" to RadosStorage.BLOCK_SIZE,
            "multiple" to RadosStorage.BLOCK_SIZE * 2,
            "multiple-with-offset" to RadosStorage.BLOCK_SIZE * 2 + RadosStorage.BLOCK_SIZE / 2,
            "one-past-offset" to RadosStorage.BLOCK_SIZE + 1,
            "large" to RadosStorage.BLOCK_SIZE * 20 + 1337
        )

        val objectSizes = files.flatMap { (fileName, fileSize) ->
            val numBlocks = (fileSize / RadosStorage.BLOCK_SIZE) + 1

            (0 until numBlocks).mapNotNull {
                val name = if (it == 0) fileName else "$fileName-$it"
                val actualSize = min(
                    fileSize - RadosStorage.BLOCK_SIZE * it, // Remaining
                    RadosStorage.BLOCK_SIZE // One block
                )

                if (actualSize > 0) name to actualSize else null
            }
        }.toMap()

        println(objectSizes)

        coEvery { store.read(any(), any(), any()) } answers {
            val oid = call.invocation.args.find { it is String }!! as String
            val outputBuffer = call.invocation.args.find { it is ByteArray }!! as ByteArray
            val offset = (call.invocation.args.find { it is Long } as Long).toInt()

            if (oid !in objectSizes) throw NotFoundObjectStoreException("BAD ($oid)!")
            val size = objectSizes[oid]!!

            val bytesToRead = min((size - offset), outputBuffer.size)
            for (i in 0 until bytesToRead) {
                outputBuffer[i] = (offset + i).toByte()
            }

            bytesToRead
        }

        val downloadService = DownloadService(store)
        for ((fileName, fileSize) in files) {
            val channel = ByteChannel(true)
            runBlocking {
                launch { downloadService.download(fileName, channel) }

                val bytes = async {
                    channel.readRemaining().readBytes()
                }.await()

                assertArrayEquals("Bad result for $fileName -> $fileSize", ByteArray(fileSize) { it.toByte() }, bytes)
            }
        }
    }
}