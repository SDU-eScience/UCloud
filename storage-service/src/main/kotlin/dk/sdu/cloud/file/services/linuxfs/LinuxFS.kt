package dk.sdu.cloud.file.services.linuxfs

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.api.AccessEntry
import dk.sdu.cloud.file.api.AccessRight
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
import dk.sdu.cloud.file.util.FSException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.attribute.FileTime

class LinuxFS(
    private val fsRoot: File,
    private val userDao: StorageUserDao<Long>
) : LowLevelFileSystemInterface<LinuxFSRunner> {
    override suspend fun copy(
        ctx: LinuxFSRunner,
        from: String,
        to: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun move(
        ctx: LinuxFSRunner,
        from: String,
        to: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.Moved>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun listDirectory(
        ctx: LinuxFSRunner,
        directory: String,
        mode: Set<FileAttribute>
    ): FSResult<List<FileRow>> = ctx.submit {
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

        val systemPath = systemFile.toPath()
        val attributes = Files.readAttributes(systemPath, "unix:uid,gid,ino,mode")
        val basicAttributes =
            Files.readAttributes(
                systemPath,
                "lastAccessTime,lastModifiedTime,creationTime,size,isSymbolicLink,isDirectory"
            )

        rawPath = systemFile.absolutePath.toCloudPath()
        path = joinPath(realParent, systemFile.name).toCloudPath()
        size = basicAttributes.getValue("size") as Long
        inode = (attributes.getValue("ino") as Long).toString()

        unixMode = attributes["mode"] as Int
        fileType = if (basicAttributes.getValue("isDirectory") as Boolean) FileType.DIRECTORY else FileType.FILE
        val lastAccess = basicAttributes.getValue("lastAccessTime") as FileTime
        val lastModified = basicAttributes.getValue("lastModifiedTime") as FileTime
        val creationTime = basicAttributes.getValue("creationTime") as FileTime
        timestamps = Timestamps(
            lastAccess.toMillis(),
            creationTime.toMillis(),
            lastModified.toMillis()
        )

        runBlocking {
            owner = userDao.findCloudUser((attributes.getValue("uid") as Int).toLong())
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

        // TODO Real owner
        // TODO Group

        sensitivityLevel =
            getExtendedAttributeInternal(ctx, systemFile, "sensitivity")?.let { SensitivityLevel.valueOf(it) }

        shares = ACL.getEntries(systemFile.absolutePath).mapNotNull {
            if (!it.isUser) return@mapNotNull null
            val cloudUser = runBlocking { userDao.findCloudUser(it.id.toLong()) } ?: return@mapNotNull null
            val rights = HashSet<AccessRight>()
            if (it.execute) rights += AccessRight.EXECUTE
            if (it.read) rights += AccessRight.READ
            if (it.write) rights += AccessRight.WRITE

            AccessEntry(cloudUser, false, rights)
        }.toList()

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
            null
        )
    }

    override suspend fun delete(ctx: LinuxFSRunner, path: String): FSResult<List<StorageEvent.Deleted>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun openForWriting(
        ctx: LinuxFSRunner,
        path: String,
        allowOverwrite: Boolean
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun write(
        ctx: LinuxFSRunner,
        writer: suspend (OutputStream) -> Unit
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun tree(ctx: LinuxFSRunner, path: String, mode: Set<FileAttribute>): FSResult<List<FileRow>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun makeDirectory(
        ctx: LinuxFSRunner,
        path: String
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
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

    override suspend fun openForReading(ctx: LinuxFSRunner, path: String): FSResult<Unit> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun <R> read(ctx: LinuxFSRunner, range: IntRange?, consumer: suspend (InputStream) -> R): R {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun createSymbolicLink(
        ctx: LinuxFSRunner,
        targetPath: String,
        linkPath: String
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> {
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

        // TODO Handle recursive
        ACL.addEntry(translateAndCheckFile(path), uid.toInt(), rights, defaultList)
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

        // TODO Handle recursive
        ACL.removeEntry(translateAndCheckFile(path), uid.toInt(), defaultList)
        FSResult(0, Unit)
    }

    override suspend fun chmod(
        ctx: LinuxFSRunner,
        path: String,
        owner: Set<AccessRight>,
        group: Set<AccessRight>,
        other: Set<AccessRight>
    ): FSResult<List<StorageEvent.CreatedOrRefreshed>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
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

}
