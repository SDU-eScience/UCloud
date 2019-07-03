package dk.sdu.cloud.file.processors

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.AuthStreams
import dk.sdu.cloud.auth.api.UserEvent
import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.homeDirectory
import dk.sdu.cloud.file.services.CommandRunner
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.FileScanner
import dk.sdu.cloud.file.services.withBlockingContext
import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class UserProcessor<FSCtx : CommandRunner>(
    private val streams: EventStreamService,
    private val externalFileService: FileScanner<FSCtx>,
    private val runnerFactory: FSCommandRunnerFactory<FSCtx>,
    private val coreFs: CoreFileSystemService<FSCtx>
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

                    Role.PROJECT_PROXY -> {
                        if (!event.userId.contains('#')) {
                            log.warn("Bad project user! $event")
                            return
                        }

                        val projectName = event.userId.substringBeforeLast('#')
                        val role = event.userId.substringAfterLast('#')

                        if (role == "PI") {
                            log.info("Creating a home folder for project: $event ($projectName)")
                            createHomeFolder("$projectName#PI", projectName)
                        }

                        runnerFactory.withBlockingContext(SERVICE_USER) { ctx ->
                            coreFs.createSymbolicLink(ctx, homeDirectory(projectName), homeDirectory(event.userId))
                        }
                    }

                    else -> log.debug("Not creating a home folder for ${event.userCreated}")
                }
            }

            else -> {
                log.warn("Discarding event: $event")
            }
        }
    }

    private suspend fun createHomeFolder(owner: String, folderName: String = owner) {
        val command = listOf(
            "sdu_cloud_add_user",
            folderName,
            "yes"
        )
        log.debug(command.toString())

        coroutineScope {
            launch(Dispatchers.IO) {
                val process = ProcessBuilder().apply { command(command) }.start()
                if (process.waitFor() != 0) {
                    throw IllegalStateException("Unable to create new user: $owner")
                }
            }.join()
        }

        // We must notify the system to scan for files created by external systems. In this case the create
        // user executable counts as an external system. An external system is any system that is not the
        // micro-service itself. We need to do this to ensure that the correct events are emitted into the u
        // storage-events stream.
        externalFileService.scanFilesCreatedExternally("/home/$folderName")
    }

    companion object : Loggable {
        override val log = logger()
    }
}
