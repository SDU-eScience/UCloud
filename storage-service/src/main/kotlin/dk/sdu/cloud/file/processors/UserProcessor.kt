package dk.sdu.cloud.file.processors

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.UserEvent
import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.homeDirectory
import dk.sdu.cloud.file.services.CommandRunner
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.ExternalFileService
import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.StorageUserDao
import dk.sdu.cloud.file.services.withBlockingContext
import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.runBlocking
import org.apache.kafka.streams.kstream.KStream

class UserProcessor<FSCtx : CommandRunner>(
    private val stream: KStream<String, UserEvent>,
    private val isDevelopment: Boolean,
    private val userDao: StorageUserDao,
    private val externalFileService: ExternalFileService<FSCtx>,
    private val runnerFactory: FSCommandRunnerFactory<FSCtx>,
    private val coreFs: CoreFileSystemService<FSCtx>
) {
    fun init() {
        stream.foreach { _, event -> handleEvent(event) }
    }

    private fun handleEvent(event: UserEvent) {
        when (event) {
            is UserEvent.Created -> {
                when (event.userCreated.role) {
                    Role.ADMIN, Role.USER -> {
                        log.info("Creating a matching user: $event")
                        createHomeFolder(event.userId)
                    }

                    Role.PROJECT_PROXY -> {
                        val indexOfSplit = event.userId.indexOf('#')
                        if (indexOfSplit == -1 || indexOfSplit == event.userId.length - 1) {
                            log.warn("Bad project user! $event")
                            return
                        }

                        val projectName = event.userId.substring(0, indexOfSplit)
                        val rootExists = runnerFactory.withBlockingContext(SERVICE_USER) { ctx ->
                            coreFs.exists(ctx, "/home/$projectName")
                        }

                        if (!rootExists) {
                            log.info("Creating a home folder for project: $event ($projectName)")
                            createHomeFolder("$projectName#PI", projectName)
                        }

                        log.info("Creating user for project: ${event.userCreated.id}")
                        createUser(event.userId)

                        runnerFactory.withBlockingContext(SERVICE_USER) { ctx ->
                            coreFs.createSymbolicLink(ctx, "/home/$projectName", homeDirectory(event.userId))
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

    private fun createUser(user: String) {
        val prefix = if (isDevelopment) emptyList() else listOf("sudo")
        val command =
            listOf("sdu_cloud_add_user", userDao.findStorageUser(user), "", "no")
        val process = ProcessBuilder().apply { command(prefix + command) }.start()
        if (process.waitFor() != 0) {
            throw IllegalStateException("Unable to create new user: $user")
        }
    }

    private fun createHomeFolder(owner: String, folderName: String = owner) {
        val prefix = if (isDevelopment) emptyList() else listOf("sudo")
        val command =
            listOf("sdu_cloud_add_user", userDao.findStorageUser(owner), folderName, "yes")
        log.debug(command.toString())

        val process = ProcessBuilder().apply { command(prefix + command) }.start()
        if (process.waitFor() != 0) {
            throw IllegalStateException("Unable to create new user: $owner")
        }

        // We must notify the system to scan for files created by external systems. In this case the create
        // user executable counts as an external system. An external system is any system that is not the
        // micro-service itself. We need to do this to ensure that the correct events are emitted into the u
        // storage-events stream.
        runBlocking {
            externalFileService.scanFilesCreatedExternally("/home/$folderName")
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
