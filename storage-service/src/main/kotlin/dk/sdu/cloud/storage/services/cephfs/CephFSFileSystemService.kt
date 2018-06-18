package dk.sdu.cloud.storage.services.cephfs

import dk.sdu.cloud.storage.api.*
import dk.sdu.cloud.storage.services.FSUserContext
import dk.sdu.cloud.storage.services.FSException
import dk.sdu.cloud.storage.services.FileSystemService
import dk.sdu.cloud.storage.services.ShareException
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.*

data class FavoritedFile(val type: FileType, val from: String, val to: String, val inode: String)

class CephFSFileSystemService(
    private val cloudToCephFsDao: CloudToCephFsDao,
    private val processRunner: ProcessRunnerFactory,
    private val fileACLService: FileACLService,
    private val copyService: CopyService,
    private val fsRoot: String,
    private val isDevelopment: Boolean = false,
    private val eventProducer: StorageEventProducer
) : FileSystemService {
    override fun openContext(user: String): FSUserContext {
        return processRunner(user)
    }

    private fun readStorageFile(row: FileRow, favorites: Set<String>): StorageFile =
        StorageFile(
            type = row.fileType!!,
            path = row.path!!,
            createdAt = row.timestamps!!.created,
            modifiedAt = row.timestamps.modified,
            ownerName = row.owner!!,
            size = row.size!!,
            acl = row.shares!!,
            favorited = row.inode in favorites,
            sensitivityLevel = row.sensitivityLevel!!,
            link = row.isLink!!,
            annotations = row.annotations!!,
            inode = row.inode!!
        )

    override fun ls(
        ctx: FSUserContext,
        path: String,
        includeImplicit: Boolean,
        includeFavorites: Boolean
    ): List<StorageFile> {
        val favorites = retrieveFavoriteInodeSet(ctx)
        val absolutePath = translateAndCheckFile(path)

        return ctx.runCommand(
            InterpreterCommand.LIST_DIRECTORY,
            absolutePath,
            STORAGE_FILE_ATTRIBUTES.asBitSet().toString(),

            consumer = {
                parseFileAttributes(it.stdoutLineSequence(), STORAGE_FILE_ATTRIBUTES).map {
                    readStorageFile(it, favorites)
                }.toList()
            }
        )
    }

    private fun retrieveFavoriteInodeSet(ctx: FSUserContext): Set<String> =
        retrieveFavorites(ctx).map { it.inode }.toSet()

    override fun retrieveFavorites(
        ctx: FSUserContext
    ): List<FavoritedFile> {
        val dir = translateAndCheckFile(favoritesDirectory(ctx), true)

        val attributes = setOf(
            FileAttribute.FILE_TYPE,
            FileAttribute.PATH,
            FileAttribute.LINK_TARGET,
            FileAttribute.LINK_INODE
        )

        return ctx.runCommand(
            InterpreterCommand.LIST_DIRECTORY,
            dir,
            attributes.asBitSet().toString(),

            consumer = {
                parseFileAttributes(it.stdoutLineSequence(), attributes).map {
                    FavoritedFile(
                        type = it.fileType!!,
                        from = it.path!!,
                        to = it.linkTarget!!,
                        inode = it.linkInode!!
                    )
                }.toList()
            }
        )
    }

    override fun stat(ctx: FSUserContext, path: String): StorageFile? {
        val favorites = retrieveFavoriteInodeSet(ctx)
        val mountedPath = translateAndCheckFile(path)

        // TODO Catch not found exception
        return ctx.runCommand(
            InterpreterCommand.STAT,
            mountedPath,
            STORAGE_FILE_ATTRIBUTES.asBitSet().toString(),

            consumer = {
                parseFileAttributes(it.stdoutLineSequence(), STORAGE_FILE_ATTRIBUTES).map {
                    readStorageFile(it, favorites)
                }.singleOrNull()
            }
        )
    }

    override fun mkdir(ctx: FSUserContext, path: String) {
        val absolutePath = translateAndCheckFile(path)

        ctx.runCommand(
            InterpreterCommand.MKDIR,
            absolutePath,
            consumer = {
                parseFileAttributes(it.stdoutLineSequence(), CREATED_OR_MODIFIED_ATTRIBUTES).forEach {
                    launch { eventProducer.emit(createdOrModifiedFromRow(it)) }
                }
            }
        )
    }

    private fun createdOrModifiedFromRow(it: FileRow): StorageEvent.CreatedOrModified {
        return StorageEvent.CreatedOrModified(
            id = it.inode!!,
            path = it.path!!,
            owner = it.owner!!,
            type = it.fileType!!,
            timestamp = it.timestamps!!.modified
        )
    }

    override fun rmdir(ctx: FSUserContext, path: String) {
        val timestamp = System.currentTimeMillis()
        val absolutePath = translateAndCheckFile(path)
        val attributes = setOf(
            FileAttribute.FILE_TYPE,
            FileAttribute.INODE,
            FileAttribute.OWNER,
            FileAttribute.PATH
        )

        ctx.runCommand(
            InterpreterCommand.DELETE,
            absolutePath,
            consumer = {
                parseFileAttributes(it.stdoutLineSequence(), attributes).forEach {
                    launch {
                        eventProducer.emit(
                            StorageEvent.Deleted(
                                id = it.inode!!,
                                path = it.path!!,
                                owner = it.owner!!,
                                timestamp = timestamp
                            )
                        )
                    }
                }
            }
        )
    }

    override fun move(ctx: FSUserContext, path: String, newPath: String) {
        val timestamp = System.currentTimeMillis()
        val absolutePath = translateAndCheckFile(path)
        val newAbsolutePath = translateAndCheckFile(newPath)
        if (absolutePath == newAbsolutePath) throw FSException.AlreadyExists(newPath)

        val stat = stat(ctx, newAbsolutePath)
        if (stat != null && stat.type != FileType.DIRECTORY) {
            throw FSException.AlreadyExists(newPath)
        }

        val attributes = setOf(
            FileAttribute.FILE_TYPE,
            FileAttribute.INODE,
            FileAttribute.PATH
        )

        ctx.runCommand(
            InterpreterCommand.MOVE,
            absolutePath,
            newAbsolutePath,

            consumer = {
                parseFileAttributes(it.stdoutLineSequence(), attributes).forEach {
                    val oldPath =
                        if (stat?.type == FileType.DIRECTORY) it.path!!.removePrefix(newPath).removePrefix("/").let {
                            File(path, it).normalize().path
                        }
                        else path.normalize()

                    launch {
                        eventProducer.emit(
                            StorageEvent.Moved(
                                id = it.inode!!,
                                path = it.path!!,
                                owner = it.owner!!,
                                timestamp = timestamp,
                                oldPath = oldPath
                            )
                        )
                    }
                }
            }
        )
    }

    override fun copy(ctx: FSUserContext, path: String, newPath: String) {
        val absolutePath = translateAndCheckFile(path)
        val newAbsolutePath = translateAndCheckFile(newPath)

        val copiedItems =
            copyService.copy(ctx, absolutePath, newAbsolutePath).map { it.copy(path = it.path.toCloudPath()) }

        if (copiedItems.isEmpty()) {
            throw FSException.PermissionException()
        }

        launch { copiedItems.forEach { eventProducer.emit(it) } }
    }

    override suspend fun <T> coRead(ctx: FSUserContext, path: String, consumer: suspend InputStream.() -> T): T {
        return read(ctx, path) {
            runBlocking { consumer() }
        }
    }

    override fun <T> read(ctx: FSUserContext, path: String, consumer: InputStream.() -> T): T {
        val absolutePath = translateAndCheckFile(path)
        return ctx.runCommand(
            InterpreterCommand.READ,
            absolutePath,

            consumer = {
                it.clearBytes(it.stdout.simpleReadLine().toLong())
                it.stdout.consumer()
            }
        )
    }

    override suspend fun <T> coWrite(ctx: FSUserContext, path: String, writer: suspend OutputStream.() -> T): T {
        return write(ctx, path) {
            runBlocking { writer() }
        }
    }

    override fun <T> write(ctx: FSUserContext, path: String, writer: OutputStream.() -> T): T {
        val absolutePath = translateAndCheckFile(path)

        ctx.runCommand(
            InterpreterCommand.WRITE_OPEN,
            absolutePath,

            consumer = {
                val row = parseFileAttributes(it.stdoutLineSequence(), CREATED_OR_MODIFIED_ATTRIBUTES).toList().single()
                launch { eventProducer.emit(createdOrModifiedFromRow(row)) }
            }
        )

        var result: T? = null
        ctx.runCommand(
            InterpreterCommand.WRITE,
            writer = { result = writer(it) },
            consumer = {}
        )

        return result!!
    }

    override fun createSoftSymbolicLink(ctx: FSUserContext, linkFile: String, pointsTo: String) {
        val absLinkPath = translateAndCheckFile(linkFile)
        val absPointsToPath = translateAndCheckFile(pointsTo)

        // We only need to check target, the rest will be enforced. Ideally we wouldn't do this as two forks,
        // but can work for prototypes. TODO Performance

        // TODO Stat needs to not ls parent dir. Disabled for now
        // TODO Stat needs to not ls parent dir. Disabled for now
        // TODO Stat needs to not ls parent dir. Disabled for now
        // TODO Stat needs to not ls parent dir. Disabled for now
        if (false && stat(ctx, pointsTo) == null) {
            throw IllegalArgumentException("Cannot point to target ($linkFile, $pointsTo)")
        }

        val process = ctx.run(listOf("ln", "-s", absPointsToPath, absLinkPath))
        val status = process.waitFor()
        if (status != 0) {
            log.info("ln failed ${ctx.user}, $absLinkPath $absPointsToPath")
            log.info(process.errorStream.reader().readText())
            throw IllegalStateException()
        } else {
            launch {
                val fileStat = stat(ctx, linkFile) ?: return@launch run {
                    log.warn("Unable to stat file after soft link. path=$linkFile")
                }

                eventProducer.emit(
                    StorageEvent.CreatedOrModified(
                        id = fileStat.inode.toString(),
                        path = fileStat.path,
                        owner = fileStat.ownerName,
                        timestamp = fileStat.createdAt,
                        type = FileType.LINK
                    )
                )
            }
        }
    }

    override fun createFavorite(ctx: FSUserContext, fileToFavorite: String) {
        // TODO Create retrieveFavorites folder if it does not exist yet
        val targetLocation =
            findFreeNameForNewFile(ctx, joinPath(favoritesDirectory(ctx), fileToFavorite.fileName()))

        createSoftSymbolicLink(ctx, targetLocation, fileToFavorite)
    }

    override fun removeFavorite(ctx: FSUserContext, favoriteFileToRemove: String) {
        val stat = stat(ctx, favoriteFileToRemove) ?: throw IllegalStateException()
        val allFavorites = retrieveFavorites(ctx)
        val toRemove = allFavorites.filter { it.inode == stat.inode }
        if (toRemove.isEmpty()) return
        val command = listOf("rm") + toRemove.map { it.from }
        val process = ctx.run(command)
        val status = process.waitFor()
        if (status != 0) {
            log.info("rm failed ${ctx.user}")
            log.info(process.errorStream.reader().readText())
            throw IllegalStateException()
        }
    }

    override fun grantRights(ctx: FSUserContext, toUser: String, path: String, rights: Set<AccessRight>) {
        val parents: List<String> = run {
            if (path == "/") throw ShareException.BadRequest("Cannot grant rights on root")
            val parents = path.parents()

            parents.filter { it != "/" && it != "/home/" }
        }.map { translateAndCheckFile(it) }

        // Execute rights are required on all parent directories (otherwise we cannot perform the
        // traversal to the share)
        parents.forEach { fileACLService.createEntry(ctx, toUser, it, setOf(AccessRight.EXECUTE)) }

        // Add to both the default and the actual list. This needs to be recursively applied
        val mountedPath = translateAndCheckFile(path)
        fileACLService.createEntry(ctx, toUser, mountedPath, rights, defaultList = true, recursive = true)
        fileACLService.createEntry(ctx, toUser, mountedPath, rights, defaultList = false, recursive = true)
    }

    override fun revokeRights(ctx: FSUserContext, toUser: String, path: String) {
        // TODO Need to look up share API to determine if we should delete execute rights on parent dirs
        val mountedPath = translateAndCheckFile(path)
        fileACLService.removeEntry(ctx, toUser, mountedPath, defaultList = true, recursive = true)
        fileACLService.removeEntry(ctx, toUser, mountedPath, defaultList = false, recursive = true)
    }

    private val duplicateNamingRegex = Regex("""\((\d+)\)""")
    override fun findFreeNameForNewFile(ctx: FSUserContext, desiredPath: String): String {
        fun findFileNameNoExtension(fileName: String): String {
            return fileName.substringBefore('.')
        }

        fun findExtension(fileName: String): String {
            if (!fileName.contains(".")) return ""
            return '.' + fileName.substringAfter('.', missingDelimiterValue = "")
        }

        val fileName = desiredPath.fileName()
        val desiredWithoutExtension = findFileNameNoExtension(fileName)
        val extension = findExtension(fileName)

        val parentPath = desiredPath.substringBeforeLast('/')
        val names = ls(ctx, parentPath).map { it.path.fileName() }

        return if (names.isEmpty()) {
            desiredPath
        } else {
            val namesMappedAsIndices = names.mapNotNull {
                val nameWithoutExtension = findFileNameNoExtension(it)
                val nameWithoutPrefix = nameWithoutExtension.substringAfter(desiredWithoutExtension)
                val myExtension = findExtension(it)

                if (extension != myExtension) return@mapNotNull null

                if (nameWithoutPrefix.isEmpty()) {
                    0 // We have an exact match on the file name
                } else {
                    val match = duplicateNamingRegex.matchEntire(nameWithoutPrefix)
                    if (match == null) {
                        null // The file name doesn't match at all, i.e., the file doesn't collide with our desired name
                    } else {
                        match.groupValues.getOrNull(1)?.toIntOrNull()
                    }
                }
            }

            if (namesMappedAsIndices.isEmpty()) {
                desiredPath
            } else {
                val currentMax = namesMappedAsIndices.max() ?: 0
                "$parentPath/$desiredWithoutExtension(${currentMax + 1})$extension"
            }
        }
    }

    override fun homeDirectory(ctx: FSUserContext): String {
        return "/home/${ctx.user}/"
    }

    private fun checkStatus(line: String): Boolean {
        if (line.startsWith("EXIT:")) {
            val status = line.split(":")[1].toInt()
            if (status != 0) throwExceptionBasedOnStatus(status)
            return true
        }
        return false
    }

    override fun listMetadataKeys(ctx: FSUserContext, path: String): List<String> {
        val absolutePath = translateAndCheckFile(path)
        return ctx.runCommand(
            InterpreterCommand.LIST_XATTR,
            absolutePath,

            consumer = {
                it.stdoutLineSequence().mapNotNull {
                    if (checkStatus(it)) return@mapNotNull null
                    it
                }.toList()
            }
        )
    }

    override fun getMetaValue(ctx: FSUserContext, path: String, key: String): String {
        val absolutePath = translateAndCheckFile(path)
        return ctx.runCommand(
            InterpreterCommand.GET_XATTR,
            absolutePath,
            key,

            consumer = {
                it.stdoutLineSequence().mapNotNull {
                    if (checkStatus(it)) return@mapNotNull null
                    it
                }.toList().single() // single can be used since command will fail if not found
            }
        )
    }

    override fun setMetaValue(ctx: FSUserContext, path: String, key: String, value: String) {
        val absolutePath = translateAndCheckFile(path)
        return ctx.runCommand(
            InterpreterCommand.SET_XATTR,
            absolutePath,
            key,
            value,

            consumer = {
                val hasExitCode = it.stdoutLineSequence().any { checkStatus(it) }
                if (!hasExitCode) throw IllegalStateException("Bad output from interpreter")
            }
        )
    }

    override suspend fun tree(
        ctx: FSUserContext,
        path: String,
        mode: Set<FileAttribute>,
        itemHandler: suspend (FileRow) -> Unit
    ) {
        val absolutePath = translateAndCheckFile(path)
        ctx.runCommand(
            InterpreterCommand.TREE,
            absolutePath,
            mode.asBitSet().toString(),

            consumer = {
                parseFileAttributes(it.stdoutLineSequence(), mode).map { it.normalize() }.forEach {
                    runBlocking { itemHandler(it) }
                }
            }
        )
    }


    override fun annotateFiles(ctx: FSUserContext, path: String, annotation: String) {
        validateAnnotation(annotation)
        setMetaValue(ctx, path, "annotate${UUID.randomUUID().toString().replace("-", "")}", annotation)
    }

    override fun markAsOpenAccess(ctx: FSUserContext, path: String) {
        val mountedPath = translateAndCheckFile(path)

        val parents: List<String> = run {
            if (path == "/") throw ShareException.BadRequest("Cannot grant rights on root")
            val parents = path.parents()

            parents.filter { it != "/" && it != "/home/" }
        }.map { translateAndCheckFile(it) }

        // Execute rights are required on all parent directories (otherwise we cannot perform the
        // traversal to the share)
        parents.forEach { fileACLService.createEntryForOthers(ctx, it, setOf(AccessRight.EXECUTE)) }

        // Grant on both default and current list
        fileACLService.createEntryForOthers(
            ctx,
            mountedPath,
            setOf(AccessRight.READ),
            defaultList = true,
            recursive = true
        )

        fileACLService.createEntryForOthers(
            ctx,
            mountedPath,
            setOf(AccessRight.READ),
            defaultList = false,
            recursive = true
        )
    }

    private fun favoritesDirectory(ctx: FSUserContext): String {
        return joinPath(homeDirectory(ctx), "Favorites", isDirectory = true)
    }

    private fun String.parents(): List<String> {
        val components = components().dropLast(1)
        return components.mapIndexed { index, s ->
            val path = "/" + components.subList(0, index + 1).joinToString("/").removePrefix("/")
            if (path == "/") path else "$path/"
        }
    }

    private fun String.components(): List<String> = split("/")

    private fun String.fileName(): String = File(this).name

    private fun String.normalize(): String = File(this).normalize().path

    private fun translateAndCheckFile(internalPath: String, isDirectory: Boolean = false): String {
        val path = File(fsRoot, internalPath)
            .normalize()
            .absolutePath
            .let { it + (if (isDirectory) "/" else "") }

        if (!path.startsWith(fsRoot)) throw IllegalArgumentException("path is not in root")
        if (path.contains("\n")) throw IllegalArgumentException("Path cannot contain new-lines")
        if (path.length >= PATH_MAX) throw IllegalArgumentException("Path is too long")

        return path
    }

    private fun String.toCloudPath(): String {
        return "/" + substringAfter(fsRoot).removePrefix("/")
    }

    private fun parseFileAttributes(sequence: Sequence<String>, attributes: Set<FileAttribute>): Sequence<FileRow> {
        return parseFileAttributes(sequence.iterator(), attributes)
    }

    private fun parseFileAttributes(iterator: Iterator<String>, attributes: Set<FileAttribute>): Sequence<FileRow> {
        return FileAttribute.rawParse(iterator, attributes).map { it.normalize() }
    }

    private fun FileRow.normalize(): FileRow {
        fun normalizeShares(incoming: List<AccessEntry>): List<AccessEntry> {
            return incoming.map {
                if (it.isGroup) {
                    it
                } else {
                    it.copy(entity = cloudToCephFsDao.findCloudUser(it.entity)!!)
                }
            }
        }

        return copy(
            owner = owner?.let { cloudToCephFsDao.findCloudUser(it)!! },
            path = path?.toCloudPath(),
            shares = shares?.let { normalizeShares(it) }
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(CephFSFileSystemService::class.java)
        const val PATH_MAX = 1024

        private val STORAGE_FILE_ATTRIBUTES = setOf(
            FileAttribute.FILE_TYPE,
            FileAttribute.PATH,
            FileAttribute.TIMESTAMPS,
            FileAttribute.OWNER,
            FileAttribute.SIZE,
            FileAttribute.SHARES,
            FileAttribute.SENSITIVITY,
            FileAttribute.ANNOTATIONS,
            FileAttribute.INODE,
            FileAttribute.IS_LINK
        )

        private val CREATED_OR_MODIFIED_ATTRIBUTES = setOf(
            FileAttribute.FILE_TYPE,
            FileAttribute.INODE,
            FileAttribute.PATH,
            FileAttribute.TIMESTAMPS,
            FileAttribute.OWNER
        )
    }
}
