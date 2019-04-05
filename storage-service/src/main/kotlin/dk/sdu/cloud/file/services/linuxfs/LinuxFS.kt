package dk.sdu.cloud.file.services.linuxfs

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.api.AccessEntry
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.FileChecksum
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.Timestamps
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.services.FSACLEntity
import dk.sdu.cloud.file.services.FSResult
import dk.sdu.cloud.file.services.FileAttribute
import dk.sdu.cloud.file.services.FileRow
import dk.sdu.cloud.file.services.LowLevelFileSystemInterface
import dk.sdu.cloud.file.services.StorageUserDao
import dk.sdu.cloud.file.util.CappedInputStream
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import kotlin.streams.toList

class LinuxFS(
    private val fsRoot: File,
    private val userDao: StorageUserDao<Long>
) : LowLevelFileSystemInterface<LinuxFSRunner> {
    private var inputStream: FileChannel? = null
    private var inputSystemFile: File? = null

    private var outputStream: OutputStream? = null
    private var outputSystemFile: File? = null

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
                createdOrModifiedFromRow(
                    stat(ctx, systemTo, CREATED_OR_MODIFIED_ATTRIBUTES, HashMap()),
                    ctx.user
                )
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
        val fromStat = stat(ctx, systemFrom, setOf(FileAttribute.FILE_TYPE, FileAttribute.PATH), HashMap())

        Files.move(systemFrom.toPath(), systemTo.toPath(), *opts)

        // We compare this information with after the move to get the correct old path.
        val toStat = stat(ctx, systemTo, MOVED_ATTRIBUTES, HashMap())
        val basePath = toStat.path.removePrefix(toStat.path).removePrefix("/")
        val oldPath = if (fromStat.fileType == FileType.DIRECTORY) {
            joinPath(fromStat.path, basePath)
        } else {
            fromStat.path
        }

        fun movedFromRow(row: FileRow): StorageEvent.Moved {
            return StorageEvent.Moved(
                id = row.inode,
                path = row.path,
                owner = row.xowner,
                creator = row.owner,
                timestamp = System.currentTimeMillis(),
                oldPath = oldPath
            )
        }

        val rows = if (fromStat.fileType == FileType.DIRECTORY) {
            // We need to emit events for every single file below this root.
            val cache = HashMap<String, String>()
            Files.walk(systemTo.toPath()).map {
                movedFromRow(stat(ctx, it.toFile(), MOVED_ATTRIBUTES, cache))
            }.toList()
        } else {
            listOf(movedFromRow(toStat))
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
            (requestedDirectory.listFiles() ?: throw FSException.PermissionException()).map { child ->
                stat(ctx, child, mode, pathCache)
            }
        )
    }

    private fun stat(
        ctx: LinuxFSRunner,
        systemFile: File,
        mode: Set<FileAttribute>,
        pathCache: MutableMap<String, String>
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

        val systemPath = systemFile.toPath()

        run {
            // UNIX file attributes
            val attributes = run {
                val opts = ArrayList<String>()
                if (FileAttribute.INODE in mode) opts.add("ino")
                if (FileAttribute.OWNER in mode) opts.add("uid")
                if (FileAttribute.UNIX_MODE in mode) opts.add("mode")
                Files.readAttributes(systemPath, "unix:${opts.joinToString(",")}")
            }

            if (FileAttribute.INODE in mode) inode = (attributes.getValue("ino") as Long).toString()
            if (FileAttribute.UNIX_MODE in mode) unixMode = attributes["mode"] as Int
            if (FileAttribute.OWNER in mode) {
                runBlocking {
                    owner = userDao.findCloudUser((attributes.getValue("uid") as Int).toLong())
                }
            }
        }

        run {
            // Basic file attributes and symlinks
            val basicAttributes = run {
                val opts = ArrayList<String>()
                if (FileAttribute.TIMESTAMPS in mode) {
                    opts.addAll(listOf("lastAccessTime", "lastModifiedTime", "creationTime"))
                }

                if (FileAttribute.SIZE in mode) {
                    opts.add("size")
                }

                if (FileAttribute.FILE_TYPE in mode) {
                    opts.add("isDirectory")
                }

                if (FileAttribute.IS_LINK in mode || FileAttribute.LINK_TARGET in mode || FileAttribute.LINK_INODE in mode) {
                    opts.add("isSymbolicLink")
                }

                Files.readAttributes(
                    systemPath,
                    opts.joinToString(",")
                )
            }

            if (FileAttribute.RAW_PATH in mode) rawPath = systemFile.absolutePath.toCloudPath()
            if (FileAttribute.PATH in mode) {
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
            if (FileAttribute.SIZE in mode) size = basicAttributes.getValue("size") as Long

            if (FileAttribute.FILE_TYPE in mode) {
                fileType = if (basicAttributes.getValue("isDirectory") as Boolean) FileType.DIRECTORY else FileType.FILE
            }

            if (FileAttribute.TIMESTAMPS in mode) {
                val lastAccess = basicAttributes.getValue("lastAccessTime") as FileTime
                val lastModified = basicAttributes.getValue("lastModifiedTime") as FileTime
                val creationTime = basicAttributes.getValue("creationTime") as FileTime
                timestamps = Timestamps(
                    lastAccess.toMillis(),
                    creationTime.toMillis(),
                    lastModified.toMillis()
                )
            }

            if (FileAttribute.IS_LINK in mode || FileAttribute.LINK_TARGET in mode || FileAttribute.LINK_INODE in mode) {
                val linkStatus = basicAttributes["isSymbolicLink"] as Boolean
                isLink = linkStatus

                if (linkStatus && (FileAttribute.LINK_TARGET in mode || FileAttribute.LINK_INODE in mode)) {
                    val readSymbolicLink = Files.readSymbolicLink(systemPath)
                    linkTarget = readSymbolicLink?.toFile()?.absolutePath
                    linkInode = Files.readAttributes(readSymbolicLink, "unix:ino")["ino"] as String
                }
            }
        }

        // TODO Real owner
        // TODO Group

        if (FileAttribute.SENSITIVITY in mode) {
            sensitivityLevel =
                getExtendedAttributeInternal(ctx, systemFile, "sensitivity")?.let { SensitivityLevel.valueOf(it) }
        }

        if (FileAttribute.SHARES in mode) {
            shares = ACL.getEntries(systemFile.absolutePath).mapNotNull {
                if (!it.isUser) return@mapNotNull null
                val cloudUser = runBlocking { userDao.findCloudUser(it.id.toLong()) } ?: return@mapNotNull null
                val rights = HashSet<AccessRight>()
                if (it.execute) rights += AccessRight.EXECUTE
                if (it.read) rights += AccessRight.READ
                if (it.write) rights += AccessRight.WRITE

                AccessEntry(cloudUser, false, rights)
            }.toList()
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
            owner
        )
    }

    override suspend fun delete(ctx: LinuxFSRunner, path: String): FSResult<List<StorageEvent.Deleted>> = ctx.submit {
        ctx.requireContext()

        val systemFile = File(translateAndCheckFile(path))
        val deletedRows = ArrayList<StorageEvent.Deleted>()
        val cache = HashMap<String, String>()

        fun delete(path: Path) {
            try {
                val stat = stat(ctx, path.toFile(), DELETED_ATTRIBUTES, cache)
                Files.delete(path)
                deletedRows.add(
                    StorageEvent.Deleted(
                        stat.inode,
                        stat.path,
                        stat.owner,
                        System.currentTimeMillis(),
                        stat.xowner,
                        ctx.user
                    )
                )
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

        if (!Files.exists(systemFile.toPath())) throw FSException.NotFound()
        traverseAndDelete(systemFile.toPath())

        FSResult(0, deletedRows)
    }

    override suspend fun openForWriting(
        ctx: LinuxFSRunner,
        path: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> = ctx.submit {
        ctx.requireContext()

        if (outputStream == null) {
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
                outputStream = Channels.newOutputStream(
                    Files.newByteChannel(systemPath, options, PosixFilePermissions.asFileAttribute(DEFAULT_FILE_MODE))
                )
                outputSystemFile = systemFile
            } catch (ex: FileAlreadyExistsException) {
                throw FSException.AlreadyExists()
            }

            FSResult(
                0,
                listOf(
                    createdOrModifiedFromRow(
                        stat(ctx, systemFile, CREATED_OR_MODIFIED_ATTRIBUTES, HashMap()),
                        ctx.user
                    )
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

        val stream = outputStream
        val file = outputSystemFile
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
            outputStream = null
            outputSystemFile = null
        }

        FSResult(
            0,
            listOf(
                createdOrModifiedFromRow(
                    stat(ctx, file, CREATED_OR_MODIFIED_ATTRIBUTES, HashMap()),
                    ctx.user
                )
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
                createdOrModifiedFromRow(
                    stat(ctx, systemFile, CREATED_OR_MODIFIED_ATTRIBUTES, HashMap()),
                    ctx.user
                )
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
            StandardCLib.setxattr(translateAndCheckFile(path), "user.$attribute", value, allowOverwrite),
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

        if (inputStream != null) {
            log.warn("openForReading() called without closing last stream")
            throw FSException.CriticalException("Internal error")
        }

        val systemFile = File(translateAndCheckFile(path))
        inputStream = FileChannel.open(systemFile.toPath(), StandardOpenOption.READ)
        inputSystemFile = systemFile
        FSResult(0, Unit)
    }

    override suspend fun <R> read(
        ctx: LinuxFSRunner,
        range: LongRange?,
        consumer: suspend (InputStream) -> R
    ): R = ctx.submit {
        ctx.requireContext()
        val stream = inputStream
        val file = inputSystemFile
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
            inputSystemFile = null
            inputStream = null
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

        // TODO I have a feeling that we need to do something different for stat.
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun createACLEntry(
        ctx: LinuxFSRunner,
        path: String,
        entity: FSACLEntity,
        rights: Set<AccessRight>,
        defaultList: Boolean,
        recursive: Boolean
    ): FSResult<Unit> = ctx.submit {
        ctx.requireContext()

        if (entity !is FSACLEntity.User) throw FSException.BadRequest()
        val uid = runBlocking { userDao.findStorageUser(entity.user) } ?: throw FSException.BadRequest()


        Files.walk(File(translateAndCheckFile(path)).toPath()).forEach {
            ACL.addEntry(it.toFile().absolutePath, uid.toInt(), rights, defaultList)
        }
        FSResult(0, Unit)
    }

    override suspend fun removeACLEntry(
        ctx: LinuxFSRunner,
        path: String,
        entity: FSACLEntity,
        defaultList: Boolean,
        recursive: Boolean
    ): FSResult<Unit> = ctx.submit {
        ctx.requireContext()

        if (entity !is FSACLEntity.User) throw FSException.BadRequest()
        val uid = runBlocking { userDao.findStorageUser(entity.user) } ?: throw FSException.BadRequest()

        Files.walk(File(translateAndCheckFile(path)).toPath()).forEach {
            ACL.removeEntry(it.toFile().absolutePath, uid.toInt(), defaultList)
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
                createdOrModifiedFromRow(
                    stat(ctx, systemFile, CREATED_OR_MODIFIED_ATTRIBUTES, HashMap()),
                    ctx.user
                )
            )
        )
    }

    private fun translateAndCheckFile(path: String): String {
        val potentialResult = File(fsRoot, path)
        val normalizedResult = potentialResult.normalize().absolutePath
        if (!normalizedResult.startsWith(fsRoot.absolutePath + "/")) {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
        }
        return normalizedResult
    }

    private fun String.toCloudPath(): String {
        return ("/" + substringAfter(fsRoot.absolutePath).removePrefix("/")).normalize()
    }

    private fun createdOrModifiedFromRow(it: FileRow, eventCausedBy: String?): StorageEvent.CreatedOrRefreshed {
        return StorageEvent.CreatedOrRefreshed(
            id = it.inode,
            path = it.path,
            owner = it.xowner,
            creator = it.owner,
            timestamp = it.timestamps.modified,

            fileType = it.fileType,

            fileTimestamps = it.timestamps,
            size = it.size,

            isLink = it.isLink,
            linkTarget = if (it.isLink) it.linkTarget else null,
            linkTargetId = if (it.isLink) it.linkInode else null,

            sensitivityLevel = it.sensitivityLevel,

            eventCausedBy = eventCausedBy,

            annotations = emptySet(),
            checksum = FileChecksum("", "")
        )
    }

    companion object : Loggable {
        override val log = logger()
        const val PATH_MAX = 1024

        @Suppress("ObjectPropertyNaming")
        private val CREATED_OR_MODIFIED_ATTRIBUTES = setOf(
            FileAttribute.FILE_TYPE,
            FileAttribute.INODE,
            FileAttribute.PATH,
            FileAttribute.TIMESTAMPS,
            FileAttribute.OWNER,
            FileAttribute.XOWNER,
            FileAttribute.SIZE,
            FileAttribute.IS_LINK,
            FileAttribute.LINK_TARGET,
            FileAttribute.LINK_INODE,
            FileAttribute.SENSITIVITY
        )

        @Suppress("ObjectPropertyNaming")
        private val MOVED_ATTRIBUTES = setOf(
            FileAttribute.FILE_TYPE,
            FileAttribute.INODE,
            FileAttribute.PATH,
            FileAttribute.OWNER,
            FileAttribute.XOWNER
        )

        @Suppress("ObjectPropertyNaming")
        private val DELETED_ATTRIBUTES = setOf(
            FileAttribute.FILE_TYPE,
            FileAttribute.INODE,
            FileAttribute.OWNER,
            FileAttribute.XOWNER,
            FileAttribute.GROUP,
            FileAttribute.PATH
        )

        private val DEFAULT_FILE_MODE = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_WRITE
        )

        private val DEFAULT_DIRECTORY_MODE = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_WRITE,
            PosixFilePermission.GROUP_EXECUTE
        )
    }
}
