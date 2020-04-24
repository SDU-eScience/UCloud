package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.activity.api.*
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.service.Loggable
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.cio.readChannel
import io.ktor.util.toByteArray
import java.nio.file.Files
import java.util.*

class FileActivityTest(private val userA: UserAndClient) {
    private val testId = UUID.randomUUID().toString()

    @OptIn(KtorExperimentalAPI::class)
    suspend fun runTest() {
        with(userA) {
            log.info("Starting test...")
            createDir(testId)
            val location = joinPath(homeFolder, testId, "file")

            run {
                log.info("Uploading file")

                @Suppress("BlockingMethodInNonBlockingContext")
                val file = Files
                    .createTempFile("foo", ".bin")
                    .toFile()
                    .apply {
                        writeBytes(byteArrayOf(1, 3, 3, 7))
                    }

                MultiPartUploadDescriptions.simpleUpload.call(
                    SimpleUploadRequest(
                        location,
                        BinaryStream.outgoingFromChannel(file.readChannel())
                    ),
                    client
                ).orThrow()

                log.info("File upload complete: $location")

                retrySection {
                    log.info("Checking activity at path")
                    val activity = Activity.listByPath.call(
                        ListActivityByPathRequest(location, null, null),
                        client
                    ).orThrow()

                    require(activity.items.any { it.type == ActivityEventType.upload }) { "No updated entry in: ${activity.items}" }
                }

                retrySection {
                    log.info("Checking activity in general")
                    val activity = Activity.activityFeed.call(
                        Activity.BrowseByUser.Request(null, null, null, null, null, null),
                        client
                    ).orThrow()

                    require(activity.items.any {
                        val activityEvent = it.activityEvent
                        activityEvent is ActivityEvent.Uploaded &&
                                activityEvent.filePath.normalize() == location.normalize()
                    }) { "No updated entry in: ${activity.items}" }
                }
            }

            run {
                log.info("Downloading file")
                FileDescriptions.download.call(DownloadByURI(location, null), client)
                    .orThrow().asIngoing().channel.toByteArray()

                retrySection {
                    log.info("Checking activity at path")
                    val activity = Activity.listByPath.call(
                        ListActivityByPathRequest(location, null, null),
                        client
                    ).orThrow()

                    require(activity.items.any { it.activityEvent is ActivityEvent.Download }) { "No updated entry in: ${activity.items}" }
                }
                retrySection {
                    log.info("Checking activity in general")
                    val activity = Activity.activityFeed.call(
                        Activity.BrowseByUser.Request(null, null, null, null, null, null),
                        client
                    ).orThrow()

                    require(activity.items.any {
                        val activityEvent = it.activityEvent
                        activityEvent is ActivityEvent.Download &&
                                activityEvent.filePath.normalize() == location.normalize()
                    }) { "No updated entry in: ${activity.items}" }
                }
            }

            run {
                log.info("Moving file")
                val newLocation = location + "2"
                FileDescriptions.move.call(MoveRequest(location, newLocation), client).orThrow()
                FileDescriptions.move.call(MoveRequest(newLocation, location), client).orThrow()

                retrySection {
                    log.info("Checking activity at path")
                    val activity = Activity.listByPath.call(
                        ListActivityByPathRequest(location, null, null),
                        client
                    ).orThrow()

                    require(activity.items.any { it.activityEvent is ActivityEvent.Moved }) { "No updated entry in: ${activity.items}" }
                }

                retrySection {
                    log.info("Checking activity in general")
                    val activity = Activity.activityFeed.call(
                        Activity.BrowseByUser.Request(null, null, null, null, null, null),
                        client
                    ).orThrow()

                    require(activity.items.any {
                        val activityEvent = it.activityEvent
                        activityEvent is ActivityEvent.Moved &&
                                activityEvent.filePath.normalize() == location.normalize()
                    }) { "No updated entry in: ${activity.items}" }
                }
            }

            run {
                log.info("Deleting file...")
                FileDescriptions.deleteFile.call(DeleteFileRequest(location), client).orThrow()

                retrySection {
                    log.info("Checking activity in general")
                    val activity = Activity.activityFeed.call(
                        Activity.BrowseByUser.Request(null, null, null, null, null, null),
                        client
                    ).orThrow()

                    require(activity.items.any {
                        val activityEvent = it.activityEvent
                        activityEvent is ActivityEvent.Deleted &&
                                activityEvent.filePath.normalize() == location.normalize()
                    }) { "No updated entry in: ${activity.items}" }
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
