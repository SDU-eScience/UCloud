package dk.sdu.cloud.storage.services.cephfs

import dk.sdu.cloud.storage.api.*
import dk.sdu.cloud.storage.services.*
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.util.*

class CephFSFileSystemService(
    private val cloudToCephFsDao: CloudToCephFsDao,
    private val processRunner: ProcessRunnerFactory,
    private val fileACLService: FileACLService,
    private val fsRoot: String,
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
        val key = lsCachingKey(path)
        val cachedResult = ctx.retrieveOrNull(key)
        if (cachedResult != null) return cachedResult

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
        ).also {
            ctx.store(key, it)
        }
    }

    private fun retrieveFavoriteInodeSet(ctx: FSUserContext): Set<String> =
        retrieveFavorites(ctx).map { it.inode }.toSet()

    override fun retrieveFavorites(
        ctx: FSUserContext
    ): List<FavoritedFile> {
        val cachedResult = ctx.retrieveOrNull(FAVORITES_KEY)
        if (cachedResult != null) return cachedResult

        val dir = translateAndCheckFile(favoritesDirectory(ctx), true)

        val attributes = setOf(
            FileAttribute.FILE_TYPE,
            FileAttribute.PATH,
            FileAttribute.LINK_TARGET,
            FileAttribute.LINK_INODE,
            FileAttribute.INODE
        )

        return (try {
            ctx.runCommand(
                InterpreterCommand.LIST_DIRECTORY,
                dir,
                attributes.asBitSet().toString(),

                consumer = {
                    parseFileAttributes(it.stdoutLineSequence(), attributes).map {
                        FavoritedFile(
                            type = it.fileType!!,
                            from = it.path!!,
                            to = it.linkTarget!!,
                            inode = it.linkInode!!,
                            favInode = it.inode!!
                        )
                    }.toList()
                }
            )
        } catch (ex: FSException.NotFound) {
            return emptyList()
        }).also {
            ctx.store(FAVORITES_KEY, it)
        }
    }

    override fun stat(ctx: FSUserContext, path: String): StorageFile? {
        val key = statCachingKey(path)
        val cachedResult = ctx.retrieveOrNull(key)
        if (cachedResult != null) return cachedResult

        val favorites = retrieveFavoriteInodeSet(ctx)
        val mountedPath = translateAndCheckFile(path)

        return (try {
            ctx.runCommand(
                InterpreterCommand.STAT,
                mountedPath,
                STORAGE_FILE_ATTRIBUTES.asBitSet().toString(),

                consumer = {
                    parseFileAttributes(it.stdoutLineSequence(), STORAGE_FILE_ATTRIBUTES).map {
                        readStorageFile(it, favorites)
                    }.singleOrNull()
                }
            )
        } catch (ex: FSException) {
            when (ex) {
                is FSException.NotFound, is FSException.BadRequest, is FSException.PermissionException -> {
                    return null
                }
                else -> throw ex
            }
        }).also {
            if (it != null) ctx.store(key, it)
        }
    }

    fun exists(ctx: FSUserContext, path: String): Boolean {
        return stat(ctx, path) != null
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

        ctx.invalidate(lsCachingKey(path.normalize().parent()))
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
            FileAttribute.GROUP,
            FileAttribute.PATH
        )

        ctx.runCommand(
            InterpreterCommand.DELETE,
            absolutePath,
            consumer = {
                val sequence = it.stdoutLineSequence().toList()
                sequence.forEachIndexed { index, s -> log.debug("$index: $s") }
                parseFileAttributes(sequence.iterator(), attributes).forEach {
                    val parent = it.path!!.parent()
                    ctx.invalidate(lsCachingKey(parent))
                    ctx.invalidate(statCachingKey(parent))
                    ctx.invalidate(statCachingKey(it.path))

                    launch {
                        eventProducer.emit(
                            StorageEvent.Deleted(
                                id = it.inode!!,
                                path = it.path,
                                owner = it.owner!!,
                                timestamp = timestamp
                            )
                        )
                    }
                }
            }
        )

    }

    override fun move(ctx: FSUserContext, path: String, newPath: String, conflictPolicy: WriteConflictPolicy) {
        val timestamp = System.currentTimeMillis()
        val absolutePath = translateAndCheckFile(path)

        val targetPath = renameAccordingToPolicy(ctx, newPath, conflictPolicy) ?: throw FSException.AlreadyExists()
        val newAbsolutePath = translateAndCheckFile(targetPath)

        val stat = stat(ctx, newAbsolutePath)
        if (stat != null && stat.type != FileType.DIRECTORY) {
            throw FSException.AlreadyExists(newPath)
        }

        val attributes = setOf(
            FileAttribute.FILE_TYPE,
            FileAttribute.INODE,
            FileAttribute.PATH,
            FileAttribute.OWNER
        )

        val allowOverwrite = conflictPolicy == WriteConflictPolicy.OVERWRITE

        ctx.runCommand(
            InterpreterCommand.MOVE,
            absolutePath,
            newAbsolutePath,
            if (allowOverwrite) "1" else "0",

            consumer = {
                parseFileAttributes(it.stdoutLineSequence(), attributes).forEach {
                    val oldPath =
                        if (stat?.type == FileType.DIRECTORY) it.path!!.removePrefix(newPath).removePrefix("/").let {
                            File(path, it).normalize().path
                        }
                        else path.normalize()

                    ctx.invalidate(lsCachingKey(oldPath.parent()))
                    ctx.invalidate(statCachingKey(oldPath))

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

    private fun renameAccordingToPolicy(
        ctx: FSUserContext,
        desiredTargetPath: String,
        conflictPolicy: WriteConflictPolicy
    ): String? {
        if (conflictPolicy == WriteConflictPolicy.OVERWRITE) return desiredTargetPath

        // Performance: This will cause a lot of stats, on items in the same folder, for the most part we could
        // simply ls a common root and cache it. This should be a lot more efficient.
        val targetExists = exists(ctx, desiredTargetPath)
        return when (conflictPolicy) {
            WriteConflictPolicy.OVERWRITE -> desiredTargetPath

            WriteConflictPolicy.RENAME -> {
                if (targetExists) findFreeNameForNewFile(ctx, desiredTargetPath)
                else desiredTargetPath
            }

            WriteConflictPolicy.REJECT -> {
                if (targetExists) null
                else desiredTargetPath
            }
        }
    }

    override fun copy(ctx: FSUserContext, path: String, newPath: String, conflictPolicy: WriteConflictPolicy) {
        val fromNormalized = path.normalize()
        val toNormalized = newPath.normalize()
        val allowOverwrite = conflictPolicy == WriteConflictPolicy.OVERWRITE

        val fromStat = stat(ctx, fromNormalized) ?: throw FSException.NotFound()
        if (fromStat.type != FileType.DIRECTORY) {
            val absoluteSourcePath = translateAndCheckFile(fromNormalized)
            val absoluteTargetPath =
                renameAccordingToPolicy(ctx, toNormalized, conflictPolicy)?.let { translateAndCheckFile(it) }
                        ?: throw FSException.AlreadyExists()

            ctx.runCommand(
                InterpreterCommand.COPY,
                absoluteSourcePath,
                absoluteTargetPath,
                if (allowOverwrite) "1" else "0",

                consumer = {
                    parseFileAttributes(it.stdoutLineSequence(), CREATED_OR_MODIFIED_ATTRIBUTES).forEach {
                        launch { eventProducer.emit(createdOrModifiedFromRow(it)) }
                    }
                }
            )

            ctx.invalidate(lsCachingKey(toNormalized.parent()))
        } else {
            // toList() call is important, as we want to re-use the ctx (which can only run one command at a time)
            val treeList = tree(ctx, fromNormalized, setOf(FileAttribute.PATH)).toList()

            val suppressedExceptions = ArrayList<FSException>()

            treeList.forEach {
                val currentPath = it.path!!

                val absoluteSourcePath = translateAndCheckFile(currentPath)

                val targetPath = run {
                    val desired = joinPath(toNormalized, relativize(fromNormalized, currentPath))
                    renameAccordingToPolicy(ctx, desired, conflictPolicy) ?: return@forEach run {
                        suppressedExceptions.add(FSException.AlreadyExists())
                    }
                }
                val absoluteTargetPath = translateAndCheckFile(targetPath)

                try {
                    ctx.runCommand(
                        InterpreterCommand.COPY,
                        absoluteSourcePath,
                        absoluteTargetPath,
                        if (allowOverwrite) "1" else "0",

                        consumer = {
                            val sequence = it.stdoutLineSequence().toList()

                            parseFileAttributes(
                                sequence.iterator(),
                                CREATED_OR_MODIFIED_ATTRIBUTES
                            ).forEach {
                                launch { eventProducer.emit(createdOrModifiedFromRow(it)) }
                            }
                        }
                    )

                    ctx.invalidate(lsCachingKey(targetPath.parent()))
                } catch (ex: FSException.AlreadyExists) {
                    // Race condition (file was created after "exists" call but before copy "call")
                    // TODO For now we ignore these, but maybe we should retry?
                    log.debug("$currentPath to $newPath file already exists at target")
                    suppressedExceptions.add(ex)
                }
            }

            if (suppressedExceptions.isNotEmpty() && suppressedExceptions.size == treeList.size) {
                throw suppressedExceptions.first()
            }
        }
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
                it.clearBytes(it.stdout.readLineUnbuffered().toLong())
                it.stdout.consumer()
            }
        )
    }

    override suspend fun <T> coWrite(
        ctx: FSUserContext,
        path: String,
        conflictPolicy: WriteConflictPolicy,
        writer: suspend OutputStream.() -> T
    ): T {
        return write(ctx, path, conflictPolicy) {
            runBlocking { writer() }
        }
    }

    override fun <T> write(
        ctx: FSUserContext,
        path: String,
        conflictPolicy: WriteConflictPolicy,
        writer: OutputStream.() -> T
    ): T {
        val normalizedPath = path.normalize()
        val allowOverwrite = conflictPolicy == WriteConflictPolicy.OVERWRITE

        val targetPath =
            renameAccordingToPolicy(ctx, normalizedPath, conflictPolicy) ?: throw FSException.AlreadyExists()
        val absolutePath = translateAndCheckFile(targetPath)

        ctx.runCommand(
            InterpreterCommand.WRITE_OPEN,
            absolutePath,
            if (allowOverwrite) "1" else "0",

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

        ctx.invalidate(lsCachingKey(normalizedPath.parent()))

        return result!!
    }

    override fun createSoftSymbolicLink(ctx: FSUserContext, linkFile: String, pointsTo: String) {
        val absLinkPath = translateAndCheckFile(linkFile)
        val absPointsToPath = translateAndCheckFile(pointsTo)

        ctx.runCommand(
            InterpreterCommand.SYMLINK,
            absPointsToPath,
            absLinkPath,

            consumer = {
                val row = parseFileAttributes(it.stdoutLineSequence(), CREATED_OR_MODIFIED_ATTRIBUTES).toList().single()
                launch {
                    val value = createdOrModifiedFromRow(row)
                    ctx.invalidate(lsCachingKey(value.path.parent()))
                    eventProducer.emit(value)
                }
            }
        )
    }

    override fun createFavorite(ctx: FSUserContext, fileToFavorite: String) {
        val favoritesDirectory = favoritesDirectory(ctx)
        if (!exists(ctx, favoritesDirectory)) {
            mkdir(ctx, favoritesDirectory)
        }

        val targetLocation =
            findFreeNameForNewFile(ctx, joinPath(favoritesDirectory, fileToFavorite.fileName()))

        createSoftSymbolicLink(ctx, targetLocation, fileToFavorite)

        ctx.invalidate(lsCachingKey(favoritesDirectory))
        ctx.invalidate(FAVORITES_KEY)
    }

    override fun removeFavorite(ctx: FSUserContext, favoriteFileToRemove: String) {
        val stat = stat(ctx, favoriteFileToRemove) ?: throw IllegalStateException()
        val allFavorites = retrieveFavorites(ctx)
        val toRemove = allFavorites.filter { it.inode == stat.inode || it.favInode == stat.inode }
        if (toRemove.isEmpty()) return
        toRemove.forEach { rmdir(ctx, it.from) }

        ctx.invalidate(lsCachingKey(favoritesDirectory(ctx)))
        ctx.invalidate(FAVORITES_KEY)
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

        val normalizedPath = path.normalize()
        ctx.invalidate(lsCachingKey(normalizedPath.parent()))
        ctx.invalidate(statCachingKey(normalizedPath))
    }

    override fun revokeRights(ctx: FSUserContext, toUser: String, path: String) {
        // TODO Need to look up share API to determine if we should delete execute rights on parent dirs
        val mountedPath = translateAndCheckFile(path)
        fileACLService.removeEntry(ctx, toUser, mountedPath, defaultList = true, recursive = true)
        fileACLService.removeEntry(ctx, toUser, mountedPath, defaultList = false, recursive = true)

        val normalizedPath = path.normalize()
        ctx.invalidate(lsCachingKey(normalizedPath.parent()))
        ctx.invalidate(statCachingKey(normalizedPath))
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
                assert(it.stdoutLineSequence().any { checkStatus(it) })
            }
        ).also {
            val normalizedPath = path.normalize()
            ctx.invalidate(lsCachingKey(normalizedPath.parent()))
            ctx.invalidate(statCachingKey(normalizedPath))
        }
    }

    override fun tree(ctx: FSUserContext, path: String, mode: Set<FileAttribute>): Sequence<FileRow> {
        val absolutePath = translateAndCheckFile(path)
        return ctx.runCommand(
            InterpreterCommand.TREE,
            absolutePath,
            mode.asBitSet().toString(),

            consumer = {
                val lines = it.stdoutLineSequence().toList() // TODO FIXME
                parseFileAttributes(lines.iterator(), mode)
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

        val normalizedPath = path.normalize()
        ctx.invalidate(lsCachingKey(normalizedPath.parent()))
        ctx.invalidate(statCachingKey(normalizedPath))
    }

    private fun favoritesDirectory(ctx: FSUserContext): String {
        return joinPath(homeDirectory(ctx), "Favorites", isDirectory = true)

    }

    private fun String.parents(): List<String> {
        val components = components().dropLast(1)
        return components.mapIndexed { index, _ ->
            val path = "/" + components.subList(0, index + 1).joinToString("/").removePrefix("/")
            if (path == "/") path else "$path/"
        }
    }

    private fun String.parent(): String {
        val components = components().dropLast(1)
        if (components.isEmpty()) return "/"

        val path = "/" + components.joinToString("/").removePrefix("/")
        return if (path == "/") path else "$path/"
    }

    private fun String.components(): List<String> = removeSuffix("/").split("/")

    private fun String.fileName(): String = File(this).name

    private fun String.normalize(): String = File(this).normalize().path

    private fun relativize(rootPath: String, absolutePath: String): String {
        return URI(rootPath).relativize(URI(absolutePath)).path
    }

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
        return ("/" + substringAfter(fsRoot).removePrefix("/")).normalize()
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
            linkTarget = linkTarget?.toCloudPath(),
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

        private val FAVORITES_KEY = ProcessRunnerAttributeKey<List<FavoritedFile>>("favorites")

        private fun lsCachingKey(path: String): ProcessRunnerAttributeKey<List<StorageFile>> {
            return ProcessRunnerAttributeKey("ls:$path")
        }

        private fun statCachingKey(path: String): ProcessRunnerAttributeKey<StorageFile> {
            return ProcessRunnerAttributeKey("stat:$path")
        }
    }
}
