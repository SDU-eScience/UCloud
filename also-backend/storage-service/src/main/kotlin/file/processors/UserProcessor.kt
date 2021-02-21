package dk.sdu.cloud.file.processors

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.AuthStreams
import dk.sdu.cloud.auth.api.UserEvent
import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.file.api.LINUX_FS_USER_UID
import dk.sdu.cloud.file.services.HomeFolderService
import dk.sdu.cloud.file.services.linuxfs.LinuxFS
import dk.sdu.cloud.file.services.linuxfs.NativeFS
import dk.sdu.cloud.service.Loggable
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class UserProcessor(
    private val streams: EventStreamService,
    private val rootFolder: File,
    private val homeFolderService: HomeFolderService
) {
    fun init() {
        streams.subscribe(AuthStreams.UserUpdateStream, EventConsumer.Immediate(this::handleEvent))
    }

    private suspend fun handleEvent(event: UserEvent) {
        when (event) {
            is UserEvent.Created -> {
                when (event.userCreated.role) {
                    Role.ADMIN, Role.USER -> {
                        log.info("Creating a matching user: $event")
                        createHomeFolder(event.userId)
                    }

                    else -> log.debug("Not creating a home folder for ${event.userCreated}")
                }
            }

            else -> {
                log.warn("Discarding event: $event")
            }
        }
    }

    private suspend fun createHomeFolder(owner: String) {
        val filePermissions = PosixFilePermissions.asFileAttribute(LinuxFS.DEFAULT_DIRECTORY_MODE)
        val homeFolder = homeFolderService.findHomeFolder(owner)
        val homeFile = File(rootFolder, homeFolder)

        listOf(homeFile, File(homeFile, "Jobs"), File(homeFile, "Trash")).forEach { directory ->
            val path = directory.toPath()
            try {
                Files.createDirectory(path, filePermissions)
            } catch (ignored: java.nio.file.FileAlreadyExistsException) {
                // Ignored
            }
            NativeFS.chown(path.toFile(), LINUX_FS_USER_UID, LINUX_FS_USER_UID)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
