package dk.sdu.cloud.file.processors

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.file.services.linuxfs.LinuxFS
import dk.sdu.cloud.file.services.linuxfs.NativeFS
import dk.sdu.cloud.project.api.ProjectEvent
import dk.sdu.cloud.project.api.ProjectEvents
import dk.sdu.cloud.service.Loggable
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class ProjectProcessor(
    private val streams: EventStreamService,
    private val rootFolder: File,
    private val authenticatedClient: AuthenticatedClient
) {
    fun init() {
        streams.subscribe(ProjectEvents.events, EventConsumer.Immediate(this::handleEvent))
    }

    private suspend fun handleEvent(event: ProjectEvent) {
        when (event) {
            is ProjectEvent.MemberAdded -> {
                log.info("Creating a project folder for user: $event")

                val filePermissions = PosixFilePermissions.asFileAttribute(LinuxFS.DEFAULT_DIRECTORY_MODE)
                val projectHome = projectHomeDirectory(event.projectId)
                val projectFolder = File(rootFolder, projectHome)
                val personalRepo = File(projectFolder, PERSONAL_REPOSITORY)
                val memberFolder = File(personalRepo, event.projectMember.username)
                val readmeFile = File(memberFolder, "Getting started.md")

                val readmePath = readmeFile.toPath()

                FileDescriptions.createPersonalRepository.call(
                    CreatePersonalRepositoryRequest(
                        event.projectId,
                        event.projectMember.username
                    ),
                    authenticatedClient
                )

                try {
                    Files.createFile(readmePath, filePermissions)
                } catch (ignored: java.nio.file.FileAlreadyExistsException) {
                    // Ignored
                }
                NativeFS.chown(readmePath.toFile(), LINUX_FS_USER_UID, LINUX_FS_USER_UID)

                Files.writeString(readmePath, """
                    Hi ${event.projectMember.username},
                    
                    This file is located in your own personal folder of this project.
                    
                    Files here are accessible to you and admins of the project.

                    Running a job while this project is active will make output and results appear in this folder.
                    
                    Any inquiries can be sent through our Support Form in the top-right corner.
                    
                    Best,
                    The UCloud Team
                """.trimIndent())
            }

            else -> {
                log.debug("Discarding event: $event")
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
