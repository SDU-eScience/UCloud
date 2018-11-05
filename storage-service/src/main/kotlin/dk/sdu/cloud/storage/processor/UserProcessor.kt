package dk.sdu.cloud.storage.processor

import dk.sdu.cloud.auth.api.UserEvent
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.storage.services.CommandRunner
import dk.sdu.cloud.storage.services.ExternalFileService
import dk.sdu.cloud.storage.services.StorageUserDao
import dk.sdu.cloud.storage.util.homeDirectory
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.streams.kstream.KStream

class UserProcessor<FSCtx : CommandRunner>(
    private val stream: KStream<String, UserEvent>,
    private val isDevelopment: Boolean,
    private val userDao: StorageUserDao,
    private val externalFileService: ExternalFileService<FSCtx>
) {
    fun init() {
        stream.foreach { _, event -> handleEvent(event) }
    }

    private fun handleEvent(event: UserEvent) {
        when (event) {
            is UserEvent.Created -> {
                log.info("Creating a matching user: $event")
                val prefix = if (isDevelopment) emptyList() else listOf("sudo")
                val command = listOf("sdu_cloud_add_user", userDao.findStorageUser(event.userId), event.userId)

                val process = ProcessBuilder().apply { command(prefix + command) }.start()
                if (process.waitFor() != 0) {
                    throw IllegalStateException("Unable to create new user: $event")
                }

                // We must notify the system to scan for files created by external systems. In this case the create
                // user executable counts as an external system. An external system is any system that is not the
                // micro-service itself. We need to do this to ensure that the correct events are emitted into the u
                // storage-events stream.
                runBlocking {
                    externalFileService.scanFilesCreatedExternally(homeDirectory(event.userId))
                }
            }

            else -> {
                log.warn("Discarding event: $event")
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
