package dk.sdu.cloud.file.services

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.WorkspaceMount
import dk.sdu.cloud.file.services.linuxfs.Chown
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunner
import dk.sdu.cloud.file.services.linuxfs.translateAndCheckFile
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import java.io.File
import java.nio.file.CopyOption
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.util.*
import kotlin.streams.asSequence

data class CreatedWorkspace(val workspaceId: String, val failures: List<WorkspaceMount>)

private data class WorkspaceManifest(
    val username: String,
    val mounts: List<WorkspaceMount>,
    val createSymbolicLinkAt: String,
    val createdAt: Long = System.currentTimeMillis()
)

class WorkspaceService(
    fsRoot: File,
    private val fileScanner: FileScanner<*>,

    private val userDao: StorageUserDao<Long>,

    private val fsCommandRunnerFactory: FSCommandRunnerFactory<LinuxFSRunner>
) {
    private val fsRoot = fsRoot.absoluteFile.normalize()
    private val workspaceFile = File(fsRoot, WORKSPACE_PATH)

    fun workspace(id: String) = File(workspaceFile, id).toPath().normalize().toAbsolutePath()

    suspend fun create(
        user: String,
        mounts: List<WorkspaceMount>,
        allowFailures: Boolean,
        createSymbolicLinkAt: String
    ): CreatedWorkspace {
        return fsCommandRunnerFactory.withContext(user) { ctx ->
            ctx.submit {
                ctx.requireContext()

                val workspaceId = UUID.randomUUID().toString()
                val workspace = workspace(workspaceId).also {
                    Files.createDirectories(it)
                    Files.setPosixFilePermissions(
                        it,
                        setOf(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE
                        )
                    )
                }
                val inputWorkspace = workspace.resolve("input").also { Files.createDirectories(it) }
                val outputWorkspace = workspace.resolve("output").also { Files.createDirectories(it) }
                val symLinkPath = createSymbolicLinkAt.let { File(it).absoluteFile.toPath() }

                writeManifest(workspace, WorkspaceManifest(user, mounts, createSymbolicLinkAt))

                fun transferFile(
                    file: Path,
                    rootPath: Path,
                    initialDestination: String,
                    readOnly: Boolean
                ) {
                    val isInitialFile = Files.isSameFile(file, rootPath)
                    val relativePath = rootPath.relativize(file)

                    val inputRoot = inputWorkspace.resolve(initialDestination)
                    val outputRoot = outputWorkspace.resolve(initialDestination)
                    val symlinkRoot = symLinkPath.resolve(initialDestination)

                    val inputDestinationPath =
                        if (isInitialFile) inputRoot
                        else inputRoot.resolve(relativePath)

                    val outputDestinationPath =
                        if (isInitialFile) outputRoot
                        else outputRoot.resolve(relativePath)

                    if (Files.isDirectory(file)) {
                        if (!readOnly) Files.createDirectories(inputDestinationPath)
                        Files.createDirectories(outputDestinationPath)

                        Files.list(file).forEach {
                            transferFile(it, rootPath, initialDestination, readOnly)
                        }
                    } else {
                        val resolvedFile =
                            if (Files.isSymbolicLink(file)) Files.readSymbolicLink(file)
                            else file

                        if (readOnly) {
                            Files.createLink(inputDestinationPath, resolvedFile)
                            Files.createSymbolicLink(outputDestinationPath, symlinkRoot.resolve(relativePath))
                        } else {
                            Files.copy(resolvedFile, outputDestinationPath)
                        }
                    }
                }

                val failures = ArrayList<WorkspaceMount>()
                mounts.forEach {
                    try {
                        val file = File(translateAndCheckFile(fsRoot, it.source)).toPath()
                        transferFile(file, file, it.destination, it.readOnly)
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
        workspaceId: String,
        fileGlobs: List<String>,
        destination: String,
        replaceExisting: Boolean
    ): List<String> {
        val workspace = workspace(workspaceId)
        val outputWorkspace = workspace.resolve("output")
        if (!Files.exists(workspace)) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        val manifest = readManifest(workspace)
        val uid =
            userDao.findStorageUser(manifest.username) ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)

        val matchers = fileGlobs.map { FileSystems.getDefault().getPathMatcher("glob:$it") }
        val defaultDestinationDir = File(translateAndCheckFile(fsRoot, destination)).toPath()
        val transferred = ArrayList<String>()

        val copyOptions = run {
            val opts = ArrayList<CopyOption>()
            if (replaceExisting) opts += StandardCopyOption.REPLACE_EXISTING
            opts.toTypedArray()
        }

        fun transferFile(sourceFile: Path, destinationDir: Path, relativeTo: Path = outputWorkspace): Path {
            val resolvedDestination = destinationDir.resolve(relativeTo.relativize(sourceFile))
            if (!resolvedDestination.startsWith(destinationDir)) {
                throw IllegalArgumentException("Resolved destination isn't within allowed target")
            }

            if (Files.isDirectory(sourceFile)) {
                val targetIsDir = Files.isDirectory(resolvedDestination)
                if (targetIsDir) {
                    Files.list(sourceFile).forEach { child ->
                        transferFile(child, destinationDir, relativeTo)
                    }
                } else {
                    Files.move(sourceFile, resolvedDestination, *copyOptions)
                }
            } else {
                Files.move(sourceFile, resolvedDestination, *copyOptions)
            }
            return resolvedDestination
        }

        Files.list(outputWorkspace)
            .asSequence()
            .mapNotNull { path ->
                if (Files.isSymbolicLink(path)) return@mapNotNull null

                val existingMount = run {
                    val fileName = path.toFile().name
                    manifest.mounts.find { fileName == it.destination }
                }

                if (existingMount != null && !existingMount.allowMergeDuringTransfer) return@mapNotNull null

                val matchesGlob = matchers.any { it.matches(path.fileName) }

                if (existingMount != null || matchesGlob) {
                    if (Files.isDirectory(path)) {
                        Pair(path, existingMount)
                    } else {
                        Pair(path, null)
                    }
                } else {
                    null
                }
            }
            .forEach { (currentFile, mount) ->
                log.debug("Transferring file: $currentFile")

                try {
                    // We start the transfer by removing all symbolic links (backed by hard-linked read-only files)
                    // and fixing permissions of files we need to transfer.

                    Files.walk(currentFile).forEach { child ->
                        if (Files.isSymbolicLink(child)) {
                            Files.deleteIfExists(child)
                        } else {
                            Chown.setOwner(child, uid.toInt(), uid.toInt())
                            Files.setPosixFilePermissions(
                                child, setOf(
                                    PosixFilePermission.OWNER_WRITE,
                                    PosixFilePermission.OWNER_READ,
                                    PosixFilePermission.OWNER_EXECUTE,
                                    PosixFilePermission.GROUP_WRITE,
                                    PosixFilePermission.GROUP_READ,
                                    PosixFilePermission.GROUP_EXECUTE
                                )
                            )
                        }
                    }

                    // We will put the file in the mount (if one exists) otherwise it will go in the default destination
                    if (mount == null) {
                        // The file is then transferred to the new system and recorded for later use
                        val resolvedDestination = transferFile(currentFile, defaultDestinationDir)
                        val path = "/" + fsRoot.toPath().relativize(resolvedDestination).toFile().path
                        transferred.add(path)
                    } else {
                        val destinationDir = File(translateAndCheckFile(fsRoot, mount.source)).toPath()
                        Files.createDirectories(destinationDir) // Ensure that directory exists

                        Files.list(currentFile).forEach { child ->
                            val resolvedDestination = transferFile(child, destinationDir, relativeTo = currentFile)

                            val path = "/" + fsRoot.toPath().relativize(resolvedDestination).toFile().path
                            transferred.add(path)
                        }
                    }
                } catch (ex: Throwable) {
                    log.info("Failed to transfer $currentFile. ${ex.message}")
                    log.debug(ex.stackTraceToString())
                }
            }

        log.debug("Transferred ${transferred.size} files")
        transferred.forEach { path ->
            log.debug("Scanning external files")
            fileScanner.scanFilesCreatedExternally(path)
            log.debug("Scanning external files done")
        }

        log.debug("Done!")

        return transferred
    }

    fun delete(workspaceId: String) {
        val workspace = workspace(workspaceId)
        if (!Files.exists(workspace)) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        workspace.toFile().deleteRecursively()
    }

    private fun writeManifest(workspace: Path, manifest: WorkspaceManifest) {
        val file = workspace.resolve(WORKSPACE_MANIFEST_NAME)
        defaultMapper.writeValue(file.toFile(), manifest)
    }

    private fun readManifest(workspace: Path): WorkspaceManifest {
        val file = workspace.resolve(WORKSPACE_MANIFEST_NAME)
        if (!Files.exists(file)) throw RPCException("Invalid workspace", HttpStatusCode.BadRequest)
        return defaultMapper.readValue(file.toFile())
    }

    companion object : Loggable {
        override val log = logger()

        const val WORKSPACE_PATH = "workspace"
        const val WORKSPACE_MANIFEST_NAME = "workspace.json"
    }
}
