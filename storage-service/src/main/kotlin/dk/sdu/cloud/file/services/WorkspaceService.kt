package dk.sdu.cloud.file.services

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.WorkspaceMount
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.services.acl.requirePermission
import dk.sdu.cloud.file.services.linuxfs.LinuxFS
import dk.sdu.cloud.file.services.linuxfs.listAndClose
import dk.sdu.cloud.file.services.linuxfs.runAndRethrowNIOExceptions
import dk.sdu.cloud.file.services.linuxfs.translateAndCheckFile
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import java.io.File
import java.nio.channels.Channels
import java.nio.file.CopyOption
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.*

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

    private val aclService: AclService<*>
) {
    private val fsRoot = fsRoot.absoluteFile.normalize()
    private val workspaceFile = File(fsRoot, WORKSPACE_PATH)

    // TODO This might need to clear ACLs from user code. Technically this won't be needed under new system but would
    //  be needed if we change stuff around again.

    fun workspace(id: String) = File(workspaceFile, id).toPath().normalize().toAbsolutePath()

    suspend fun create(
        user: String,
        mounts: List<WorkspaceMount>,
        allowFailures: Boolean,
        createSymbolicLinkAt: String
    ): CreatedWorkspace = runAndRethrowNIOExceptions {
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

        val failures = ArrayList<WorkspaceMount>()
        mounts.forEach {
            aclService.requirePermission(
                it.source,
                user,
                if (it.readOnly) AccessRight.READ else AccessRight.WRITE
            )

            try {
                val file = File(translateAndCheckFile(fsRoot, it.source)).toPath()
                transferFileNoAccessCheck(
                    inputWorkspace,
                    outputWorkspace,
                    symLinkPath,
                    file,
                    file,
                    it.destination,
                    it.readOnly
                )
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
                    sourceFile.listAndClose().forEach { child ->
                        transferFile(child, destinationDir, relativeTo)
                    }
                } else {
                    transferred.add(resolvedDestination.toCloudPath())
                    Files.move(sourceFile, resolvedDestination, *copyOptions)
                }
            } else {
                if (Files.exists(resolvedDestination)) {
                    val lastModifiedTime = runCatching { Files.getLastModifiedTime(resolvedDestination) }.getOrNull()
                    if (Files.getLastModifiedTime(sourceFile) != lastModifiedTime) {
                        // Truncate to preserve inode
                        val options = HashSet<OpenOption>()
                        options.add(StandardOpenOption.TRUNCATE_EXISTING)
                        options.add(StandardOpenOption.WRITE)
                        options.add(StandardOpenOption.CREATE)

                        val os = Channels.newOutputStream(
                            Files.newByteChannel(
                                resolvedDestination,
                                options,
                                PosixFilePermissions.asFileAttribute(LinuxFS.DEFAULT_FILE_MODE)
                            )
                        )

                        val ins = Channels.newInputStream(
                            Files.newByteChannel(
                                sourceFile,
                                StandardOpenOption.READ
                            )
                        )

                        // No need to copy list since the original file is simply updated.
                        ins.use { os.use { ins.copyTo(os) } }
                        transferred.add(resolvedDestination.toCloudPath())
                    } else {
                        log.debug("Don't need to copy file. It has not been modified.")
                    }
                } else {
                    transferred.add(resolvedDestination.toCloudPath())
                    Files.move(sourceFile, resolvedDestination, *copyOptions)
                }
            }
            return resolvedDestination
        }

        outputWorkspace.listAndClose()
            .asSequence()
            .mapNotNull { path ->
                if (Files.isSymbolicLink(path)) return@mapNotNull null

                val existingMount = run {
                    val fileName = path.toFile().name
                    manifest.mounts.find { fileName == File(it.destination).name }
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
                            TODO("Deal with UIDs")
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

                    // TODO FIXME We need to check permissions here!
                    // We will put the file in the mount (if one exists) otherwise it will go in the default destination
                    if (mount == null) {
                        // The file is then transferred to the new system and recorded for later use
                        transferFile(currentFile, defaultDestinationDir)
                    } else {
                        val destinationDir = File(translateAndCheckFile(fsRoot, mount.source)).toPath()
                        runCatching {
                            Files.createDirectories(destinationDir) // Ensure that directory exists
                        }

                        currentFile.listAndClose().forEach { child ->
                            transferFile(child, destinationDir, relativeTo = currentFile)
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

    private fun Path.toCloudPath(): String = "/" + fsRoot.toPath().relativize(this).toFile().path

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

    private fun transferFileNoAccessCheck(
        inputWorkspace: Path,
        outputWorkspace: Path,
        symLinkPath: Path,

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
            if (readOnly) Files.createDirectories(inputDestinationPath)
            Files.createDirectories(outputDestinationPath)

            file.listAndClose().forEach {
                transferFileNoAccessCheck(inputWorkspace, outputWorkspace, symLinkPath, it, rootPath, initialDestination, readOnly)
            }
        } else {
            val resolvedFile =
                if (Files.isSymbolicLink(file)) Files.readSymbolicLink(file)
                else file

            if (readOnly) {
                Files.createLink(inputDestinationPath, resolvedFile)
                Files.createSymbolicLink(outputDestinationPath, symlinkRoot.resolve(relativePath))
            } else {
                Files.copy(resolvedFile, outputDestinationPath, StandardCopyOption.COPY_ATTRIBUTES)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()

        const val WORKSPACE_PATH = "workspace"
        const val WORKSPACE_MANIFEST_NAME = "workspace.json"
    }
}
