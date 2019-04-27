package dk.sdu.cloud.file.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.api.WorkspaceMount
import dk.sdu.cloud.file.services.linuxfs.translateAndCheckFile
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.streams.toList

data class CreatedWorkspace(val workspaceId: String, val failures: List<WorkspaceMount>)

class WorkspaceService(
    fsRoot: File,
    private val fileScanner: FileScanner<*>
) {
    private val fsRoot = fsRoot.absoluteFile
    private val workspaceFile = File(fsRoot, WORKSPACE_PATH)

    fun workspace(id: String) = File(workspaceFile, id).toPath().normalize().toAbsolutePath()

    fun create(mounts: List<WorkspaceMount>, allowFailures: Boolean): CreatedWorkspace {
        val workspaceId = UUID.randomUUID().toString()
        val workspace = workspace(workspaceId).also { Files.createDirectories(it) }

        fun transferFile(file: Path, destination: Path, relativeTo: Path) {
            val resolvedDestination = if (Files.isSameFile(file, relativeTo)) {
                destination
            } else {
                destination.resolve(relativeTo.relativize(file))
            }

            if (Files.isDirectory(file)) {
                Files.createDirectories(resolvedDestination)
                Files.list(file).forEach {
                    transferFile(it, destination, relativeTo)
                }
            } else {
                val resolvedFile = if (Files.isSymbolicLink(file)) {
                    Files.readSymbolicLink(file)
                } else {
                    file
                }

                Files.createLink(resolvedDestination, resolvedFile)
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

        return CreatedWorkspace(workspaceId, failures)
    }

    suspend fun transfer(workspaceId: String, fileGlobs: List<String>, destination: String): List<String> {
        val workspace = workspace(workspaceId)
        if (!Files.exists(workspace)) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
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
                    // Transfer file back
                    val resolvedDestination = destinationDir.resolve(workspace.relativize(it))
                    if (resolvedDestination.startsWith(destinationDir)) {
                        Files.move(it, resolvedDestination)
                        val path = "/" + fsRoot.toPath().relativize(resolvedDestination).toFile().path
                        fileScanner.scanFilesCreatedExternally(path)
                        transferred.add(path)
                    } else {
                        log.info("Resolved destination is not within destination directory! $it $resolvedDestination")

                    }
                } catch (ex: Throwable) {
                    log.info("Failed to transfer $it. ${ex.message}")
                    log.debug(ex.stackTraceToString())
                }
            }

        return transferred
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