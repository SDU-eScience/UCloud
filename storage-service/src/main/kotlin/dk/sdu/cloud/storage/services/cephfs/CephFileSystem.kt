package dk.sdu.cloud.storage.services.cephfs

import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.service.BashEscaper
import dk.sdu.cloud.storage.services.FSACLEntity
import dk.sdu.cloud.storage.services.FSResult
import dk.sdu.cloud.storage.services.FileAttribute
import dk.sdu.cloud.storage.services.FileRow
import dk.sdu.cloud.storage.services.LowLevelFileSystemInterface
import dk.sdu.cloud.storage.services.StorageUserDao
import dk.sdu.cloud.storage.services.asBitSet
import java.io.File
import java.io.InputStream
import java.io.OutputStream


private const val NOT_FOUND = -2

class CephFileSystem(
    private val userDao: StorageUserDao,
    private val fsRoot: String
) : LowLevelFileSystemInterface<CephFSCommandRunner> {
    override fun copy(
        ctx: CephFSCommandRunner,
        from: String,
        to: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> {
        val fromPath = translateAndCheckFile(from)
        val toPath = translateAndCheckFile(to)

        return ctx.runCommand(
            InterpreterCommand.COPY,
            fromPath,
            toPath,
            if (allowOverwrite) "1" else "0",

            consumer = { out ->
                parseFileAttributes(out.stdoutLineSequence(), CREATED_OR_MODIFIED_ATTRIBUTES)
                    .asFSResult { createdOrModifiedFromRow(it, ctx.user) }
            }
        )
    }

    override fun move(
        ctx: CephFSCommandRunner,
        from: String,
        to: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.Moved>> {
        val fromPath = translateAndCheckFile(from)
        val toPath = translateAndCheckFile(to)

        val fromStat = stat(ctx, from, setOf(FileAttribute.FILE_TYPE))
        if (fromStat.statusCode != 0) return FSResult(fromStat.statusCode)

        val timestamp = System.currentTimeMillis()

        return ctx.runCommand(
            InterpreterCommand.MOVE,
            fromPath,
            toPath,
            if (allowOverwrite) "1" else "0",

            consumer = { out ->
                val toList = out.stdoutLineSequence().toList()
                val sequence = toList.iterator()
                val realFrom = sequence.next()
                    .takeIf { !it.startsWith(EXIT) }
                    ?.toCloudPath() ?: return@runCommand FSResult(NOT_FOUND)

                val realTo = sequence.next().toCloudPath()

                parseFileAttributes(sequence, MOVED_ATTRIBUTES).asFSResult {
                    assert(it.path.startsWith(realTo))
                    val basePath = it.path.removePrefix(realTo).removePrefix("/")
                    val oldPath = if (fromStat.value.fileType == FileType.DIRECTORY) {
                        joinPath(realFrom, basePath)
                    } else {
                        realFrom
                    }

                    StorageEvent.Moved(it.inode, it.path, it.owner, timestamp, oldPath)
                }
            }
        )
    }

    override fun listDirectory(
        ctx: CephFSCommandRunner,
        directory: String,
        mode: Set<FileAttribute>
    ): FSResult<List<FileRow>> {
        return ctx.runCommand(
            InterpreterCommand.LIST_DIRECTORY,
            translateAndCheckFile(directory),
            mode.asBitSet().toString(),

            consumer = { parseFileAttributes(it.stdoutLineSequence(), mode).asFSResult() }
        )
    }

    override fun delete(ctx: CephFSCommandRunner, path: String): FSResult<List<StorageEvent.Deleted>> {
        val timestamp = System.currentTimeMillis()
        val absolutePath = translateAndCheckFile(path)

        return ctx.runCommand(
            InterpreterCommand.DELETE,
            absolutePath,
            consumer = { out ->
                parseFileAttributes(out.stdoutLineSequence(), DELETED_ATTRIBUTES).asFSResult {
                    StorageEvent.Deleted(
                        it.inode,
                        it.path,
                        it.owner,
                        timestamp,
                        ctx.user
                    )
                }
            }
        )
    }

    override fun openForWriting(
        ctx: CephFSCommandRunner,
        path: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> {
        val absolutePath = translateAndCheckFile(path)
        return ctx.runCommand(
            InterpreterCommand.WRITE_OPEN,
            absolutePath,
            if (allowOverwrite) "1" else "0",
            consumer = { out ->
                parseFileAttributes(
                    out.stdoutLineSequence(),
                    CREATED_OR_MODIFIED_ATTRIBUTES
                ).asFSResult { createdOrModifiedFromRow(it, ctx.user) }
            }
        )
    }

    override fun write(
        ctx: CephFSCommandRunner,
        writer: (OutputStream) -> Unit
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> {
        return ctx.runCommand(
            InterpreterCommand.WRITE,
            writer = { writer(it) },
            consumer = { out ->
                parseFileAttributes(
                    out.stdoutLineSequence(),
                    CREATED_OR_MODIFIED_ATTRIBUTES
                ).asFSResult { createdOrModifiedFromRow(it, ctx.user) }
            }
        )
    }

    override fun tree(ctx: CephFSCommandRunner, path: String, mode: Set<FileAttribute>): FSResult<List<FileRow>> {
        val absolutePath = translateAndCheckFile(path)
        return ctx.runCommand(
            InterpreterCommand.TREE,
            absolutePath,
            mode.asBitSet().toString(),
            consumer = { parseFileAttributes(it.stdoutLineSequence(), mode).asFSResult() }
        )
    }

    override fun makeDirectory(
        ctx: CephFSCommandRunner,
        path: String
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> {
        val absolutePath = translateAndCheckFile(path)
        return ctx.runCommand(
            InterpreterCommand.MKDIR,
            absolutePath,
            consumer = { out ->
                parseFileAttributes(
                    out.stdoutLineSequence(),
                    CREATED_OR_MODIFIED_ATTRIBUTES
                ).asFSResult { createdOrModifiedFromRow(it, ctx.user) }
            }
        )
    }

    override fun getExtendedAttribute(ctx: CephFSCommandRunner, path: String, attribute: String): FSResult<String> {
        val absolutePath = translateAndCheckFile(path)
        return ctx.runCommand(
            InterpreterCommand.GET_XATTR,
            absolutePath,
            attribute,

            consumer = {
                var value: String? = null
                var statusCode: Int? = null

                for (line in it.stdoutLineSequence()) {
                    if (line.startsWith(EXIT)) {
                        statusCode = line.split(":")[1].toInt()
                    } else {
                        value = line
                    }
                }

                FSResult(statusCode!!, value)
            }
        )
    }

    override fun setExtendedAttribute(
        ctx: CephFSCommandRunner,
        path: String,
        attribute: String,
        value: String
    ): FSResult<Unit> {
        val absolutePath = translateAndCheckFile(path)
        return ctx.runCommand(
            InterpreterCommand.SET_XATTR,
            absolutePath,
            attribute.removePrefix(ATTRIBUTE_PREFIX).let { "$ATTRIBUTE_PREFIX$it" },
            value,
            consumer = this::consumeStatusCode
        )
    }

    override fun listExtendedAttribute(ctx: CephFSCommandRunner, path: String): FSResult<List<String>> {
        val absolutePath = translateAndCheckFile(path)
        return ctx.runCommand(
            InterpreterCommand.LIST_XATTR,
            absolutePath,

            consumer = { out ->
                out.stdoutLineSequence().map {
                    if (it.startsWith(EXIT)) StatusTerminatedItem.Exit<String>(it.split(":")[1].toInt())
                    else StatusTerminatedItem.Item(it)
                }.asFSResult()
            }
        )
    }

    override fun deleteExtendedAttribute(ctx: CephFSCommandRunner, path: String, attribute: String): FSResult<Unit> {
        val absolutePath = translateAndCheckFile(path)
        return ctx.runCommand(
            InterpreterCommand.DELETE_XATTR,
            absolutePath,
            consumer = this::consumeStatusCode
        )
    }

    override fun stat(ctx: CephFSCommandRunner, path: String, mode: Set<FileAttribute>): FSResult<FileRow> {
        val absolutePath = translateAndCheckFile(path)
        return ctx.runCommand(
            InterpreterCommand.STAT,
            absolutePath,
            mode.asBitSet().toString(),
            consumer = { out ->
                parseFileAttributes(out.stdoutLineSequence(), mode).asFSResult().let {
                    FSResult(it.statusCode, it.value.singleOrNull())
                }
            }
        )
    }

    override fun openForReading(ctx: CephFSCommandRunner, path: String): FSResult<Unit> {
        val absolutePath = translateAndCheckFile(path)
        return ctx.runCommand(
            InterpreterCommand.READ_OPEN,
            absolutePath,
            consumer = this::consumeStatusCode
        )
    }

    override fun <R> read(ctx: CephFSCommandRunner, range: IntRange?, consumer: (InputStream) -> R): R {
        val start = range?.start ?: -1
        val end = range?.endInclusive ?: -1

        return ctx.runCommand(
            InterpreterCommand.READ,
            start.toString(),
            end.toString(),
            consumer = {
                it.clearBytes(it.stdout.readLineUnbuffered().toLong())
                return@runCommand consumer(it.stdout)
            }
        )
    }

    override fun createSymbolicLink(
        ctx: CephFSCommandRunner,
        targetPath: String,
        linkPath: String
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> {
        val absTargetPath = translateAndCheckFile(targetPath)
        val absLinkPath = translateAndCheckFile(linkPath)

        return ctx.runCommand(
            InterpreterCommand.SYMLINK,
            absTargetPath,
            absLinkPath,
            consumer = { out ->
                parseFileAttributes(
                    out.stdoutLineSequence(),
                    CREATED_OR_MODIFIED_ATTRIBUTES
                ).asFSResult { createdOrModifiedFromRow(it, ctx.user) }
            }
        )
    }

    override fun createACLEntry(
        ctx: CephFSCommandRunner,
        path: String,
        entity: FSACLEntity,
        rights: Set<AccessRight>,
        defaultList: Boolean,
        recursive: Boolean
    ): FSResult<Unit> {
        val absolutePath = translateAndCheckFile(path)
        println(absolutePath)

        val unixEntity = entity.toUnixEntity()
        if (unixEntity.statusCode != 0) return FSResult(unixEntity.statusCode)

        val command = ArrayList<String>().apply {
            if (defaultList) add("-d")
            if (recursive) add("-R")

            add("-m")
            val permissions: String = run {
                val read = if (AccessRight.READ in rights) "r" else "-"
                val write = if (AccessRight.WRITE in rights) "w" else "-"
                val execute = if (AccessRight.EXECUTE in rights) "x" else "X" // Note: execute is implicit for dirs

                read + write + execute
            }

            add(BashEscaper.safeBashArgument("${unixEntity.value.serializedEntity}:$permissions"))

            add(BashEscaper.safeBashArgument(absolutePath))
        }.joinToString(" ")

        return ctx.runCommand(
            InterpreterCommand.SETFACL,
            command,
            consumer = this::consumeStatusCode
        )
    }

    override fun removeACLEntry(
        ctx: CephFSCommandRunner,
        path: String,
        entity: FSACLEntity,
        defaultList: Boolean,
        recursive: Boolean
    ): FSResult<Unit> {
        val absolutePath = translateAndCheckFile(path)

        val unixEntity = entity.toUnixEntity()
        if (unixEntity.statusCode != 0) return FSResult(unixEntity.statusCode)

        val command = ArrayList<String>().apply {
            if (defaultList) add("-d")
            if (recursive) add("-R")
            add("-x")
            add(BashEscaper.safeBashArgument(unixEntity.value.serializedEntity))
            add(BashEscaper.safeBashArgument(absolutePath))
        }.joinToString(" ")

        return ctx.runCommand(
            InterpreterCommand.SETFACL,
            command,
            consumer = this::consumeStatusCode
        )
    }

    override fun chmod(
        ctx: CephFSCommandRunner,
        path: String,
        owner: Set<AccessRight>,
        group: Set<AccessRight>,
        other: Set<AccessRight>
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> {
        val absolutePath = translateAndCheckFile(path)
        fun Set<AccessRight>.toBitSet(): Int {
            var result = 0
            if (AccessRight.EXECUTE in this) result = result or 1
            if (AccessRight.WRITE in this) result = result or 2
            if (AccessRight.READ in this) result = result or 4
            return result
        }

        val mode = (owner.toBitSet() shl 6) or (group.toBitSet() shl 3) or (other.toBitSet())
        return ctx.runCommand(
            InterpreterCommand.CHMOD,
            absolutePath,
            mode.toString(),
            consumer = { out ->
                parseFileAttributes(
                    out.stdoutLineSequence(),
                    CREATED_OR_MODIFIED_ATTRIBUTES
                ).asFSResult { createdOrModifiedFromRow(it, ctx.user) }
            }
        )
    }

    private fun FSACLEntity.toUnixEntity(): FSResult<FSACLEntity> {
        val entity = this
        return when (entity) {
            is FSACLEntity.User -> {
                val user = userDao.findStorageUser(entity.user) ?: return FSResult(NOT_FOUND)
                FSResult(0, FSACLEntity.User(user))
            }

            else -> FSResult(0, entity)
        }
    }

    private fun consumeStatusCode(it: CephFSCommandRunner): FSResult<Unit> {
        var statusCode: Int? = null
        val stdoutLineSequence = it.stdoutLineSequence().toList()
        for (line in stdoutLineSequence) {
            if (line.startsWith(EXIT)) {
                statusCode = line.split(":")[1].toInt()
            }
        }

        return FSResult(statusCode!!, Unit)
    }

    private fun createdOrModifiedFromRow(it: FileRow, eventCausedBy: String?): StorageEvent.CreatedOrRefreshed {
        return StorageEvent.CreatedOrRefreshed(
            id = it.inode,
            path = it.path,
            owner = it.owner,
            timestamp = it.timestamps.modified,

            fileType = it.fileType,

            fileTimestamps = it.timestamps,
            size = it.size,
            checksum = it.checksum,

            isLink = it.isLink,
            linkTarget = if (it.isLink) it.linkTarget else null,
            linkTargetId = if (it.isLink) it.linkInode else null,

            annotations = it.annotations,

            sensitivityLevel = it.sensitivityLevel,

            eventCausedBy = eventCausedBy
        )
    }

    //
    // File utility
    //

    private fun translateAndCheckFile(internalPath: String, isDirectory: Boolean = false): String {
        val userRoot = File(fsRoot, "home").absolutePath.removeSuffix("/") + "/"
        val path = File(fsRoot, internalPath)
            .normalize()
            .absolutePath
            .let { it + (if (isDirectory) "/" else "") }

        if (!path.startsWith(userRoot) && path.removeSuffix("/") != userRoot.removeSuffix("/")) throw IllegalArgumentException(
            "path is not in user-root"
        )
        if (path.contains("\n")) throw IllegalArgumentException("Path cannot contain new-lines")
        if (path.length >= PATH_MAX) throw IllegalArgumentException("Path is too long")

        return path
    }

    private fun String.toCloudPath(): String {
        return ("/" + substringAfter(fsRoot).removePrefix("/")).normalize()
    }

    private fun parseFileAttributes(
        sequence: Sequence<String>,
        attributes: Set<FileAttribute>
    ): Sequence<StatusTerminatedItem<FileRow>> {
        return parseFileAttributes(sequence.iterator(), attributes)
    }

    private fun parseFileAttributes(
        iterator: Iterator<String>,
        attributes: Set<FileAttribute>
    ): Sequence<StatusTerminatedItem<FileRow>> {
        return FileAttribute.rawParse(iterator, attributes).map { item ->
            when (item) {
                is StatusTerminatedItem.Item -> item.copy(
                    item = item.item.convertToCloud(
                        usernameConverter = { userDao.findCloudUser(it)!! },
                        pathConverter = { it.toCloudPath() }
                    )
                )

                else -> item
            }
        }
    }

    private inline fun <T, R> Sequence<StatusTerminatedItem<T>>.asFSResult(mapper: (T) -> R): FSResult<List<R>> {
        val rows = toList()
        val exit = rows.last() as StatusTerminatedItem.Exit
        return FSResult(
            exit.statusCode,
            rows.subList(0, rows.size - 1).map { mapper((it as StatusTerminatedItem.Item).item) }
        )
    }

    private fun <T> Sequence<StatusTerminatedItem<T>>.asFSResult(): FSResult<List<T>> {
        val rows = toList()
        val exit = rows.last() as StatusTerminatedItem.Exit
        return FSResult(
            exit.statusCode,
            rows.subList(0, rows.size - 1).map { (it as StatusTerminatedItem.Item).item }
        )
    }

    companion object {
        const val PATH_MAX = 1024

        private const val EXIT = "EXIT:"
        private const val ATTRIBUTE_PREFIX = "user."

        @Suppress("ObjectPropertyNaming")
        private val CREATED_OR_MODIFIED_ATTRIBUTES = setOf(
            FileAttribute.FILE_TYPE,
            FileAttribute.INODE,
            FileAttribute.PATH,
            FileAttribute.TIMESTAMPS,
            FileAttribute.OWNER,
            FileAttribute.SIZE,
            FileAttribute.CHECKSUM,
            FileAttribute.IS_LINK,
            FileAttribute.LINK_TARGET,
            FileAttribute.LINK_INODE,
            FileAttribute.ANNOTATIONS,
            FileAttribute.SENSITIVITY
        )

        @Suppress("ObjectPropertyNaming")
        private val MOVED_ATTRIBUTES = setOf(
            FileAttribute.FILE_TYPE,
            FileAttribute.INODE,
            FileAttribute.PATH,
            FileAttribute.OWNER
        )

        @Suppress("ObjectPropertyNaming")
        private val DELETED_ATTRIBUTES = setOf(
            FileAttribute.FILE_TYPE,
            FileAttribute.INODE,
            FileAttribute.OWNER,
            FileAttribute.GROUP,
            FileAttribute.PATH
        )
    }
}
