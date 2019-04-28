package dk.sdu.cloud.file.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.api.WorkspaceMount
import dk.sdu.cloud.file.services.linuxfs.LinuxFS
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunner
import dk.sdu.cloud.file.services.linuxfs.StandardCLib
import dk.sdu.cloud.file.services.linuxfs.translateAndCheckFile
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.*
import java.util.*
import kotlin.streams.toList

data class CreatedWorkspace(val workspaceId: String, val failures: List<WorkspaceMount>)

class WorkspaceService(
    fsRoot: File,
    private val fileScanner: FileScanner<*>,

    // Note: Avoid using the fs since we do not want events to be emitted.
    private val fs: LinuxFS,

    private val fsCommandRunnerFactory: FSCommandRunnerFactory<LinuxFSRunner>
) {
    private val fsRoot = fsRoot.absoluteFile
    private val workspaceFile = File(fsRoot, WORKSPACE_PATH)

    fun workspace(id: String) = File(workspaceFile, id).toPath().normalize().toAbsolutePath()

    suspend fun create(user: String, mounts: List<WorkspaceMount>, allowFailures: Boolean): CreatedWorkspace {
        val workspaceId = UUID.randomUUID().toString()
        val workspace = workspace(workspaceId).also { Files.createDirectories(it) }

        return fsCommandRunnerFactory.withContext(user) { ctx ->
            ctx.submit {
                ctx.requireContext()

                fun transferFile(file: Path, destination: Path, relativeTo: Path) {
                    val resolvedDestination = if (Files.isSameFile(file, relativeTo)) {
                        destination
                    } else {
                        destination.resolve(relativeTo.relativize(file))
                    }

                    if (Files.isDirectory(file)) {
                        Files.createDirectories(resolvedDestination)

                        // Set the birth attribute to indicate to the system later that this file is a mount
                        // See the transfer method for more details.
                        StandardCLib.setxattr(
                            resolvedDestination.toFile().absolutePath,
                            "user.$XATTR_BIRTH",
                            (System.currentTimeMillis() / 1000).toString(),
                            false
                        )

                        Files.list(file).forEach {
                            transferFile(it, destination, relativeTo)
                        }
                    } else {
                        val resolvedFile = if (Files.isSymbolicLink(file)) {
                            Files.readSymbolicLink(file)
                        } else {
                            file
                        }

                        // We will first try the cheapest solution, which is to create a hard link. Creating a hard link
                        // will require read/write permissions. In case we do not have write permissions we will attempt
                        // to copy the file. Copying the file is obviously more expensive, but only requires read
                        // permissions.
                        try {
                            Files.createLink(resolvedDestination, resolvedFile)
                        } catch (ex: AccessDeniedException) {
                            Files.copy(resolvedFile, resolvedDestination, StandardCopyOption.REPLACE_EXISTING)
                        }
                    }
                }

                val failures = ArrayList<WorkspaceMount>()
                mounts.forEach {
                    try {
                        val file = File(translateAndCheckFile(fsRoot, it.source)).toPath()
                        transferFile(file, workspace.resolve(it.destination), file)
                    } catch (ex: Throwable) {
                        log.info("Failed to add ${it.source}. ${ex.message}")
                        log.debug(ex.stackTraceToString())

                        failures.add(it)
                    }
                }

                if (failures.isNotEmpty() && !allowFailures) {
                    delete(workspaceId)
                    throw RPCException("Workspace creation had failures: $failures", HttpStatusCode.BadRequest)
                }

                CreatedWorkspace(workspaceId, failures)
            }
        }

    }

    suspend fun transfer(
        user: String,
        workspaceId: String,
        fileGlobs: List<String>,
        destination: String
    ): List<String> {
        val workspace = workspace(workspaceId)
        if (!Files.exists(workspace)) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        return fsCommandRunnerFactory.withContext(user) { ctx ->
            ctx.submit {
                ctx.requireContext()

                val matchers = fileGlobs.map { FileSystems.getDefault().getPathMatcher("glob:$it") }
                val destinationDir = File(translateAndCheckFile(fsRoot, destination)).toPath()

                val transferred = ArrayList<String>()

                Files.list(workspace)
                    .filter { path ->
                        log.debug("Checking if we match $path")
                        matchers.any { it.matches(path.fileName) }
                    }
                    .toList()
                    .forEach {
                        try {
                            val resolvedDestination = destinationDir.resolve(workspace.relativize(it))
                            if (resolvedDestination.startsWith(destinationDir)) {
                                val path = "/" + fsRoot.toPath().relativize(resolvedDestination).toFile().path

                                // We use the birth attribute to check if a file is tracked in SDUCloud.
                                val isTrackedFile =
                                    runBlocking { fs.getExtendedAttribute(ctx, path, XATTR_BIRTH).statusCode == 0 }

                                if (isTrackedFile) {
                                    // Copy files that were mounted to give them a fresh ID
                                    Files.copy(it, resolvedDestination)
                                } else {
                                    // Non-tracked files can just be moved to the destination. They will have a
                                    // unique ID.
                                    Files.move(it, resolvedDestination)
                                }
                                transferred.add(path)
                            } else {
                                log.info("Resolved destination is not within destination directory! $it $resolvedDestination")
                            }
                        } catch (ex: Throwable) {
                            log.info("Failed to transfer $it. ${ex.message}")
                            log.debug(ex.stackTraceToString())
                        }
                    }

                transferred
            }
        }.also { transferred ->
            transferred.forEach { path ->
                fileScanner.scanFilesCreatedExternally(path)
            }
        }
    }

    fun delete(workspaceId: String) {
        val workspace = workspace(workspaceId)
        if (!Files.exists(workspace)) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        workspace.toFile().deleteRecursively()
    }

    companion object : Loggable {
        override val log = logger()

        const val WORKSPACE_PATH = "workspace"
    }
}