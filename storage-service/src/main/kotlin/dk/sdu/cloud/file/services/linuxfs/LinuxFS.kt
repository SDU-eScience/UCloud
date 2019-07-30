@file:Suppress("BlockingMethodInNonBlockingContext")

package dk.sdu.cloud.file.services.linuxfs

import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.AccessEntry
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.FileSortBy
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.SortOrder
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.Timestamps
import dk.sdu.cloud.file.api.components
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.api.parent
import dk.sdu.cloud.file.services.FSResult
import dk.sdu.cloud.file.services.FileAttribute
import dk.sdu.cloud.file.services.FileRow
import dk.sdu.cloud.file.services.LowLevelFileSystemInterface
import dk.sdu.cloud.file.services.XATTR_BIRTH
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.services.acl.requirePermission
import dk.sdu.cloud.file.services.linuxfs.LinuxFS.Companion.PATH_MAX
import dk.sdu.cloud.file.services.mergeWith
import dk.sdu.cloud.file.util.CappedInputStream
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.file.util.STORAGE_EVENT_MODE
import dk.sdu.cloud.file.util.toCreatedEvent
import dk.sdu.cloud.file.util.toDeletedEvent
import dk.sdu.cloud.file.util.toMovedEvent
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import kotlin.math.min
import kotlin.streams.toList

class LinuxFS(
    fsRoot: File,
    private val aclService: AclService<*>
) : LowLevelFileSystemInterface<LinuxFSRunner> {
    private val fsRoot = fsRoot.normalize().absoluteFile

    // This function exists out of this class to avoid a circular dependency between ACLService and LinuxFS.
    // LinuxFS depends on ACLService for ACLs and ACLService depends on being able to fully normalize paths (this
    // includes removing all symlinks).
    private val realPathFunction = linuxFSRealPathSupplier(fsRoot)

    override suspend fun copy(
        ctx: LinuxFSRunner,
        from: String,
        to: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> = ctx.submit {
        val systemFrom = File(translateAndCheckFile(from))
        val systemTo = File(translateAndCheckFile(to))
        aclService.requirePermission(from, ctx.user, AccessRight.READ)
        aclService.requirePermission(to, ctx.user, AccessRight.WRITE)

        val opts = if (allowOverwrite) arrayOf(StandardCopyOption.REPLACE_EXISTING) else emptyArray()
        Files.copy(systemFrom.toPath(), systemTo.toPath(), *opts)
        FSResult(
            0,
            listOf(
                stat(
                    ctx,
                    systemTo,
                    STORAGE_EVENT_MODE,
                    hasPerformedPermissionCheck = true
                ).toCreatedEvent(true)
            )
        )
    }

    override suspend fun move(
        ctx: LinuxFSRunner,
        from: String,
        to: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.Moved>> = ctx.submit {
        val systemFrom = File(translateAndCheckFile(from))
        val systemTo = File(translateAndCheckFile(to))

        aclService.requirePermission(from, ctx.user, AccessRight.READ)
        aclService.requirePermission(to, ctx.user, AccessRight.WRITE)

        val opts = if (allowOverwrite) arrayOf(StandardCopyOption.REPLACE_EXISTING) else emptyArray()

        // We need to record some information from before the move
        val fromStat = stat(
            ctx,
            systemFrom,
            setOf(FileAttribute.FILE_TYPE, FileAttribute.PATH, FileAttribute.FILE_TYPE),
            hasPerformedPermissionCheck = true
        )

        val targetType =
            runCatching {
                stat(
                    ctx,
                    systemTo,
                    setOf(FileAttribute.FILE_TYPE),
                    hasPerformedPermissionCheck = true
                )
            }.getOrNull()?.fileType

        if (targetType != null && fromStat.fileType != targetType) {
            throw FSException.BadRequest("Target already exists and is not of same type as source.")
        }

        Files.move(systemFrom.toPath(), systemTo.toPath(), *opts)

        // We compare this information with after the move to get the correct old path.
        val toStat = stat(
            ctx,
            systemTo,
            STORAGE_EVENT_MODE,
            hasPerformedPermissionCheck = true
        )
        val basePath = toStat.path.removePrefix(toStat.path).removePrefix("/")
        val oldPath = if (fromStat.fileType == FileType.DIRECTORY) {
            joinPath(fromStat.path, basePath)
        } else {
            fromStat.path
        }

        val rows = if (fromStat.fileType == FileType.DIRECTORY) {
            // We need to emit events for every single file below this root.
            // TODO copyCausedBy
            Files.walk(systemTo.toPath()).toList().map {
                stat(
                    ctx,
                    it.toFile(),
                    STORAGE_EVENT_MODE,
                    hasPerformedPermissionCheck = true
                ).toMovedEvent(oldPath, copyCausedBy = true)
            }
        } else {
            listOf(toStat.toMovedEvent(oldPath, copyCausedBy = true))
        }

        FSResult(0, rows)
    }

    override suspend fun listDirectoryPaginated(
        ctx: LinuxFSRunner,
        directory: String,
        mode: Set<FileAttribute>,
        sortBy: FileSortBy?,
        paginationRequest: NormalizedPaginationRequest?,
        order: SortOrder?
    ): FSResult<Page<FileRow>> = ctx.submit {
        aclService.requirePermission(directory, ctx.user, AccessRight.READ)

        val systemFiles = run {
            val file = File(translateAndCheckFile(directory))
            val requestedDirectory = file.takeIf { it.exists() } ?: throw FSException.NotFound()

            (requestedDirectory.listFiles() ?: throw FSException.PermissionException()).toList()
        }

        val min = if (paginationRequest == null) 0 else paginationRequest.itemsPerPage * paginationRequest.page
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
                FileSortBy.TYPE -> FileAttribute.FILE_TYPE
                FileSortBy.PATH -> FileAttribute.PATH
                FileSortBy.CREATED_AT, FileSortBy.MODIFIED_AT -> FileAttribute.TIMESTAMPS
                FileSortBy.SIZE -> FileAttribute.SIZE
                FileSortBy.ACL -> FileAttribute.SHARES
                FileSortBy.SENSITIVITY -> FileAttribute.SENSITIVITY
                null -> FileAttribute.PATH
            }

            val statsForSorting = stat(
                ctx,
                systemFiles,
                setOf(FileAttribute.PATH, sortingAttribute),
                hasPerformedPermissionCheck = true
            ).filterNotNull()

            val comparator = comparatorForFileRows(sortBy, order)

            val relevantFileRows = statsForSorting.sortedWith(comparator).subList(min, max)

            // Time for the second lookup. We will retrieve all attributes we don't already know about and merge them
            // with first lookup.
            val relevantFiles = relevantFileRows.map { File(translateAndCheckFile(it.path)) }

            val desiredMode = mode - setOf(sortingAttribute) + setOf(FileAttribute.PATH)

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

        FSResult(0, page)
    }

    private fun comparatorForFileRows(
        sortBy: FileSortBy,
        order: SortOrder
    ): Comparator<FileRow> {
        val naturalComparator: Comparator<FileRow> = when (sortBy) {
            FileSortBy.ACL -> Comparator.comparingInt { it.shares.size }

            FileSortBy.CREATED_AT -> Comparator.comparingLong { it.timestamps.created }

            FileSortBy.MODIFIED_AT -> Comparator.comparingLong { it.timestamps.modified }

            FileSortBy.TYPE -> Comparator.comparing<FileRow, String> {
                it.fileType.name
            }.thenComparing(Comparator.comparing<FileRow, String> {
                it.path.fileName().toLowerCase()
            })

            FileSortBy.PATH -> Comparator.comparing<FileRow, String> {
                it.path.fileName().toLowerCase()
            }

            FileSortBy.SIZE -> Comparator.comparingLong { it.size }

            // TODO This should be resolved before sorting
            FileSortBy.SENSITIVITY -> Comparator.comparing<FileRow, String> {
                (it.sensitivityLevel?.name?.toLowerCase()) ?: "inherit"
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
        mode: Set<FileAttribute>,
        hasPerformedPermissionCheck: Boolean,
        followLink: Boolean = false
    ): FileRow {
        return stat(ctx, listOf(systemFile), mode, hasPerformedPermissionCheck, followLink).first()
            ?: throw FSException.NotFound()
    }

    private suspend fun stat(
        ctx: LinuxFSRunner,
        systemFiles: List<File>,
        mode: Set<FileAttribute>,
        hasPerformedPermissionCheck: Boolean,
        followLink: Boolean = false
    ): List<FileRow?> {
        // The 'shareLookup' contains a mapping between cloud paths and their ACL
        val shareLookup = if (FileAttribute.SHARES in mode) {
            val parents = systemFiles.map { it.path.toCloudPath().parent().normalize() }

            // We optimize in the case that we only have a single unique parent. This makes it quite a bit more
            // efficient.
            if (parents.size == 1) {
                aclService.listAclsForChildrenOf(
                    parents.first(),
                    parents
                )
            } else {
                aclService.listAcl(systemFiles.map { it.path.toCloudPath().normalize() })
            }
        } else {
            emptyMap()
        }

        log.debug("Result of shareLookup is $shareLookup")

        return systemFiles.map { systemFile ->
            val capturedIsLink = Files.isSymbolicLink(systemFile.toPath())
            // TODO This should just remove it silently?
            if (capturedIsLink) throw FSException.CriticalException("$systemFile is a symbolic link")

            try {
                if (!hasPerformedPermissionCheck) {
                    aclService.requirePermission(systemFile.path.toCloudPath(), ctx.user, AccessRight.READ)
                }

                var fileType: FileType? = null
                var unixMode: Int? = null
                var timestamps: Timestamps? = null
                var path: String? = null
                var inode: String? = null
                var size: Long? = null
                var shares: List<AccessEntry>? = null
                var sensitivityLevel: SensitivityLevel? = null

                val systemPath = try {
                    systemFile.toPath()
                } catch (ex: InvalidPathException) {
                    throw FSException.BadRequest()
                }

                val linkOpts = if (!followLink) arrayOf(LinkOption.NOFOLLOW_LINKS) else emptyArray()

                run {
                    // UNIX file attributes
                    val attributes = run {
                        val opts = ArrayList<String>()
                        if (FileAttribute.INODE in mode) opts.add("ino")
                        if (FileAttribute.CREATOR in mode) opts.add("uid")
                        if (FileAttribute.UNIX_MODE in mode) opts.add("mode")

                        if (opts.isEmpty()) {
                            null
                        } else {
                            Files.readAttributes(systemPath, "unix:${opts.joinToString(",")}", *linkOpts)
                        }
                    } ?: return@run

                    if (FileAttribute.INODE in mode) inode = (attributes.getValue("ino") as Long).toString()
                    if (FileAttribute.UNIX_MODE in mode) unixMode = attributes["mode"] as Int
                }

                run {
                    // Basic file attributes and symlinks
                    val basicAttributes = run {
                        val opts = ArrayList<String>()

                        // We always add SIZE. This will make sure we always get a stat executed and thus throw if the
                        // file doesn't actually exist.
                        opts.add("size")

                        if (FileAttribute.TIMESTAMPS in mode) {
                            // Note we don't rely on the creationTime due to not being available in all file systems.
                            opts.addAll(listOf("lastAccessTime", "lastModifiedTime"))
                        }

                        if (FileAttribute.FILE_TYPE in mode) {
                            opts.add("isDirectory")
                        }

                        if (opts.isEmpty()) {
                            null
                        } else {
                            Files.readAttributes(systemPath, opts.joinToString(","), *linkOpts)
                        }
                    }

                    if (FileAttribute.PATH in mode) path = systemFile.absolutePath.toCloudPath()

                    if (basicAttributes != null) {
                        // We need to always ask if this file is a link to correctly resolve target file type.

                        if (FileAttribute.SIZE in mode) size = basicAttributes.getValue("size") as Long

                        if (FileAttribute.FILE_TYPE in mode) {
                            val isDirectory = if (capturedIsLink) {
                                Files.isDirectory(systemPath)
                            } else {
                                basicAttributes.getValue("isDirectory") as Boolean
                            }

                            fileType = if (isDirectory) {
                                FileType.DIRECTORY
                            } else {
                                FileType.FILE
                            }
                        }

                        if (FileAttribute.TIMESTAMPS in mode) {
                            val lastAccess = basicAttributes.getValue("lastAccessTime") as FileTime
                            val lastModified = basicAttributes.getValue("lastModifiedTime") as FileTime

                            // The extended attribute is set by CoreFS
                            // Old setup would ignore errors. This is required for createLink to work
                            val creationTime =
                                runCatching {
                                    getExtendedAttributeInternal(systemFile, XATTR_BIRTH)?.toLongOrNull()
                                        ?.let { it * 1000 }
                                }.getOrNull() ?: lastModified.toMillis()

                            timestamps = Timestamps(
                                lastAccess.toMillis(),
                                creationTime,
                                lastModified.toMillis()
                            )
                        }
                    }
                }

                val realOwner = if (FileAttribute.OWNER in mode || FileAttribute.CREATOR in mode) {
                    val toCloudPath = systemFile.absolutePath.toCloudPath()
                    val realPath = realPathFunction(toCloudPath)

                    val components = realPath.components()
                    when {
                        components.isEmpty() -> SERVICE_USER
                        components.first() != "home" -> SERVICE_USER
                        components.size < 2 -> SERVICE_USER
                        else -> // TODO This won't work for projects (?)
                            components[1]
                    }
                } else {
                    null
                }

                if (FileAttribute.SENSITIVITY in mode) {
                    // Old setup would ignore errors. This is required for createLink to work
                    sensitivityLevel =
                        runCatching {
                            getExtendedAttributeInternal(
                                systemFile,
                                "sensitivity"
                            )?.let { SensitivityLevel.valueOf(it) }
                        }.getOrNull()
                }

                if (FileAttribute.SHARES in mode) {
                    val cloudPath = systemFile.path.toCloudPath()
                    shares = shareLookup.getOrDefault(cloudPath, emptyList()).map {
                        AccessEntry(it.username, it.permissions)
                    }
                }

                FileRow(
                    fileType,
                    unixMode,
                    realOwner,
                    "",
                    timestamps,
                    path,
                    inode,
                    size,
                    shares,
                    sensitivityLevel,
                    realOwner
                )
            } catch (ex: NoSuchFileException) {
                null
            }
        }
    }

    override suspend fun delete(ctx: LinuxFSRunner, path: String): FSResult<List<StorageEvent.Deleted>> =
        ctx.submit {
            aclService.requirePermission(path.parent(), ctx.user, AccessRight.WRITE)
            aclService.requirePermission(path, ctx.user, AccessRight.WRITE)

            val systemFile = File(translateAndCheckFile(path))
            val deletedRows = ArrayList<StorageEvent.Deleted>()

            if (!Files.exists(systemFile.toPath(), LinkOption.NOFOLLOW_LINKS)) throw FSException.NotFound()
            traverseAndDelete(ctx, systemFile.toPath(), deletedRows)

            FSResult(0, deletedRows.toList())
        }

    private suspend fun delete(
        ctx: LinuxFSRunner,
        path: Path,
        deletedRows: ArrayList<StorageEvent.Deleted>
    ) {
        try {
            val stat = stat(ctx, path.toFile(), STORAGE_EVENT_MODE, hasPerformedPermissionCheck = true)
            Files.delete(path)
            deletedRows.add(stat.toDeletedEvent(true))
        } catch (ex: NoSuchFileException) {
            log.debug("File at $path does not exists any more. Ignoring this error.")
        }
    }

    private suspend fun traverseAndDelete(
        ctx: LinuxFSRunner,
        path: Path,
        deletedRows: ArrayList<StorageEvent.Deleted>
    ) {
        if (Files.isSymbolicLink(path)) {
            delete(ctx, path, deletedRows)
            return
        }

        if (Files.isDirectory(path)) {
            path.toFile().listFiles()?.forEach {
                traverseAndDelete(ctx, it.toPath(), deletedRows)
            }
        }

        delete(ctx, path, deletedRows)
    }

    override suspend fun openForWriting(
        ctx: LinuxFSRunner,
        path: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> = ctx.submit {
        log.debug("${ctx.user} is attempting to open $path")
        val systemFile = File(translateAndCheckFile(path))
        aclService.requirePermission(path, ctx.user, AccessRight.WRITE)

        if (ctx.outputStream == null) {
            val options = HashSet<OpenOption>()
            options.add(StandardOpenOption.TRUNCATE_EXISTING)
            options.add(StandardOpenOption.WRITE)
            if (!allowOverwrite) {
                options.add(StandardOpenOption.CREATE_NEW)
            } else {
                options.add(StandardOpenOption.CREATE)
            }

            try {
                val systemPath = systemFile.toPath()
                ctx.outputStream = Channels.newOutputStream(
                    Files.newByteChannel(systemPath, options, PosixFilePermissions.asFileAttribute(DEFAULT_FILE_MODE))
                )
                ctx.outputSystemFile = systemFile
            } catch (ex: FileAlreadyExistsException) {
                throw FSException.AlreadyExists()
            }

            FSResult(
                0,
                listOf(
                    stat(
                        ctx,
                        systemFile,
                        STORAGE_EVENT_MODE,
                        hasPerformedPermissionCheck = true
                    ).toCreatedEvent(true)
                )
            )
        } else {
            log.warn("openForWriting called twice without closing old file!")
            throw FSException.CriticalException("Internal error")
        }
    }

    override suspend fun write(
        ctx: LinuxFSRunner,
        writer: suspend (OutputStream) -> Unit
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> = ctx.submit {
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

        FSResult(
            0,
            listOf(
                stat(ctx, file, STORAGE_EVENT_MODE, hasPerformedPermissionCheck = false).toCreatedEvent(true)
            )
        )
    }

    override suspend fun tree(
        ctx: LinuxFSRunner,
        path: String,
        mode: Set<FileAttribute>
    ): FSResult<List<FileRow>> = ctx.submit {
        aclService.requirePermission(path, ctx.user, AccessRight.READ)

        val systemFile = File(translateAndCheckFile(path))
        FSResult(
            0,
            Files.walk(systemFile.toPath())
                .toList()
                .map {
                    stat(ctx, it.toFile(), mode, hasPerformedPermissionCheck = true)
                }
        )
    }

    override suspend fun makeDirectory(
        ctx: LinuxFSRunner,
        path: String
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> = ctx.submit {
        val systemFile = File(translateAndCheckFile(path))
        aclService.requirePermission(path.parent(), ctx.user, AccessRight.WRITE)

        Files.createDirectory(systemFile.toPath(), PosixFilePermissions.asFileAttribute(DEFAULT_DIRECTORY_MODE))

        FSResult(
            0,
            listOf(
                stat(
                    ctx,
                    systemFile,
                    STORAGE_EVENT_MODE,
                    hasPerformedPermissionCheck = true
                ).toCreatedEvent(true)
            )
        )
    }

    private fun getExtendedAttributeInternal(
        systemFile: File,
        attribute: String
    ): String? {
        return try {
            StandardCLib.getxattr(systemFile.absolutePath, "user.$attribute")
        } catch (ex: NativeException) {
            if (ex.statusCode == 61) return null
            if (ex.statusCode == 2) return null
            throw ex
        }
    }

    override suspend fun getExtendedAttribute(
        ctx: LinuxFSRunner,
        path: String,
        attribute: String
    ): FSResult<String> = ctx.submit {
        // TODO Should this be owner only?
        aclService.requirePermission(path, ctx.user, AccessRight.READ)

        FSResult(
            0,
            getExtendedAttributeInternal(File(translateAndCheckFile(path)), attribute)
        )
    }

    override suspend fun setExtendedAttribute(
        ctx: LinuxFSRunner,
        path: String,
        attribute: String,
        value: String,
        allowOverwrite: Boolean
    ): FSResult<Unit> = ctx.submit {
        // TODO Should this be owner only?
        aclService.requirePermission(path, ctx.user, AccessRight.WRITE)

        FSResult(
            StandardCLib.setxattr(
                translateAndCheckFile(path),
                "user.$attribute",
                value,
                allowOverwrite
            ),
            Unit
        )
    }

    override suspend fun listExtendedAttribute(ctx: LinuxFSRunner, path: String): FSResult<List<String>> =
        ctx.submit {
            // TODO Should this be owner only?
            aclService.requirePermission(path, ctx.user, AccessRight.READ)

            FSResult(
                0,
                StandardCLib.listxattr(translateAndCheckFile(path)).map { it.removePrefix("user.") }
            )
        }

    override suspend fun deleteExtendedAttribute(
        ctx: LinuxFSRunner,
        path: String,
        attribute: String
    ): FSResult<Unit> = ctx.submit {
        // TODO Should this be owner only?
        aclService.requirePermission(path, ctx.user, AccessRight.WRITE)

        FSResult(
            StandardCLib.removexattr(translateAndCheckFile(path), "user.$attribute"),
            Unit
        )
    }

    override suspend fun stat(ctx: LinuxFSRunner, path: String, mode: Set<FileAttribute>): FSResult<FileRow> =
        ctx.submit {
            val systemFile = File(translateAndCheckFile(path))
            aclService.requirePermission(path, ctx.user, AccessRight.READ)

            FSResult(
                0,
                stat(ctx, systemFile, mode, hasPerformedPermissionCheck = true)
            )
        }

    override suspend fun openForReading(ctx: LinuxFSRunner, path: String): FSResult<Unit> = ctx.submit {
        aclService.requirePermission(path, ctx.user, AccessRight.READ)

        if (ctx.inputStream != null) {
            log.warn("openForReading() called without closing last stream")
            throw FSException.CriticalException("Internal error")
        }

        val systemFile = File(translateAndCheckFile(path))
        ctx.inputStream = FileChannel.open(systemFile.toPath(), StandardOpenOption.READ)
        ctx.inputSystemFile = systemFile
        FSResult(0, Unit)
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
            stream.position(range.first)
            CappedInputStream(Channels.newInputStream(stream), range.last - range.first)
        } else {
            Channels.newInputStream(stream)
        }

        try {
            consumer(convertedToStream)
        } finally {
            convertedToStream.close()
            ctx.inputSystemFile = null
            ctx.inputStream = null
        }
    }

    private fun String.toCloudPath(): String {
        return linuxFSToCloudPath(fsRoot, this)
    }

    private fun translateAndCheckFile(internalPath: String, isDirectory: Boolean = false): String {
        return translateAndCheckFile(fsRoot, internalPath, isDirectory)
    }

    companion object : Loggable {
        override val log = logger()
        const val PATH_MAX = 1024

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
    }
}

fun linuxFSRealPathSupplier(fsRoot: File): (String) -> String = f@{ path: String ->
    val systemFile = translateAndCheckFile(fsRoot, path)

    val realPath = if (File(systemFile).exists()) {
        StandardCLib.realPath(systemFile) ?: StandardCLib.realPath(systemFile.parent()) ?: throw FSException.NotFound()
    } else {
        StandardCLib.realPath(systemFile.parent()) ?: throw FSException.NotFound()
    }

    linuxFSToCloudPath(fsRoot, realPath)
}

private fun linuxFSToCloudPath(fsRoot: File, path: String): String {
    return ("/" + path.substringAfter(fsRoot.normalize().absolutePath).removePrefix("/")).normalize()
}

fun translateAndCheckFile(fsRoot: File, internalPath: String, isDirectory: Boolean = false): String {
    val userRoot = File(fsRoot, "home").absolutePath.normalize().removeSuffix("/") + "/"
    val systemFile = File(fsRoot, internalPath)
    val path = systemFile
        .normalize()
        .absolutePath
        .let { it + (if (isDirectory) "/" else "") }

    if (Files.isSymbolicLink(systemFile.toPath())) {
        // We do not allow symlinks. Delete them if we detect them.
        systemFile.delete()
    }

    if (!path.startsWith(userRoot) && path.removeSuffix("/") != userRoot.removeSuffix("/")) {
        throw FSException.BadRequest("path is not in user-root")
    }

    if (path.contains("\n")) throw FSException.BadRequest("Path cannot contain new-lines")
    if (path.length >= PATH_MAX) throw FSException.BadRequest("Path is too long")

    return path
}

