/*@file:Suppress("BlockingMethodInNonBlockingContext")

package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.services.acl.AclService
import dk.sdu.cloud.file.ucloud.services.acl.PERSONAL_REPOSITORY
import dk.sdu.cloud.file.ucloud.services.acl.requirePermission
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.task.api.TaskContext
import kotlinx.coroutines.runBlocking
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.*
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.*
import kotlin.Comparator
import kotlin.collections.ArrayList
import kotlin.math.min

typealias CephConfiguration = Unit

class UCloudFileSystem(
    fsRoot: File,
    private val aclService: AclService,
    private val cephConfiguration: CephConfiguration
) {
    private val fsRoot = fsRoot.normalize().absoluteFile

    suspend fun requirePermission(actor: Actor, path: String, permission: FilePermission) {
        aclService.requirePermission(path.normalize(), actor, permission)
    }

    suspend fun copy(
        actor: Actor,
        from: String,
        to: String,
        writeConflictPolicy: WriteConflictPolicy,
        task: TaskContext
    ) {
        translateAndCheckFile(actor, from)
        aclService.requirePermission(from, actor, FilePermission.READ)
        aclService.requirePermission(to, actor, FilePermission.WRITE)

        return copyPreAuthorized(actor, from, to, writeConflictPolicy)
    }

    private fun copyPreAuthorized(
        actor: Actor,
        from: String,
        to: String,
        writeConflictPolicy: WriteConflictPolicy
    ) {
        val systemFrom = File(translateAndCheckFile(actor, from))
        val systemTo = File(translateAndCheckFile(actor, to))

        if (from.normalize() == to.normalize() && writeConflictPolicy == WriteConflictPolicy.REPLACE) {
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
            val originalPermission = NativeFS.readNativeFilePermissons(systemFrom)
            NativeFS.openForReading(systemFrom).use { ins ->
                NativeFS.openForWriting(
                    systemTo,
                    writeConflictPolicy.allowsOverwrite(),
                    permissions = originalPermission
                ).use { outs ->
                    ins.copyTo(outs)
                }
            }
        }
    }

    suspend fun move(
        actor: Actor,
        from: String,
        to: String,
        writeConflictPolicy: WriteConflictPolicy,
        task: TaskContext
    ) {
        val systemFrom = File(translateAndCheckFile(actor, from))
        val systemTo = File(translateAndCheckFile(actor, to))

        // We need write permission on from's parent to avoid being able to steal a file by changing the owner.
        aclService.requirePermission(from.parent(), actor, FilePermission.WRITE)
        aclService.requirePermission(to, actor, FilePermission.WRITE)

        val fromComponents = from.normalize().components()
        val toComponents = to.normalize().components()
        if ((fromComponents.size in setOf(3) && fromComponents[0] == "projects" && fromComponents[2] == PERSONAL_REPOSITORY) ||
            (toComponents.size in setOf(3, 4) && toComponents[0] == "projects" && toComponents[2] == PERSONAL_REPOSITORY)) {
            // The personal repository and direct children can _only_ be changed by the service user
            throw FSException.PermissionException()
        }

        // We need to record some information from before the move
        val fromStat = stat(
            actor,
            systemFrom,
            setOf(StorageFileAttribute.fileType, StorageFileAttribute.path, StorageFileAttribute.fileType),
            hasPerformedPermissionCheck = true
        )

        val targetType =
            runCatching {
                stat(
                    actor,
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
                writeConflictPolicy == WriteConflictPolicy.REPLACE
            ) {
                throw FSException.BadRequest("Directory is not allowed to overwrite existing directory")
            }
        }

        movePreAuthorized(actor, from, to, writeConflictPolicy)
    }

    private suspend fun movePreAuthorized(
        actor: Actor,
        from: String,
        to: String,
        writeConflictPolicy: WriteConflictPolicy
    ) {
        val systemFrom = File(translateAndCheckFile(actor, from))
        val systemTo = File(translateAndCheckFile(actor, to))
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

    suspend fun listDirectoryPaginated(
        actor: Actor,
        request: FilesBrowseRequest,
    ): PageV2<UFile> {
        aclService.requirePermission(request.path, actor, FilePermission.READ)

        val systemFiles = run {
            val file = File(translateAndCheckFile(actor, request.path))
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
        actor: Actor,
        systemFile: File,
        mode: Set<StorageFileAttribute>,
        hasPerformedPermissionCheck: Boolean
    ): UFile {
        return stat(ctx, listOf(systemFile), mode, hasPerformedPermissionCheck).first()
            ?: throw FSException.NotFound()
    }

    private suspend fun stat(
        actor: Actor,
        systemFiles: List<File>,
        mode: Set<StorageFileAttribute>,
        hasPerformedPermissionCheck: Boolean
    ): List<UFile?> {
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
                            if (aclService.isOwner(realPath, ctx.user)) {
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

                StorageFile(
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

    suspend fun delete(
        actor: Actor,
        path: String,
        task: TaskContext
    ) {
        aclService.requirePermission(path.parent(), actor, FilePermission.WRITE)
        aclService.requirePermission(path, actor, FilePermission.WRITE)

        val systemFile = File(translateAndCheckFile(ctx, path))

        if (!Files.exists(systemFile.toPath(), LinkOption.NOFOLLOW_LINKS)) throw FSException.NotFound()
        traverseAndDelete(actor, systemFile.toPath())
    }

    private fun delete(path: Path) {
        try {
            NativeFS.delete(path.toFile())
        } catch (ex: NoSuchFileException) {
            log.debug("File at $path does not exists any more. Ignoring this error.")
        }
    }

    private suspend fun traverseAndDelete(
        actor: Actor,
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

    suspend fun openForWriting(
        actor: Actor,
        path: String,
        allowOverwrite: Boolean
    ) {
        log.debug("${ctx.user} is attempting to open $path")
        val systemFile = File(translateAndCheckFile(ctx, path))
        aclService.requirePermission(path, actor, FilePermission.WRITE)
        val components = path.normalize().components()
        if (ctx.user != SERVICE_USER) {
            if ((components.size == 3 && components[0] == "projects") ||
                (components.size == 4 && components[0] == "projects" && components[2] == PERSONAL_REPOSITORY)
            ) {
                throw FSException.PermissionException()
            }
        }

        if (ctx.outputStream == null) {
            ctx.outputStream = BufferedOutputStream(
                NativeFS.openForWriting(
                    systemFile,
                    allowOverwrite,
                    permissions = null
                ))
            ctx.outputSystemFile = systemFile
        } else {
            log.warn("openForWriting called twice without closing old file!")
            throw FSException.CriticalException("Internal error")
        }
    }

    suspend fun checkWritePermissions(actor: Actor, path: String) {
        aclService.requirePermission(path, actor, FilePermission.WRITE)
    }

    suspend fun write(
        actor: Actor,
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

    suspend fun tree(
        actor: Actor,
        path: String,
        mode: Set<StorageFileAttribute>
    ): List<UFile> {
        aclService.requirePermission(path, actor, FilePermission.READ)

        val systemFile = File(translateAndCheckFile(ctx, path))
        val queue = LinkedList<File>()
        queue.add(systemFile)

        val result = ArrayList<UFile>()
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
                        result.add(stat(actor, it, mode, hasPerformedPermissionCheck = true))
                    }
            }
        }

        return result
    }

    suspend fun makeDirectory(
        actor: Actor,
        path: String
    ) {
        val systemFile = File(translateAndCheckFile(actor, path))

        val components = path.normalize().components()
        if (actor != Actor.System && components.size == 3 && components[2] == PERSONAL_REPOSITORY) {
            // The personal repository and direct children can _only_ be changed by the service user
            throw FSException.PermissionException()
        }

        aclService.requirePermission(path.parent(), actor, FilePermission.WRITE)
        NativeFS.createDirectories(systemFile)
        Unit
    }

    suspend fun stat(ctx: Actor, path: String, mode: Set<StorageFileAttribute>): UFile {
        val systemFile = File(translateAndCheckFile(ctx, path))
        aclService.requirePermission(path, actor, FilePermission.READ)
        return stat(ctx, systemFile, mode, hasPerformedPermissionCheck = true)
    }

    suspend fun openForReading(actor: Actor, path: String) {
        aclService.requirePermission(path, actor, FilePermission.READ)

        if (ctx.inputStream != null) {
            log.warn("openForReading() called without closing last stream")
            throw FSException.CriticalException("Internal error")
        }

        val systemFile = File(translateAndCheckFile(ctx, path))
        ctx.inputStream = NativeFS.openForReading(systemFile)
        ctx.inputSystemFile = systemFile
    }

    suspend fun <R> read(
        actor: Actor,
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

    suspend fun normalizePermissions(actor: Actor, path: String) {
        aclService.requirePermission(path, actor, FilePermission.WRITE)
        val file = File(translateAndCheckFile(ctx, path))
        val stat = NativeFS.stat(file)
        NativeFS.changeFilePermissions(
            file,
            if (stat.fileType == FileType.DIRECTORY) NativeFS.DEFAULT_DIR_MODE else NativeFS.DEFAULT_FILE_MODE,
            LINUX_FS_USER_UID,
            LINUX_FS_USER_UID
        )
    }

    suspend fun onFileCreated(actor: Actor, path: String) {
        NativeFS.chown(File(translateAndCheckFile(ctx, path)), LINUX_FS_USER_UID, LINUX_FS_USER_UID)
    }

    suspend fun calculateRecursiveStorageUsed(actor: Actor, path: String): Long {
        if (cephConfiguration.useCephDirectoryStats) return estimateRecursiveStorageUsedMakeItFast(ctx, path)

        aclService.requirePermission(path, actor, FilePermission.READ)

        val systemFile = File(translateAndCheckFile(ctx, path))
        val queue = LinkedList<File>()
        queue.add(systemFile)

        var sum = 0L

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
                        val s = stat(ctx, it, setOf(StorageFileAttribute.size), hasPerformedPermissionCheck = true)
                        sum += s.size
                    }
            }
        } else {
            val s = stat(ctx, systemFile, setOf(StorageFileAttribute.size), hasPerformedPermissionCheck = true)
            sum = s.size
        }

        return sum
    }

    suspend fun estimateRecursiveStorageUsedMakeItFast(actor: Actor, path: String): Long {
        aclService.requirePermission(path, actor, FilePermission.READ)
        return if (!cephConfiguration.useCephDirectoryStats) {
            // Just assume we'll use 30GB. This is a really bad estimate.
            30L * 1000 * 1000 * 1000
        } else {
            CephFsFastDirectoryStats.getRecursiveSize(File(translateAndCheckFile(ctx, path)))
        }
    }

    private fun String.toCloudPath(): String {
        return ("/" + substringAfter(fsRoot.normalize().absolutePath).removePrefix("/")).normalize()
    }

    private fun translateAndCheckFile(
        actor: Actor,
        internalPath: String,
        isDirectory: Boolean = false
    ): String {
        return translateAndCheckFile(fsRoot, internalPath, isDirectory, actor == Actor.System)
    }

    companion object : Loggable {
        override val log = logger()

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

    return path
}
 */