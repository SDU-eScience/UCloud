package dk.sdu.cloud.storage

import dk.sdu.cloud.storage.services.FileUpload.Companion.BLOCK_SIZE
import dk.sdu.cloud.storage.services.NotFoundObjectStoreException
import dk.sdu.cloud.storage.services.ObjectDownloadService
import dk.sdu.cloud.storage.services.ObjectStore
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
            "exact" to BLOCK_SIZE,
            "multiple" to BLOCK_SIZE * 2,
            "multiple-with-offset" to BLOCK_SIZE * 2 + BLOCK_SIZE / 2,
            "one-past-offset" to BLOCK_SIZE + 1,
            "large" to BLOCK_SIZE * 20 + 1337
        )

        val objectSizes = files.flatMap { (fileName, fileSize) ->
            val numBlocks = (fileSize / BLOCK_SIZE) + 1

            (0 until numBlocks).mapNotNull {
                val name = if (it == 0) fileName else "$fileName-$it"
                val actualSize = min(
                    fileSize - BLOCK_SIZE * it, // Remaining
                    BLOCK_SIZE // One block
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

        val downloadService = ObjectDownloadService(store)
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