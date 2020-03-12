package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.file.api.MultiPartUploadDescriptions
import dk.sdu.cloud.file.api.SimpleBulkUpload
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.service.Loggable
import io.ktor.util.cio.readChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.util.*

class ConcurrentArchiveTest(private val userA: UserAndClient, private val concurrency: Int) {
    private val testId = UUID.randomUUID().toString()

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun runTest() {
        withContext(Dispatchers.IO) {
            with(userA) {
                createDir(testId)

                (0 until concurrency).map { tId ->
                    launch {
                        createDir(testId, tId.toString())
                        val archiveFile = Files.createTempFile("", ".tar.gz").toFile()
                        javaClass.classLoader.getResourceAsStream("many.tar.gz")!!.use { ins ->
                            archiveFile.outputStream().use { outs ->
                                ins.copyTo(outs)
                            }
                        }
                        MultiPartUploadDescriptions.simpleBulkUpload.call(
                            SimpleBulkUpload(
                                location = joinPath(
                                    homeFolder,
                                    testId,
                                    tId.toString()
                                ),
                                policy = WriteConflictPolicy.OVERWRITE,
                                format = "tgz",
                                file = BinaryStream.outgoingFromChannel(archiveFile.readChannel())
                            ),
                            client
                        ).orThrow()
                    }
                }.joinAll()
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
