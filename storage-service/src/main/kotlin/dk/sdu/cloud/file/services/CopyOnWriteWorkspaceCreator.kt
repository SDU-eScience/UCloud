package dk.sdu.cloud.file.services

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.CowSnapshot
import dk.sdu.cloud.file.api.CowWorkspace
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.LINUX_FS_USER_UID
import dk.sdu.cloud.file.api.SNAPS_FILE
import dk.sdu.cloud.file.api.WorkspaceMode
import dk.sdu.cloud.file.api.WorkspaceMount
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.services.acl.requirePermission
import dk.sdu.cloud.file.services.linuxfs.Chown
import dk.sdu.cloud.file.services.linuxfs.listAndClose
import dk.sdu.cloud.file.services.linuxfs.runAndRethrowNIOExceptions
import dk.sdu.cloud.file.services.linuxfs.translateAndCheckFile
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.util.*
import kotlin.streams.asSequence

/**
 * Each copy-on-write file system has the following layers:
 *
 * - Lower: Contains the read-only layer. The layer is constructed from a
 * read-only snapshot of CephFS.
 * - Upper: Contains all the changes to the file system. The format depends on
 * the underlying CoW filesystem. The format is described below.
 * - Work: Temporary files for maintaining the CoW FS.
 *
 * The workspace service prepares the lower-layer. This is done by creating the
 * required snapshots from the workspace mounts. The service must communicate the
 * location of these snapshots to the consumer such that they can use them during
 * FS setup.
 *
 * Space must be allocated for the upper layer and work during creation. During
 * creation we don't need to care significantly about these. These two layers must
 * be created for each snapshot created.
 *
 * Details about the snapshots created and the general structure will be
 * communicated through a JSON files placed at the root of the workspace. The
 * following format will be used: [CowWorkspace], [CowSnapshot].
 *
 *
 * The workspace creation tools will create the following folder structure:
 *
 * ```
 * /
 *     /snaps.json
 *     /work (For remaining files)
 *     /snapshots (needed to namespace the snapshots)
 *         ${directoryName}/
 *             upper/
 *             work/
 * ```
 *
 * Note that the lower directory will be managed by the workspace consumer.
 *
 * For the initial prototype we will definitely create a single snapshot for each
 * mount.
 *
 * Snapshots can only be created for folders this presents a minor problem for
 * input files. As a work around we can simply mount the parent folder. This
 * should technically be valid as long as the application can find the file. This
 * could, for example, be done via a symlink to the file.
 */
class CopyOnWriteWorkspaceCreator<Ctx : FSUserContext>(
    private val fsRoot: File,
    private val aclService: AclService<*>,
    private val fsRunner: FSCommandRunnerFactory<Ctx>,
    private val coreFs: CoreFileSystemService<Ctx>
) : WorkspaceCreator {
    override suspend fun create(
        user: String,
        mounts: List<WorkspaceMount>,
        allowFailures: Boolean,
        createSymbolicLinkAt: String
    ): CreatedWorkspace = runAndRethrowNIOExceptions {
        val workspaceId = UUID.randomUUID().toString()
        val workspace = workspaceFile(fsRoot, workspaceId).also {
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

        val manifest = WorkspaceManifest(user, mounts, "", mode = WorkspaceMode.COPY_ON_WRITE)
        manifest.write(workspace)

        Files.createDirectory(workspace.resolve(GENERAL_WORK))

        val snaps = ArrayList<CowSnapshot>()
        val failures = ArrayList<WorkspaceMount>()
        mounts.forEach { mount ->
            if (mount.readOnly) {
                log.warn(
                    "Ignoring readOnly property of workspace mount. This attribute is not supported for " +
                            "CoW workspaces"
                )
            }

            aclService.requirePermission(
                mount.source,
                user,
                AccessRight.READ // We check if we have write permissions later
            )

            try {
                val file = File(translateAndCheckFile(fsRoot, mount.source)).toPath()
                if (Files.isDirectory(file)) {
                    val targetName = mount.destination.fileName()

                    val snapDirectory = file.resolve("./.snap/${workspaceId}")
                    Files.createDirectory(snapDirectory)
                    val root = workspace.resolve("./$SNAPSHOT_LAYER/$targetName")
                    Files.createDirectories(root)
                    Files.createDirectory(root.resolve(UPPER_LAYER))
                    Files.createDirectory(root.resolve(WORK_LAYER))

                    snaps.add(
                        CowSnapshot(
                            targetName,
                            fsRoot.toPath().relativize(snapDirectory).normalize().toString(),
                            mount.source
                        )
                    )
                } else if (Files.isRegularFile(file)) {
                    TODO("Files not yet implemented")
                }
            } catch (ex: Throwable) {
                log.info("Failed to add ${mount.source}. ${ex.message}")
                log.debug(ex.stackTraceToString())
                failures.add(mount)
            }
        }

        if (failures.isNotEmpty() && !allowFailures) {
            delete(workspaceId, manifest)

            throw RPCException("Workspace creation had failures: $failures", HttpStatusCode.BadRequest)
        }

        workspace.resolve(SNAPS_FILE).toFile().writeBytes(defaultMapper.writeValueAsBytes(CowWorkspace(snaps)))

        CreatedWorkspace(workspaceId, failures)
    }

    override suspend fun transfer(
        id: String,
        manifest: WorkspaceManifest,
        replaceExisting: Boolean,
        matchers: List<PathMatcher>,
        destination: String,
        defaultDestinationDir: Path
    ): List<String> {
        log.debug("transfer $id $manifest $destination $defaultDestinationDir")
        val workspace = workspaceFile(fsRoot, id)
        val cowManifest = defaultMapper.readValue<CowWorkspace>(workspace.resolve(SNAPS_FILE).toFile())
        val snapshotsDirectory = workspace.resolve(SNAPSHOT_LAYER)
        val workDirectory = workspace.resolve(GENERAL_WORK)
        val transferredFiles = ArrayList<String>()
        val canWriteToDefault =
            aclService.hasPermission(defaultDestinationDir.toCloudPath(), manifest.username, AccessRight.WRITE)

        log.debug("workspace = $workspace")
        log.debug("cowManifest = $cowManifest")
        log.debug("snapshotsDirectory = $snapshotsDirectory")
        log.debug("workDirectory = $workDirectory")
        log.debug("canWriteToDefault = $canWriteToDefault")

        cowManifest.snapshots.forEach { snapshot ->
            val snapshotRoot = snapshotsDirectory.resolve(snapshot.directoryName)
            val upper = snapshotRoot.resolve(UPPER_LAYER)
            val realRootPath = File(translateAndCheckFile(fsRoot, snapshot.realPath)).toPath()

            val hasWriteAccess = aclService.hasPermission(snapshot.realPath, manifest.username, AccessRight.WRITE)
            if (hasWriteAccess) {
                transferSnapshot(upper, realRootPath, transferredFiles)
            } else {
                if (canWriteToDefault) {
                    fsRunner.withContext(SERVICE_USER) { rootCtx ->
                        upper.listAndClose().forEach { rootPath ->
                            transferToDefault(rootPath, defaultDestinationDir, rootCtx, transferredFiles)
                        }
                    }
                }
            }
        }

        if (canWriteToDefault) {
            fsRunner.withContext(SERVICE_USER) { rootCtx ->
                workDirectory
                    .listAndClose()
                    .asSequence()
                    .filter { child ->
                        matchers.any { it.matches(child) }
                    }
                    .forEach { rootPath ->
                        transferToDefault(rootPath, defaultDestinationDir, rootCtx, transferredFiles)
                    }
            }
        }

        return transferredFiles
    }

    // This should already be scheduled in a thread pool designed for blocking IO. Not much we can do all file IO
    // is inherently blocking.
    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun transferToDefault(
        rootPath: Path,
        defaultDestinationDir: Path,
        rootCtx: Ctx,
        transferredFiles: ArrayList<String>
    ) {
        Files.walk(rootPath).asSequence().forEach { path ->
            val relativePath = defaultDestinationDir.relativize(path)
            val realPath = defaultDestinationDir.resolve(relativePath)
            val cloudPath = realPath.toCloudPath()
            val workspaceCloudPath = path.toCloudPath()

            log.debug("transferToDefault($rootPath, $defaultDestinationDir, $path)")

            if (Files.isRegularFile(path)) {
                sanitizeFile(path)
                coreFs.handlePotentialFileCreation(rootCtx, workspaceCloudPath)
                Files.move(
                    path,
                    realPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )

                coreFs.emitUpdateEvent(rootCtx, cloudPath)
                transferredFiles.add(cloudPath)
            } else if (Files.isDirectory(path)) {
                coreFs.makeDirectory(rootCtx, cloudPath)
            }
        }
    }

    private suspend fun transferSnapshot(
        upper: Path,
        realRootPath: Path,
        transferredFiles: ArrayList<String>
    ) {
        fsRunner.withContext(SERVICE_USER) { rootCtx ->
            Files.walk(upper).asSequence().forEach seq@{ path ->
                val relativePath = upper.relativize(path)
                val realPath = realRootPath.resolve(relativePath)
                val cloudPath = realPath.toCloudPath()
                val workspaceCloudPath = path.toCloudPath()

                if (Files.isSymbolicLink(path)) {
                    Files.deleteIfExists(path)
                    return@seq
                }

                if (Files.isRegularFile(path)) {
                    val fileName = path.toFile().name
                    if (fileName.startsWith(DELETED_PREFIX)) {
                        try {
                            coreFs.delete(
                                rootCtx,
                                realPath.resolveSibling(fileName.removePrefix(DELETED_PREFIX)).toCloudPath()
                            )
                        } catch (ex: FSException.NotFound) {
                            // Ignored
                        }
                    } else {
                        sanitizeFile(path)

                        val existingFile =
                            coreFs.statOrNull(
                                rootCtx,
                                cloudPath,
                                setOf(FileAttribute.INODE, FileAttribute.FILE_TYPE, FileAttribute.TIMESTAMPS)
                            )

                        val fileIdToReuse = if (existingFile?.fileType != FileType.FILE) {
                            null
                        } else {
                            existingFile.inode
                        }

                        val existingCreation = existingFile?.timestamps?.created

                        if (existingFile != null && existingFile.fileType != FileType.FILE) {
                            // Type mismatch. We should delete the existing entry before creating a new one
                            // (with a new ID)
                            coreFs.delete(rootCtx, cloudPath)
                        }

                        // File has been updated
                        coreFs.handlePotentialFileCreation(
                            rootCtx,
                            workspaceCloudPath,
                            preAllocatedCreation = existingCreation,
                            preAllocatedFileId = fileIdToReuse
                        )

                        Files.move(
                            path,
                            realPath,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING
                        )

                        coreFs.emitUpdateEvent(rootCtx, cloudPath)
                        transferredFiles.add(cloudPath)
                    }
                } else if (Files.isDirectory(path)) {
                    try {
                        coreFs.makeDirectory(rootCtx, cloudPath)
                        transferredFiles.add(cloudPath)
                    } catch (ex: FSException.AlreadyExists) {
                        // Ignored
                    }
                }
            }
        }
    }

    private fun sanitizeFile(path: Path) {
        // File sanitization
        Chown.setOwner(path, LINUX_FS_USER_UID, LINUX_FS_USER_UID)
        Files.setPosixFilePermissions(
            path, setOf(
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_WRITE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_EXECUTE
            )
        )
    }

    override suspend fun delete(id: String, manifest: WorkspaceManifest) {
        val workspace = workspaceFile(fsRoot, id)
        val cowManifest = defaultMapper.readValue<CowWorkspace>(workspace.resolve(SNAPS_FILE).toFile())
        cowManifest.snapshots.forEach { snap ->
            runCatching {
                Files.delete(fsRoot.toPath().resolve(snap.snapshotPath))
            }
        }

        workspace.toFile().deleteRecursively()
    }

    private fun Path.toCloudPath(): String = "/" + fsRoot.toPath().relativize(this).toFile().path

    companion object : Loggable {
        override val log = logger()
        private const val GENERAL_WORK = "work"
        private const val UPPER_LAYER = "upper"
        private const val WORK_LAYER = "work"
        private const val SNAPSHOT_LAYER = "snapshots"
        private const val DELETED_PREFIX = ".wh."
    }
}
