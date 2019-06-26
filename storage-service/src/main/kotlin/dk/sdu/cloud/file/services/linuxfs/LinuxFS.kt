package dk.sdu.cloud.file.services.linuxfs

import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.file.services.*
import dk.sdu.cloud.file.services.acl.AclPermission
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.services.acl.requirePermission
import dk.sdu.cloud.file.services.linuxfs.LinuxFS.Companion.PATH_MAX
import dk.sdu.cloud.file.util.*
import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import kotlin.streams.toList

class LinuxFS(
    fsRoot: File,
    private val aclService: AclService<*>
) : LowLevelFileSystemInterface<LinuxFSRunner> {
    private val fsRoot = fsRoot.normalize().absoluteFile

    override suspend fun copy(
        ctx: LinuxFSRunner,
        from: String,
        to: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> = runAndRethrowNIOExceptions {
        aclService.requirePermission(from, ctx.user, AclPermission.READ)
        aclService.requirePermission(to, ctx.user, AclPermission.WRITE)

        val opts = if (allowOverwrite) arrayOf(StandardCopyOption.REPLACE_EXISTING) else emptyArray()
        val systemFrom = File(translateAndCheckFile(from))
        val systemTo = File(translateAndCheckFile(to))
        Files.copy(systemFrom.toPath(), systemTo.toPath(), *opts)
        FSResult(
            0,
            listOf(
                stat(
                    ctx,
                    systemTo,
                    STORAGE_EVENT_MODE,
                    HashMap(),
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
    ): FSResult<List<StorageEvent.Moved>> = runAndRethrowNIOExceptions {
        aclService.requirePermission(from, ctx.user, AclPermission.READ)
        aclService.requirePermission(to, ctx.user, AclPermission.WRITE)

        val opts = if (allowOverwrite) arrayOf(StandardCopyOption.REPLACE_EXISTING) else emptyArray()
        val systemFrom = File(translateAndCheckFile(from))
        val systemTo = File(translateAndCheckFile(to))

        // We need to record some information from before the move
        val fromStat = stat(
            ctx,
            systemFrom,
            setOf(FileAttribute.FILE_TYPE, FileAttribute.PATH, FileAttribute.FILE_TYPE),
            HashMap(),
            hasPerformedPermissionCheck = true
        )

        val targetType =
            runCatching {
                stat(
                    ctx,
                    systemTo,
                    setOf(FileAttribute.FILE_TYPE),
                    HashMap(),
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
            HashMap(),
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
            val cache = HashMap<String, String>()
            Files.walk(systemTo.toPath()).toList().map {
                stat(
                    ctx,
                    it.toFile(),
                    STORAGE_EVENT_MODE,
                    cache,
                    hasPerformedPermissionCheck = true
                ).toMovedEvent(oldPath, copyCausedBy = true)
            }
        } else {
            listOf(toStat.toMovedEvent(oldPath, copyCausedBy = true))
        }

        FSResult(0, rows)
    }

    override suspend fun listDirectory(
        ctx: LinuxFSRunner,
        directory: String,
        mode: Set<FileAttribute>
    ): FSResult<List<FileRow>> = runAndRethrowNIOExceptions {
        aclService.requirePermission(directory, ctx.user, AclPermission.READ)

        val file = File(translateAndCheckFile(directory))
        val requestedDirectory = file.takeIf { it.exists() } ?: throw FSException.NotFound()

        // TODO Maybe this works
        // TODO Maybe this works
        // TODO Maybe this works
        // TODO Maybe this works
        // TODO Maybe this works
        // TODO Maybe this works
        FSResult(
            0,
            stat(
                ctx,
                (requestedDirectory.listFiles() ?: throw FSException.PermissionException()).toList(),
                mode,
                HashMap<String, String>(),
                hasPerformedPermissionCheck = true
            ).filterNotNull()
        )
    }

    private suspend fun stat(
        ctx: LinuxFSRunner,
        systemFile: File,
        mode: Set<FileAttribute>,
        pathCache: MutableMap<String, String>,
        hasPerformedPermissionCheck: Boolean,
        followLink: Boolean = false
    ): FileRow {
        return stat(ctx, listOf(systemFile), mode, pathCache, hasPerformedPermissionCheck, followLink).first()
            ?: throw FSException.NotFound()
    }

    private suspend fun stat(
        ctx: LinuxFSRunner,
        systemFiles: List<File>,
        mode: Set<FileAttribute>,
        pathCache: MutableMap<String, String>,
        hasPerformedPermissionCheck: Boolean,
        followLink: Boolean = false
    ): List<FileRow?> {
        val shareLookup = if (FileAttribute.SHARES in mode) {
            val paths = systemFiles.map { it.path.toCloudPath() }
            aclService.listAcl(paths)
        } else {
            emptyMap()
        }

        return systemFiles.map { systemFile ->
            try {
                if (!hasPerformedPermissionCheck) {
                    aclService.requirePermission(systemFile.path.toCloudPath(), ctx.user, AclPermission.READ)
                }

                var fileType: FileType? = null
                var isLink: Boolean? = null
                var linkTarget: String? = null
                var unixMode: Int? = null
                var timestamps: Timestamps? = null
                var path: String? = null
                var rawPath: String? = null
                var inode: String? = null
                var size: Long? = null
                var shares: List<AccessEntry>? = null
                var sensitivityLevel: SensitivityLevel? = null
                var linkInode: String? = null

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

                    if (FileAttribute.RAW_PATH in mode) rawPath = systemFile.absolutePath.toCloudPath()
                    if (FileAttribute.PATH in mode || FileAttribute.OWNER in mode) { // owner requires path
                        val realParent = run {
                            val parent = systemFile.parent
                            val cached = pathCache[parent]
                            if (cached != null) {
                                cached
                            } else {
                                val result = StandardCLib.realPath(parent) ?: parent
                                pathCache[parent] = result
                                result
                            }
                        }

                        path = joinPath(realParent, systemFile.name).toCloudPath()
                    }

                    if (basicAttributes != null) {
                        // We need to always ask if this file is a link to correctly resolve target file type.
                        val capturedIsLink = Files.isSymbolicLink(systemFile.toPath())
                        isLink = capturedIsLink

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

                        if (FileAttribute.IS_LINK in mode || FileAttribute.LINK_TARGET in mode || FileAttribute.LINK_INODE in mode) {
                            if (capturedIsLink && (FileAttribute.LINK_TARGET in mode || FileAttribute.LINK_INODE in mode)) {
                                val readSymbolicLink = Files.readSymbolicLink(systemPath)
                                var targetExists = true
                                linkInode = try {
                                    Files.readAttributes(readSymbolicLink, "unix:ino")["ino"].toString()
                                } catch (ex: NoSuchFileException) {
                                    targetExists = false
                                    "0"
                                }

                                if (targetExists) {
                                    linkTarget = readSymbolicLink?.toFile()?.absolutePath?.toCloudPath()
                                } else {
                                    linkTarget = "/"
                                }
                            }
                        }
                    }
                }

                val realOwner = if (FileAttribute.OWNER in mode || FileAttribute.CREATOR in mode) {
                    val realPath = systemFile.path.toCloudPath()

                    log.debug("realPath is $realPath")

                    val components = realPath.components()
                    if (components.isEmpty()) {
                        SERVICE_USER
                    } else if (components.first() != "home") {
                        SERVICE_USER
                    } else if (components.size < 2) {
                        SERVICE_USER
                    } else {
                        // TODO This won't work for projects (?)
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
                        AccessEntry(
                            it.username, false, when (it.permission) {
                                AclPermission.READ -> setOf(AccessRight.READ, AccessRight.EXECUTE)
                                AclPermission.WRITE -> setOf(AccessRight.READ, AccessRight.WRITE, AccessRight.EXECUTE)
                            }
                        )
                    }
                }

                FileRow(
                    fileType,
                    isLink,
                    linkTarget,
                    unixMode,
                    realOwner,
                    "",
                    timestamps,
                    path,
                    rawPath,
                    inode,
                    size,
                    shares,
                    sensitivityLevel,
                    linkInode,
                    realOwner
                )
            } catch (ex: NoSuchFileException) {
                null
            }
        }
    }

    override suspend fun delete(ctx: LinuxFSRunner, path: String): FSResult<List<StorageEvent.Deleted>> =
        runAndRethrowNIOExceptions {
            aclService.requirePermission(path.parent(), ctx.user, AclPermission.WRITE)
            aclService.requirePermission(path, ctx.user, AclPermission.WRITE)

            val systemFile = File(translateAndCheckFile(path))
            val deletedRows = ArrayList<StorageEvent.Deleted>()
            val cache = HashMap<String, String>()



            if (!Files.exists(systemFile.toPath(), LinkOption.NOFOLLOW_LINKS)) throw FSException.NotFound()
            traverseAndDelete(ctx, systemFile.toPath(), cache, deletedRows)

            FSResult(0, deletedRows)
        }

    private suspend fun delete(
        ctx: LinuxFSRunner,
        path: Path,
        cache: HashMap<String, String>,
        deletedRows: ArrayList<StorageEvent.Deleted>
    ) {
        try {
            val stat = stat(ctx, path.toFile(), STORAGE_EVENT_MODE, cache, hasPerformedPermissionCheck = true)
            Files.delete(path)
            deletedRows.add(stat.toDeletedEvent(true))
        } catch (ex: NoSuchFileException) {
            log.debug("File at $path does not exists any more. Ignoring this error.")
        }
    }

    private suspend fun traverseAndDelete(
        ctx: LinuxFSRunner,
        path: Path,
        cache: HashMap<String, String>,
        deletedRows: ArrayList<StorageEvent.Deleted>
    ) {
        if (Files.isSymbolicLink(path)) {
            delete(ctx, path, cache, deletedRows)
            return
        }

        if (Files.isDirectory(path)) {
            path.toFile().listFiles()?.forEach {
                traverseAndDelete(ctx, it.toPath(), cache, deletedRows)
            }
        }

        delete(ctx, path, cache, deletedRows)
    }

    override suspend fun openForWriting(
        ctx: LinuxFSRunner,
        path: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> = runAndRethrowNIOExceptions {
        aclService.requirePermission(path, ctx.user, AclPermission.WRITE)

        if (ctx.outputStream == null) {
            val options = HashSet<OpenOption>()
            options.add(StandardOpenOption.TRUNCATE_EXISTING)
            options.add(StandardOpenOption.WRITE)
            if (!allowOverwrite) {
                options.add(StandardOpenOption.CREATE_NEW)
            } else {
                options.add(StandardOpenOption.CREATE)
            }

            val systemFile = File(translateAndCheckFile(path))
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
                        HashMap(),
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
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> = runAndRethrowNIOExceptions {
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
                stat(ctx, file, STORAGE_EVENT_MODE, HashMap(), hasPerformedPermissionCheck = false).toCreatedEvent(true)
            )
        )
    }

    override suspend fun tree(
        ctx: LinuxFSRunner,
        path: String,
        mode: Set<FileAttribute>
    ): FSResult<List<FileRow>> = runAndRethrowNIOExceptions {
        aclService.requirePermission(path, ctx.user, AclPermission.READ)

        val systemFile = File(translateAndCheckFile(path))
        val cache = HashMap<String, String>()
        FSResult(
            0,
            Files.walk(systemFile.toPath())
                .toList()
                .map {
                    stat(ctx, it.toFile(), mode, cache, hasPerformedPermissionCheck = true)
                }
        )
    }

    override suspend fun makeDirectory(
        ctx: LinuxFSRunner,
        path: String
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> = runAndRethrowNIOExceptions {
        aclService.requirePermission(path.parent(), ctx.user, AclPermission.WRITE)

        val systemFile = File(translateAndCheckFile(path))
        Files.createDirectory(systemFile.toPath(), PosixFilePermissions.asFileAttribute(DEFAULT_DIRECTORY_MODE))

        FSResult(
            0,
            listOf(
                stat(
                    ctx,
                    systemFile,
                    STORAGE_EVENT_MODE,
                    HashMap(),
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
    ): FSResult<String> = runAndRethrowNIOExceptions {
        // TODO Should this be owner only?
        aclService.requirePermission(path, ctx.user, AclPermission.READ)

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
    ): FSResult<Unit> = runAndRethrowNIOExceptions {
        // TODO Should this be owner only?
        aclService.requirePermission(path, ctx.user, AclPermission.WRITE)

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
        runAndRethrowNIOExceptions {
            // TODO Should this be owner only?
            aclService.requirePermission(path, ctx.user, AclPermission.READ)

            FSResult(
                0,
                StandardCLib.listxattr(translateAndCheckFile(path)).map { it.removePrefix("user.") }
            )
        }

    override suspend fun deleteExtendedAttribute(
        ctx: LinuxFSRunner,
        path: String,
        attribute: String
    ): FSResult<Unit> = runAndRethrowNIOExceptions {
        // TODO Should this be owner only?
        aclService.requirePermission(path, ctx.user, AclPermission.WRITE)

        FSResult(
            StandardCLib.removexattr(translateAndCheckFile(path), "user.$attribute"),
            Unit
        )
    }

    override suspend fun stat(ctx: LinuxFSRunner, path: String, mode: Set<FileAttribute>): FSResult<FileRow> =
        runAndRethrowNIOExceptions {
            aclService.requirePermission(path, ctx.user, AclPermission.READ)

            FSResult(
                0,
                stat(ctx, File(translateAndCheckFile(path)), mode, HashMap(), hasPerformedPermissionCheck = true)
            )
        }

    override suspend fun openForReading(ctx: LinuxFSRunner, path: String): FSResult<Unit> = runAndRethrowNIOExceptions {
        aclService.requirePermission(path, ctx.user, AclPermission.READ)

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
    ): R = runAndRethrowNIOExceptions {
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
            runBlocking {
                consumer(convertedToStream)
            }
        } finally {
            convertedToStream.close()
            ctx.inputSystemFile = null
            ctx.inputStream = null
        }
    }

    override suspend fun createSymbolicLink(
        ctx: LinuxFSRunner,
        targetPath: String,
        linkPath: String
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> = runAndRethrowNIOExceptions {
        aclService.requirePermission(targetPath.parent(), ctx.user, AclPermission.WRITE)

        val systemLink = File(translateAndCheckFile(linkPath))
        val systemTarget = File(translateAndCheckFile(targetPath))
        Files.createSymbolicLink(systemLink.toPath(), systemTarget.toPath())

        FSResult(
            0,
            listOf(
                stat(
                    ctx,
                    systemLink,
                    STORAGE_EVENT_MODE,
                    HashMap(),
                    followLink = false,
                    hasPerformedPermissionCheck = true
                ).toCreatedEvent(true)
            )
        )
    }

    override suspend fun createACLEntry(
        ctx: LinuxFSRunner,
        path: String,
        entity: FSACLEntity,
        rights: Set<AccessRight>
    ): FSResult<Unit> = runAndRethrowNIOExceptions {
        val hasRead = AccessRight.READ in rights
        val hasWrite = AccessRight.WRITE in rights
        if (!hasRead && !hasWrite) return FSResult(0, Unit)

        aclService.createOrUpdatePermission(
            path,
            entity.user,
            if (hasWrite) AclPermission.WRITE else AclPermission.READ
        )
        return FSResult(0, Unit)
    }

    override suspend fun removeACLEntry(
        ctx: LinuxFSRunner,
        path: String,
        entity: FSACLEntity
    ): FSResult<Unit> = runAndRethrowNIOExceptions {
        aclService.revokePermission(path, entity.user)
        return FSResult(0, Unit)
    }

    override suspend fun checkPermissions(ctx: LinuxFSRunner, path: String, requireWrite: Boolean): FSResult<Boolean> =
        runAndRethrowNIOExceptions {
            return FSResult(
                0,
                aclService.hasPermission(path, ctx.user, if (requireWrite) AclPermission.WRITE else AclPermission.READ)
            )
        }

    private fun String.toCloudPath(): String {
        return ("/" + substringAfter(fsRoot.absolutePath).removePrefix("/")).normalize()
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

fun translateAndCheckFile(fsRoot: File, internalPath: String, isDirectory: Boolean = false): String {
    val userRoot = File(fsRoot, "home").absolutePath.normalize().removeSuffix("/") + "/"
    val path = File(fsRoot, internalPath)
        .normalize()
        .absolutePath
        .let { it + (if (isDirectory) "/" else "") }

    if (!path.startsWith(userRoot) && path.removeSuffix("/") != userRoot.removeSuffix("/")) {
        throw FSException.BadRequest("path is not in user-root")
    }

    if (path.contains("\n")) throw FSException.BadRequest("Path cannot contain new-lines")
    if (path.length >= PATH_MAX) throw FSException.BadRequest("Path is too long")

    return path
}

