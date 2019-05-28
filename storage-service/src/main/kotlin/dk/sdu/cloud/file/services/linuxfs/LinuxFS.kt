package dk.sdu.cloud.file.services.linuxfs

import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.AccessEntry
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.Timestamps
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.services.FSACLEntity
import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.FSResult
import dk.sdu.cloud.file.services.FileAttribute
import dk.sdu.cloud.file.services.FileRow
import dk.sdu.cloud.file.services.LowLevelFileSystemInterface
import dk.sdu.cloud.file.services.StorageUserDao
import dk.sdu.cloud.file.services.XATTR_BIRTH
import dk.sdu.cloud.file.services.linuxfs.LinuxFS.Companion.PATH_MAX
import dk.sdu.cloud.file.util.CappedInputStream
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.file.util.STORAGE_EVENT_MODE
import dk.sdu.cloud.file.util.toCreatedEvent
import dk.sdu.cloud.file.util.toDeletedEvent
import dk.sdu.cloud.file.util.toMovedEvent
import dk.sdu.cloud.file.util.unwrap
import dk.sdu.cloud.service.Loggable
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
import kotlin.streams.toList
import kotlin.system.measureNanoTime

class LinuxFS(
    processRunner: FSCommandRunnerFactory<LinuxFSRunner>,
    fsRoot: File,
    private val userDao: StorageUserDao<Long>
) : LowLevelFileSystemInterface<LinuxFSRunner> {
    private val fsRoot = fsRoot.normalize().absoluteFile

    // Note: We should generally avoid these cyclic dependencies
    private val fileOwnerLookupService = FileOwnerLookupService(processRunner, this)

    override suspend fun copy(
        ctx: LinuxFSRunner,
        from: String,
        to: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> = ctx.submit {
        ctx.requireContext()

        val opts = if (allowOverwrite) arrayOf(StandardCopyOption.REPLACE_EXISTING) else emptyArray()
        val systemFrom = File(translateAndCheckFile(from))
        val systemTo = File(translateAndCheckFile(to))
        Files.copy(systemFrom.toPath(), systemTo.toPath(), *opts)
        FSResult(
            0,
            listOf(
                stat(ctx, systemTo, STORAGE_EVENT_MODE, HashMap()).toCreatedEvent(true)
            )
        )
    }

    override suspend fun move(
        ctx: LinuxFSRunner,
        from: String,
        to: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.Moved>> = ctx.submit {
        ctx.requireContext()

        val opts = if (allowOverwrite) arrayOf(StandardCopyOption.REPLACE_EXISTING) else emptyArray()
        val systemFrom = File(translateAndCheckFile(from))
        val systemTo = File(translateAndCheckFile(to))

        // We need to record some information from before the move
        val fromStat = stat(
            ctx,
            systemFrom,
            setOf(FileAttribute.FILE_TYPE, FileAttribute.PATH, FileAttribute.FILE_TYPE),
            HashMap()
        )

        val targetType =
            runCatching { stat(ctx, systemTo, setOf(FileAttribute.FILE_TYPE), HashMap()) }.getOrNull()?.fileType

        if (targetType != null && fromStat.fileType != targetType) {
            throw FSException.BadRequest("Target already exists and is not of same type as source.")
        }

        Files.move(systemFrom.toPath(), systemTo.toPath(), *opts)

        // We compare this information with after the move to get the correct old path.
        val toStat = stat(ctx, systemTo, STORAGE_EVENT_MODE, HashMap())
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
            Files.walk(systemTo.toPath()).map {
                stat(ctx, it.toFile(), STORAGE_EVENT_MODE, cache).toMovedEvent(oldPath, copyCausedBy = true)
            }.toList()
        } else {
            listOf(toStat.toMovedEvent(oldPath, copyCausedBy = true))
        }

        FSResult(0, rows)
    }

    override suspend fun listDirectory(
        ctx: LinuxFSRunner,
        directory: String,
        mode: Set<FileAttribute>
    ): FSResult<List<FileRow>> = ctx.submit {
        ctx.requireContext()

        val file = File(translateAndCheckFile(directory))
        val requestedDirectory = file.takeIf { it.exists() } ?: throw FSException.NotFound()

        val pathCache = HashMap<String, String>()

        FSResult(
            0,
            (requestedDirectory.listFiles() ?: throw FSException.PermissionException()).mapNotNull { child ->
                try {
                    stat(ctx, child, mode, pathCache)
                } catch (ex: NoSuchFileException) {
                    null
                }
            }
        )
    }

    private fun stat(
        ctx: LinuxFSRunner,
        systemFile: File,
        mode: Set<FileAttribute>,
        pathCache: MutableMap<String, String>,
        followLink: Boolean = false
    ): FileRow {
        ctx.requireContext()

        var fileType: FileType? = null
        var isLink: Boolean? = null
        var linkTarget: String? = null
        var unixMode: Int? = null
        var owner: String? = null
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
            if (FileAttribute.CREATOR in mode) {
                runBlocking {
                    owner = userDao.findCloudUser((attributes.getValue("uid") as Int).toLong())
                }
            }
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
                            getExtendedAttributeInternal(ctx, systemFile, XATTR_BIRTH)?.toLongOrNull()
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

        val realOwner = if (FileAttribute.OWNER in mode) {
            val realPath = path!!.toCloudPath()
            runBlocking { fileOwnerLookupService.lookupOwner(realPath) }
        } else {
            null
        }

        if (FileAttribute.SENSITIVITY in mode) {
            // Old setup would ignore errors. This is required for createLink to work
            sensitivityLevel =
                runCatching {
                    getExtendedAttributeInternal(ctx, systemFile, "sensitivity")?.let { SensitivityLevel.valueOf(it) }
                }.getOrNull()
        }

        if (FileAttribute.SHARES in mode) {
            var timer = 0L
            val time = measureNanoTime {
                shares = runBlocking {
                    ACL.getEntries(systemFile.absolutePath).mapNotNull {
                        val start = System.nanoTime()
                        if (!it.isUser) return@mapNotNull null
                        val cloudUser = userDao.findCloudUser(it.id.toLong()) ?: return@mapNotNull null

                        timer += System.nanoTime() - start
                        AccessEntry(cloudUser, false, it.rights)
                    }
                }
            }

            log.debug("It took $time/$timer ns to lookup ACL")
        }

        return FileRow(
            fileType,
            isLink,
            linkTarget,
            unixMode,
            owner,
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
    }

    override suspend fun delete(ctx: LinuxFSRunner, path: String): FSResult<List<StorageEvent.Deleted>> = ctx.submit {
        ctx.requireContext()

        val systemFile = File(translateAndCheckFile(path))
        val deletedRows = ArrayList<StorageEvent.Deleted>()
        val cache = HashMap<String, String>()

        fun delete(path: Path) {
            try {
                val stat = stat(ctx, path.toFile(), STORAGE_EVENT_MODE, cache)
                Files.delete(path)
                deletedRows.add(stat.toDeletedEvent(true))
            } catch (ex: NoSuchFileException) {
                log.debug("File at $path does not exists any more. Ignoring this error.")
            }
        }

        fun traverseAndDelete(path: Path) {
            if (Files.isSymbolicLink(path)) {
                delete(path)
                return
            }

            if (Files.isDirectory(path)) {
                Files.list(path).forEach {
                    traverseAndDelete(it)
                }
            }

            delete(path)
        }

        if (!Files.exists(systemFile.toPath(), LinkOption.NOFOLLOW_LINKS)) throw FSException.NotFound()
        traverseAndDelete(systemFile.toPath())

        FSResult(0, deletedRows)
    }

    override suspend fun openForWriting(
        ctx: LinuxFSRunner,
        path: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> = ctx.submit {
        ctx.requireContext()

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
                    stat(ctx, systemFile, STORAGE_EVENT_MODE, HashMap()).toCreatedEvent(true)
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
        ctx.requireContext()

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
                stat(ctx, file, STORAGE_EVENT_MODE, HashMap()).toCreatedEvent(true)
            )
        )
    }

    override suspend fun tree(
        ctx: LinuxFSRunner,
        path: String,
        mode: Set<FileAttribute>
    ): FSResult<List<FileRow>> = ctx.submit {
        ctx.requireContext()

        val systemFile = File(translateAndCheckFile(path))
        val cache = HashMap<String, String>()
        FSResult(
            0,
            Files.walk(systemFile.toPath())
                .map {
                    stat(ctx, it.toFile(), mode, cache)
                }
                .toList()
        )
    }

    override suspend fun makeDirectory(
        ctx: LinuxFSRunner,
        path: String
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> = ctx.submit {
        ctx.requireContext()

        val systemFile = File(translateAndCheckFile(path))
        Files.createDirectory(systemFile.toPath(), PosixFilePermissions.asFileAttribute(DEFAULT_DIRECTORY_MODE))

        FSResult(
            0,
            listOf(
                stat(ctx, systemFile, STORAGE_EVENT_MODE, HashMap()).toCreatedEvent(true)
            )
        )
    }

    private fun getExtendedAttributeInternal(
        ctx: LinuxFSRunner,
        systemFile: File,
        attribute: String
    ): String? {
        ctx.requireContext()

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
        ctx.requireContext()

        FSResult(
            0,
            getExtendedAttributeInternal(ctx, File(translateAndCheckFile(path)), attribute)
        )
    }

    override suspend fun setExtendedAttribute(
        ctx: LinuxFSRunner,
        path: String,
        attribute: String,
        value: String,
        allowOverwrite: Boolean
    ): FSResult<Unit> = ctx.submit {
        ctx.requireContext()

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

    override suspend fun listExtendedAttribute(ctx: LinuxFSRunner, path: String): FSResult<List<String>> = ctx.submit {
        ctx.requireContext()

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
        ctx.requireContext()

        FSResult(
            StandardCLib.removexattr(translateAndCheckFile(path), "user.$attribute"),
            Unit
        )
    }

    override suspend fun stat(ctx: LinuxFSRunner, path: String, mode: Set<FileAttribute>): FSResult<FileRow> =
        ctx.submit {
            ctx.requireContext()

            FSResult(
                0,
                stat(ctx, File(translateAndCheckFile(path)), mode, HashMap())
            )
        }

    override suspend fun openForReading(ctx: LinuxFSRunner, path: String): FSResult<Unit> = ctx.submit {
        ctx.requireContext()

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
        ctx.requireContext()
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
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> = ctx.submit {
        ctx.requireContext()

        val systemLink = File(translateAndCheckFile(linkPath))
        val systemTarget = File(translateAndCheckFile(targetPath))
        Files.createSymbolicLink(systemLink.toPath(), systemTarget.toPath())

        FSResult(
            0,
            listOf(
                stat(ctx, systemLink, STORAGE_EVENT_MODE, HashMap(), followLink = false).toCreatedEvent(true)
            )
        )
    }

    override suspend fun createACLEntry(
        ctx: LinuxFSRunner,
        path: String,
        entity: FSACLEntity,
        rights: Set<AccessRight>,
        defaultList: Boolean,
        recursive: Boolean,
        transferOwnershipTo: String?
    ): FSResult<Unit> = ctx.submit {
        ctx.requireContext()

        val backwardsCompatibleRights = rights + setOf(AccessRight.EXECUTE) // Execute was always being set previously

        if (entity !is FSACLEntity.User) throw FSException.BadRequest()
        val uid = runBlocking { userDao.findStorageUser(entity.user) } ?: throw FSException.BadRequest()

        val rootFile = File(translateAndCheckFile(path))
        if (!rootFile.isDirectory && defaultList) return@submit FSResult(0, Unit) // Files don't have a default list

        fun transferOwnership(path: Path) {
            if (transferOwnershipTo != null) {
                runBlocking {
                    internalChown(ctx, transferOwnershipTo, path).unwrap()
                }
            }
        }

        if (recursive) {
            Files.walk(rootFile.toPath()).forEach {
                transferOwnership(it)

                if (!Files.isDirectory(it) && defaultList) return@forEach // Files don't have a default list
                ACL.addEntry(it.toFile().absolutePath, uid.toInt(), backwardsCompatibleRights, defaultList)
            }
        } else {
            transferOwnership(rootFile.toPath())
            ACL.addEntry(rootFile.absolutePath, uid.toInt(), backwardsCompatibleRights, defaultList)
        }

        FSResult(0, Unit)
    }

    override suspend fun removeACLEntry(
        ctx: LinuxFSRunner,
        path: String,
        entity: FSACLEntity,
        defaultList: Boolean,
        recursive: Boolean,
        transferOwnershipTo: String?
    ): FSResult<Unit> = ctx.submit {
        ctx.requireContext()

        if (entity !is FSACLEntity.User) throw FSException.BadRequest()
        val uid = runBlocking { userDao.findStorageUser(entity.user) } ?: throw FSException.BadRequest()

        fun transferOwnership(path: Path) {
            if (transferOwnershipTo != null) {
                runBlocking {
                    internalChown(ctx, transferOwnershipTo, path).unwrap()
                }
            }
        }

        Files.walk(File(translateAndCheckFile(path)).toPath()).forEach {
            transferOwnership(it)

            if (!Files.isDirectory(it) && defaultList) return@forEach // Files don't have a default list
            val absPath = it.toFile().absolutePath
            log.debug("removing acl entry from $absPath")
            ACL.removeEntry(absPath, uid.toInt(), defaultList)
        }
        FSResult(0, Unit)
    }

    override suspend fun chmod(
        ctx: LinuxFSRunner,
        path: String,
        owner: Set<AccessRight>,
        group: Set<AccessRight>,
        other: Set<AccessRight>
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> = ctx.submit {
        ctx.requireContext()

        val opts = HashSet<PosixFilePermission>()
        if (AccessRight.READ in owner) opts.add(PosixFilePermission.OWNER_READ)
        if (AccessRight.WRITE in owner) opts.add(PosixFilePermission.OWNER_WRITE)
        if (AccessRight.EXECUTE in owner) opts.add(PosixFilePermission.OWNER_EXECUTE)

        if (AccessRight.READ in group) opts.add(PosixFilePermission.GROUP_READ)
        if (AccessRight.WRITE in group) opts.add(PosixFilePermission.GROUP_WRITE)
        if (AccessRight.EXECUTE in group) opts.add(PosixFilePermission.GROUP_EXECUTE)

        if (AccessRight.READ in other) opts.add(PosixFilePermission.OTHERS_READ)
        if (AccessRight.WRITE in other) opts.add(PosixFilePermission.OTHERS_WRITE)
        if (AccessRight.EXECUTE in other) opts.add(PosixFilePermission.OTHERS_EXECUTE)

        val systemFile = File(translateAndCheckFile(path))
        Files.setPosixFilePermissions(systemFile.toPath(), opts)

        FSResult(
            0,
            listOf(
                stat(ctx, systemFile, STORAGE_EVENT_MODE, HashMap()).toCreatedEvent(true)
            )
        )
    }

    override suspend fun chown(
        ctx: LinuxFSRunner,
        path: String,
        owner: String
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> {
        if (ctx.user != SERVICE_USER) throw FSException.PermissionException()

        return ctx.submit {
            ctx.requireContext()
            val systemPath = File(translateAndCheckFile(path)).toPath()
            internalChown(ctx, owner, systemPath)
        }
    }

    override suspend fun checkPermissions(ctx: LinuxFSRunner, path: String, requireWrite: Boolean): FSResult<Boolean> =
        ctx.submit {
            ctx.requireContext()

            val internalFile = File(translateAndCheckFile(path))
            val internalPath = internalFile.toPath()

            FSResult(
                0,
                (requireWrite && Files.isWritable(internalPath)) ||
                        (!requireWrite && Files.isReadable(internalPath))
            )
        }

    private fun internalChown(
        ctx: LinuxFSRunner,
        owner: String,
        systemPath: Path
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> {
        val uid = runBlocking {
            userDao.findStorageUser(owner)
        } ?: throw FSException.BadRequest()

        Chown.setOwner(systemPath, uid.toInt(), uid.toInt())

        return FSResult(
            0,
            listOf(
                stat(ctx, systemPath.toFile(), STORAGE_EVENT_MODE, HashMap()).toCreatedEvent(true)
            )
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

