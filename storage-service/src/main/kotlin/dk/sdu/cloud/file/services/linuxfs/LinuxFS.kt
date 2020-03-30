@file:Suppress("BlockingMethodInNonBlockingContext")

package dk.sdu.cloud.file.services.linuxfs

import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.file.services.LowLevelFileSystemInterface
import dk.sdu.cloud.file.services.ProjectCache
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.services.acl.requirePermission
import dk.sdu.cloud.file.services.linuxfs.LinuxFS.Companion.PATH_MAX
import dk.sdu.cloud.file.util.CappedInputStream
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.file.util.throwExceptionBasedOnStatus
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.task.api.TaskContext
import kotlinx.coroutines.runBlocking
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermission
import java.util.*
import kotlin.Comparator
import kotlin.collections.ArrayList
import kotlin.math.min

class LinuxFS(
    fsRoot: File,
    private val aclService: AclService,
    private val projectCache: ProjectCache
) : LowLevelFileSystemInterface<LinuxFSRunner> {
    private val fsRoot = fsRoot.normalize().absoluteFile

    override suspend fun requirePermission(ctx: LinuxFSRunner, path: String, permission: AccessRight) {
        aclService.requirePermission(path.normalize(), ctx.user, permission)
    }

    override suspend fun copy(
        ctx: LinuxFSRunner,
        from: String,
        to: String,
        writeConflictPolicy: WriteConflictPolicy,
        task: TaskContext
    ) = ctx.submit {
        translateAndCheckFile(ctx, from)
        aclService.requirePermission(from, ctx.user, AccessRight.READ)
        aclService.requirePermission(to, ctx.user, AccessRight.WRITE)

        copyPreAuthorized(ctx, from, to, writeConflictPolicy)
    }

    private fun copyPreAuthorized(
        ctx: LinuxFSRunner,
        from: String,
        to: String,
        writeConflictPolicy: WriteConflictPolicy
    ) {
        val systemFrom = File(translateAndCheckFile(ctx, from))
        val systemTo = File(translateAndCheckFile(ctx, to))

        if (from.normalize() == to.normalize() && writeConflictPolicy == WriteConflictPolicy.OVERWRITE) {
            // Do nothing (The code below would truncate the file and then copy the remaining 0 bytes)
            return
        }

        if (Files.isDirectory(systemFrom.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            if (Files.exists(systemTo.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isDirectory(systemTo.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    if (!writeConflictPolicy.allowsOverwrite()) {
                        throw FSException.AlreadyExists()
                    }
                } else {
                    throw FSException.BadRequest("File types do not match")
                }
            } else {
                NativeFS.createDirectories(systemTo)
            }
        } else {
            NativeFS.openForReading(systemFrom).use { ins ->
                NativeFS.openForWriting(systemTo, writeConflictPolicy.allowsOverwrite()).use { outs ->
                    ins.copyTo(outs)
                }
            }
        }
    }

    override suspend fun move(
        ctx: LinuxFSRunner,
        from: String,
        to: String,
        writeConflictPolicy: WriteConflictPolicy,
        task: TaskContext
    ) = ctx.submit {
        val systemFrom = File(translateAndCheckFile(ctx, from))
        val systemTo = File(translateAndCheckFile(ctx, to))

        // We need write permission on from's parent to avoid being able to steal a file by changing the owner.
        aclService.requirePermission(from.parent(), ctx.user, AccessRight.WRITE)
        aclService.requirePermission(to, ctx.user, AccessRight.WRITE)

        // We need to record some information from before the move
        val fromStat = stat(
            ctx,
            systemFrom,
            setOf(StorageFileAttribute.fileType, StorageFileAttribute.path, StorageFileAttribute.fileType),
            hasPerformedPermissionCheck = true
        )

        val targetType =
            runCatching {
                stat(
                    ctx,
                    systemTo,
                    setOf(StorageFileAttribute.fileType),
                    hasPerformedPermissionCheck = true
                )
            }.getOrNull()?.fileType

        if (targetType != null) {
            if (fromStat.fileType != targetType) {
                throw FSException.BadRequest("Target already exists and is not of same type as source.")
            }
            if (fromStat.fileType == targetType &&
                fromStat.fileType == FileType.DIRECTORY &&
                writeConflictPolicy == WriteConflictPolicy.OVERWRITE
            ) {
                throw FSException.BadRequest("Directory is not allowed to overwrite existing directory")
            }
        }

        movePreAuthorized(ctx, from, to, writeConflictPolicy)
    }

    private suspend fun movePreAuthorized(
        ctx: LinuxFSRunner,
        from: String,
        to: String,
        writeConflictPolicy: WriteConflictPolicy
    ) {
        val systemFrom = File(translateAndCheckFile(ctx, from))
        val systemTo = File(translateAndCheckFile(ctx, to))
        val replaceExisting = writeConflictPolicy.allowsOverwrite()

        if (writeConflictPolicy == WriteConflictPolicy.MERGE) {
            if (systemFrom.isDirectory) {
                NativeFS.listFiles(systemFrom).map { File(systemFrom, it) }.forEach {
                    movePreAuthorized(
                        ctx,
                        Paths.get(from, it.name).toString(),
                        Paths.get(to, it.name).toString(),
                        writeConflictPolicy
                    )
                }
            } else {
                try {
                    NativeFS.createDirectories(systemTo.parentFile)
                } catch (ignored: FSException.AlreadyExists) {
                    // Ignored
                }
                NativeFS.move(systemFrom, systemTo, replaceExisting)
            }
        } else {
            NativeFS.move(systemFrom, systemTo, replaceExisting)
        }
    }

    override suspend fun listDirectoryPaginated(
        ctx: LinuxFSRunner,
        directory: String,
        mode: Set<StorageFileAttribute>,
        sortBy: FileSortBy?,
        paginationRequest: NormalizedPaginationRequest?,
        order: SortOrder?,
        type: FileType?
    ): Page<StorageFile> = ctx.submit {
        aclService.requirePermission(directory, ctx.user, AccessRight.READ)

        val systemFiles = run {
            val file = File(translateAndCheckFile(ctx, directory))
            val requestedDirectory = file.takeIf { it.exists() } ?: throw FSException.NotFound()

            NativeFS.listFiles(requestedDirectory)
                .map { File(requestedDirectory, it) }
                .filter { fileInDirectory ->
                    when (type) {
                        FileType.DIRECTORY -> fileInDirectory.isDirectory
                        FileType.FILE -> fileInDirectory.isFile
                        null -> true
                    }
                }
        }.filter { !Files.isSymbolicLink(it.toPath()) }

        val min =
            if (paginationRequest == null) 0
            else min(systemFiles.size, paginationRequest.itemsPerPage * paginationRequest.page)
        val max =
            if (paginationRequest == null) systemFiles.size
            else min(systemFiles.size, min + paginationRequest.itemsPerPage)

        val page = if (sortBy != null && order != null) {
            // We must sort our files. We do this in two lookups!

            // The first lookup will retrieve just the path (this is cheap) and the attribute we need
            // (this might not be).

            // In the second lookup we use only the relevant files. We do this by performing the sorting after the
            // first step and gathering a list of files to be included in the result.

            val sortingAttribute = when (sortBy) {
                FileSortBy.TYPE -> StorageFileAttribute.fileType
                FileSortBy.PATH -> StorageFileAttribute.path
                FileSortBy.CREATED_AT -> StorageFileAttribute.createdAt
                FileSortBy.MODIFIED_AT -> StorageFileAttribute.modifiedAt
                FileSortBy.SIZE -> StorageFileAttribute.size
                FileSortBy.SENSITIVITY -> StorageFileAttribute.sensitivityLevel
                null -> StorageFileAttribute.path
            }

            val statsForSorting = stat(
                ctx,
                systemFiles,
                setOf(StorageFileAttribute.path, sortingAttribute),
                hasPerformedPermissionCheck = true
            ).filterNotNull()

            val comparator = comparatorForFileRows(sortBy, order)

            val relevantFileRows = statsForSorting.sortedWith(comparator).subList(min, max)

            // Time for the second lookup. We will retrieve all attributes we don't already know about and merge them
            // with first lookup.
            val relevantFiles = relevantFileRows.map { File(translateAndCheckFile(ctx, it.path)) }

            val desiredMode = mode - setOf(sortingAttribute) + setOf(StorageFileAttribute.path)

            val statsForRelevantRows = stat(
                ctx,
                relevantFiles,
                desiredMode,
                hasPerformedPermissionCheck = true
            ).filterNotNull().associateBy { it.path }

            val items = relevantFileRows.mapNotNull { rowWithSortingInfo ->
                val rowWithFullInfo = statsForRelevantRows[rowWithSortingInfo.path] ?: return@mapNotNull null
                rowWithSortingInfo.mergeWith(rowWithFullInfo)
            }

            Page(
                systemFiles.size,
                paginationRequest?.itemsPerPage ?: items.size,
                paginationRequest?.page ?: 0,
                items
            )
        } else {
            val items = stat(
                ctx,
                systemFiles,
                mode,
                hasPerformedPermissionCheck = true
            ).filterNotNull().subList(min, max)

            Page(
                items.size,
                paginationRequest?.itemsPerPage ?: items.size,
                paginationRequest?.page ?: 0,
                items
            )
        }

        page
    }

    private fun comparatorForFileRows(
        sortBy: FileSortBy,
        order: SortOrder
    ): Comparator<StorageFile> {
        val naturalComparator: Comparator<StorageFile> = when (sortBy) {
            FileSortBy.CREATED_AT -> Comparator.comparingLong { it.createdAt }

            FileSortBy.MODIFIED_AT -> Comparator.comparingLong { it.modifiedAt }

            FileSortBy.TYPE -> Comparator.comparing<StorageFile, String> {
                it.fileType.name
            }.thenComparing(Comparator.comparing<StorageFile, String> {
                it.path.fileName().toLowerCase()
            })

            FileSortBy.PATH -> Comparator.comparing<StorageFile, String> {
                it.path.fileName().toLowerCase()
            }

            FileSortBy.SIZE -> Comparator.comparingLong { it.size }

            // TODO This should be resolved before sorting
            FileSortBy.SENSITIVITY -> Comparator.comparing<StorageFile, String> {
                (it.ownSensitivityLevelOrNull?.name?.toLowerCase()) ?: "inherit"
            }
        }

        return when (order) {
            SortOrder.ASCENDING -> naturalComparator
            SortOrder.DESCENDING -> naturalComparator.reversed()
        }
    }

    private suspend fun stat(
        ctx: LinuxFSRunner,
        systemFile: File,
        mode: Set<StorageFileAttribute>,
        hasPerformedPermissionCheck: Boolean
    ): StorageFile {
        return stat(ctx, listOf(systemFile), mode, hasPerformedPermissionCheck).first()
            ?: throw FSException.NotFound()
    }

    private suspend fun stat(
        ctx: LinuxFSRunner,
        systemFiles: List<File>,
        mode: Set<StorageFileAttribute>,
        hasPerformedPermissionCheck: Boolean
    ): List<StorageFile?> {
        // The 'shareLookup' contains a mapping between cloud paths and their ACL
        val shareLookup = if (StorageFileAttribute.acl in mode) {
            aclService.listAcl(systemFiles.map { it.path.toCloudPath().normalize() })
        } else {
            emptyMap()
        }

        return systemFiles.map { systemFile ->
            try {
                if (!hasPerformedPermissionCheck) {
                    aclService.requirePermission(systemFile.path.toCloudPath(), ctx.user, AccessRight.READ)
                }

                val stat = NativeFS.stat(systemFile)

                val realOwner = if (StorageFileAttribute.ownerName in mode) {
                    val toCloudPath = systemFile.absolutePath.toCloudPath()
                    val realPath = toCloudPath.normalize()

                    val components = realPath.components()
                    when {
                        components.isEmpty() -> SERVICE_USER
                        components.size < 2 -> SERVICE_USER
                        components.first() == "projects" -> {
                            val projectId = components[1]
                            if (projectCache.viewMember(projectId, ctx.user)?.role?.isAdmin() == true) {
                                ctx.user
                            } else {
                                SERVICE_USER
                            }
                        }
                        components.first() != "home" -> SERVICE_USER
                        else -> components[1]
                    }
                } else {
                    null
                }

                val shares = if (StorageFileAttribute.acl in mode) {
                    val cloudPath = systemFile.path.toCloudPath()
                    shareLookup.getOrDefault(cloudPath, emptyList()).map {
                        AccessEntry(it.entity, it.permissions)
                    }
                } else {
                    null
                }

                StorageFileImpl(
                    stat.fileType,
                    systemFile.absolutePath.toCloudPath(),
                    stat.modifiedAt,
                    stat.modifiedAt,
                    realOwner,
                    stat.size,
                    shares,
                    null,
                    stat.ownSensitivityLevel,
                    stat.ownerUid != LINUX_FS_USER_UID
                )
            } catch (ex: NoSuchFileException) {
                null
            }
        }
    }

    override suspend fun delete(
        ctx: LinuxFSRunner,
        path: String,
        task: TaskContext
    ) = ctx.submit {
        aclService.requirePermission(path.parent(), ctx.user, AccessRight.WRITE)
        aclService.requirePermission(path, ctx.user, AccessRight.WRITE)

        val systemFile = File(translateAndCheckFile(ctx, path))

        if (!Files.exists(systemFile.toPath(), LinkOption.NOFOLLOW_LINKS)) throw FSException.NotFound()
        traverseAndDelete(ctx, systemFile.toPath())
    }

    private fun delete(path: Path) {
        try {
            NativeFS.delete(path.toFile())
        } catch (ex: NoSuchFileException) {
            log.debug("File at $path does not exists any more. Ignoring this error.")
        }
    }

    private suspend fun traverseAndDelete(
        ctx: LinuxFSRunner,
        path: Path
    ) {
        if (Files.isSymbolicLink(path)) {
            delete(path)
            return
        }

        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            NativeFS.listFiles(path.toFile()).map { File(path.toFile(), it) }.forEach {
                traverseAndDelete(ctx, it.toPath())
            }
        }

        delete(path)
    }

    override suspend fun openForWriting(
        ctx: LinuxFSRunner,
        path: String,
        allowOverwrite: Boolean
    ) = ctx.submit {
        log.debug("${ctx.user} is attempting to open $path")
        val systemFile = File(translateAndCheckFile(ctx, path))
        aclService.requirePermission(path, ctx.user, AccessRight.WRITE)

        if (ctx.outputStream == null) {
            ctx.outputStream = BufferedOutputStream(NativeFS.openForWriting(systemFile, allowOverwrite))
            ctx.outputSystemFile = systemFile
        } else {
            log.warn("openForWriting called twice without closing old file!")
            throw FSException.CriticalException("Internal error")
        }
    }

    override suspend fun write(
        ctx: LinuxFSRunner,
        writer: suspend (OutputStream) -> Unit
    ) = ctx.submit {
        // Note: This function has already checked permissions via openForWriting
        val stream = ctx.outputStream
        val file = ctx.outputSystemFile
        if (stream == null || file == null) {
            log.warn("write() called without openForWriting()!")
            throw FSException.CriticalException("Internal error")
        }

        try {
            runBlocking {
                writer(stream)
            }
        } finally {
            stream.close()
            ctx.outputStream = null
            ctx.outputSystemFile = null
        }
    }

    override suspend fun tree(
        ctx: LinuxFSRunner,
        path: String,
        mode: Set<StorageFileAttribute>
    ): List<StorageFile> = ctx.submit {
        aclService.requirePermission(path, ctx.user, AccessRight.READ)

        val systemFile = File(translateAndCheckFile(ctx, path))
        val queue = LinkedList<File>()
        queue.add(systemFile)

        val result = ArrayList<StorageFile>()
        if (systemFile.isDirectory) {
            while (queue.isNotEmpty()) {
                val next = queue.pop()
                NativeFS.listFiles(next)
                    .map { File(next, it) }
                    .forEach {
                        if (Files.isSymbolicLink(it.toPath())) {
                            return@forEach
                        }

                        if (it.isDirectory) queue.add(it)
                        result.add(stat(ctx, it, mode, hasPerformedPermissionCheck = true))
                    }
            }
        }

        result
    }

    override suspend fun makeDirectory(
        ctx: LinuxFSRunner,
        path: String
    ) = ctx.submit {
        val systemFile = File(translateAndCheckFile(ctx, path))
        aclService.requirePermission(path.parent(), ctx.user, AccessRight.WRITE)
        NativeFS.createDirectories(systemFile)
        Unit
    }

    private fun getExtendedAttributeInternal(
        systemFile: File,
        attribute: String
    ): String? {
        return try {
            NativeFS.getExtendedAttribute(systemFile, "user.$attribute")
        } catch (ex: NativeException) {
            if (ex.statusCode == 61) return null
            if (ex.statusCode == 2) return null
            throw ex
        }
    }

    private suspend fun getExtendedAttribute(
        ctx: LinuxFSRunner,
        path: String,
        attribute: String
    ): String = ctx.submit {
        aclService.requirePermission(path, ctx.user, AccessRight.READ)
        getExtendedAttributeInternal(File(translateAndCheckFile(ctx, path)), attribute) ?: throw FSException.NotFound()
    }

    private suspend fun setExtendedAttribute(
        ctx: LinuxFSRunner,
        path: String,
        attribute: String,
        value: String,
        allowOverwrite: Boolean
    ) = ctx.submit {
        aclService.requirePermission(path, ctx.user, AccessRight.WRITE)

        val status = NativeFS.setExtendedAttribute(
            File(translateAndCheckFile(ctx, path)),
            "user.$attribute",
            value,
            allowOverwrite
        )

        if (status != 0) throw throwExceptionBasedOnStatus(status)

        Unit
    }

    private suspend fun deleteExtendedAttribute(
        ctx: LinuxFSRunner,
        path: String,
        attribute: String
    ) = ctx.submit {
        aclService.requirePermission(path, ctx.user, AccessRight.WRITE)
        NativeFS.removeExtendedAttribute(File(translateAndCheckFile(ctx, path)), "user.$attribute")
        Unit
    }

    override suspend fun getSensitivityLevel(ctx: LinuxFSRunner, path: String): SensitivityLevel? {
        return try {
            SensitivityLevel.valueOf(getExtendedAttribute(ctx, path, SENSITIVITY_XATTR))
        } catch (ex: FSException.NotFound) {
            stat(ctx, path, setOf(StorageFileAttribute.path))
            return null
        }
    }

    override suspend fun setSensitivityLevel(ctx: LinuxFSRunner, path: String, sensitivityLevel: SensitivityLevel?) {
        if (sensitivityLevel == null) {
            deleteExtendedAttribute(ctx, path, SENSITIVITY_XATTR)
        } else {
            setExtendedAttribute(ctx, path, SENSITIVITY_XATTR, sensitivityLevel.name, true)
        }
    }

    override suspend fun stat(ctx: LinuxFSRunner, path: String, mode: Set<StorageFileAttribute>): StorageFile =
        ctx.submit {
            val systemFile = File(translateAndCheckFile(ctx, path))
            aclService.requirePermission(path, ctx.user, AccessRight.READ)
            stat(ctx, systemFile, mode, hasPerformedPermissionCheck = true)
        }

    override suspend fun openForReading(ctx: LinuxFSRunner, path: String) = ctx.submit {
        aclService.requirePermission(path, ctx.user, AccessRight.READ)

        if (ctx.inputStream != null) {
            log.warn("openForReading() called without closing last stream")
            throw FSException.CriticalException("Internal error")
        }

        val systemFile = File(translateAndCheckFile(ctx, path))
        ctx.inputStream = NativeFS.openForReading(systemFile)
        ctx.inputSystemFile = systemFile
    }

    override suspend fun <R> read(
        ctx: LinuxFSRunner,
        range: LongRange?,
        consumer: suspend (InputStream) -> R
    ): R = ctx.submit {
        // Note: This function has already checked permissions via openForReading

        val stream = ctx.inputStream
        val file = ctx.inputSystemFile
        if (stream == null || file == null) {
            log.warn("read() called without calling openForReading()")
            throw FSException.CriticalException("Internal error")
        }

        val convertedToStream: InputStream = if (range != null) {
            stream.skip(range.first)
            CappedInputStream(stream, range.last - range.first)
        } else {
            stream
        }

        try {
            consumer(convertedToStream)
        } finally {
            convertedToStream.close()
            ctx.inputSystemFile = null
            ctx.inputStream = null
        }
    }

    override suspend fun normalizePermissions(ctx: LinuxFSRunner, path: String) {
        aclService.requirePermission(path, ctx.user, AccessRight.WRITE)
        val file = File(translateAndCheckFile(ctx, path))
        val stat = NativeFS.stat(file)
        NativeFS.changeFilePermissions(
            file,
            if (stat.fileType == FileType.DIRECTORY) NativeFS.DEFAULT_DIR_MODE else NativeFS.DEFAULT_FILE_MODE,
            LINUX_FS_USER_UID,
            LINUX_FS_USER_UID
        )
    }

    override suspend fun onFileCreated(ctx: LinuxFSRunner, path: String) {
        NativeFS.chown(File(translateAndCheckFile(ctx, path)), LINUX_FS_USER_UID, LINUX_FS_USER_UID)
    }

    private fun String.toCloudPath(): String {
        return ("/" + substringAfter(fsRoot.normalize().absolutePath).removePrefix("/")).normalize()
    }

    private fun translateAndCheckFile(
        ctx: LinuxFSRunner,
        internalPath: String,
        isDirectory: Boolean = false
    ): String {
        return translateAndCheckFile(fsRoot, internalPath, isDirectory, ctx.user == SERVICE_USER)
    }

    companion object : Loggable {
        override val log = logger()

        // Setting this to 4096 is too big for us to save files from workspaces. We want to leave a bit of buffer room.
        const val PATH_MAX = 3700

        val DEFAULT_FILE_MODE = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_WRITE
        )

        val DEFAULT_DIRECTORY_MODE = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_WRITE,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_EXECUTE
        )

        const val SENSITIVITY_XATTR = "sensitivity"
    }
}

fun translateAndCheckFile(
    fsRoot: File,
    internalPath: String,
    isDirectory: Boolean = false,
    isServiceUser: Boolean = false
): String {
    val projectRoot = (if (!isServiceUser) File(fsRoot, "projects") else fsRoot).absolutePath.normalize().removeSuffix("/") + "/"
    val root = (if (!isServiceUser) File(fsRoot, "home") else fsRoot).absolutePath.normalize().removeSuffix("/") + "/"
    val systemFile = File(fsRoot, internalPath)
    val path = systemFile
        .normalize()
        .absolutePath
        .let { it + (if (isDirectory) "/" else "") }

    if (Files.isSymbolicLink(systemFile.toPath())) {
        // We do not allow symlinks. Delete them if we detect them.
        systemFile.delete()
    }

    val isOutsideUserRoot = !path.startsWith(root) && path.removeSuffix("/") != root.removeSuffix("/")
    val isOutsideProjectRoot = !path.startsWith(projectRoot) && path.removeSuffix("/") != projectRoot.removeSuffix("/")
    if (isOutsideUserRoot && isOutsideProjectRoot) {
        throw FSException.BadRequest("path is not in user-root")
    }

    if (path.length >= PATH_MAX) {
        throw FSException.BadRequest("Path is too long ${path.length} '$path'")
    }

    return path
}
