package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.file.api.DeleteFileRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.MultiPartUploadDescriptions
import dk.sdu.cloud.file.api.SimpleUploadRequest
import dk.sdu.cloud.file.api.joinPath
import io.ktor.util.cio.readChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.util.*

class ConcurrentFileUploadsTest(
    private val userA: UserAndClient,
    private val concurrency: Int
) {
    private val testId = UUID.randomUUID().toString()

    suspend fun runTest() {
        withContext(Dispatchers.IO) {
            with(userA) {
                createDir(testId)

                val sampleFile = Files
                    .createTempFile("sample", ".bin")
                    .toFile()
                    .apply {
                        val buffer = ByteArray(1024) { 1 }
                        outputStream().use { fos ->
                            repeat(1024 * 4) {
                                fos.write(buffer)
                            }
                        }
                    }

                (0 until concurrency).map { id ->
                    launch {
                        MultiPartUploadDescriptions.simpleUpload.call(
                            SimpleUploadRequest(
                                joinPath(homeFolder, testId, "$id"),
                                BinaryStream.outgoingFromChannel(sampleFile.readChannel())
                            ),
                            client
                        ).orThrow()

                        FileDescriptions.deleteFile.call(
                            DeleteFileRequest(joinPath(homeFolder, testId, "$id")),
                            client
                        ).orThrow()
                    }
                }.joinAll()
            }
        }
    }
}
