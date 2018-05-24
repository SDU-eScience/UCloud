package dk.sdu.cloud.storage.services.cephfs

import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.storage.api.*
import dk.sdu.cloud.storage.services.*
import dk.sdu.cloud.storage.util.BashEscaper
import dk.sdu.cloud.storage.util.CommaSeparatedLexer
import kotlinx.coroutines.experimental.launch
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.*

data class FavoritedFile(val type: FileType, val from: String, val to: String, val inode: Long)

class CephFSFileSystemService(
    private val cloudToCephFsDao: CloudToCephFsDao,
    private val processRunner: ProcessRunnerFactory,
    private val fileACLService: FileACLService,
    private val xAttrService: XAttrService,
    private val treeService: TreeService,
    private val copyService: CopyService,
    private val removeService: RemoveService,
    private val fsRoot: String,
    private val isDevelopment: Boolean = false,
    private val eventProducer: StorageEventProducer
) : FileSystemService {
    override fun openContext(user: String): FSUserContext {
        return processRunner(user)
    }

    override fun ls(
        ctx: FSUserContext,
        path: String,
        includeImplicit: Boolean,
        includeFavorites: Boolean
    ): List<StorageFile> {
        val absolutePath = translateAndCheckFile(path)
        val cloudPath = absolutePath.toCloudPath()

        val command = mutableListOf(dirListingExecutable)
        if (includeFavorites) command += listOf("--fav", translateAndCheckFile(favoritesDirectory(ctx), true))
        val (status, stdout, stderr) = ctx.runWithResultAsInMemoryString(command, absolutePath)

        if (status != 0) {
            log.info("ls failed ${ctx.user}, $path")
            log.info(stderr)
            throw IllegalStateException()
        } else {
            return parseDirListingOutput(
                File(cloudPath),
                stdout,
                includeImplicit,
                includeFavorites
            ).second
        }
    }

    override fun retrieveFavorites(
        ctx: FSUserContext
    ): List<FavoritedFile> {
        val command = mutableListOf(
            dirListingExecutable,
            "--fav",
            translateAndCheckFile(favoritesDirectory(ctx), true),
            "--just-fav"
        )

        val (status, stdout, stderr) = ctx.runWithResultAsInMemoryString(command)

        if (status == 0) {
            return parseDirListingOutput(
                File(translateAndCheckFile(homeDirectory(ctx))),
                stdout,
                false,
                true
            ).first
        } else {
            log.warn("retrieveFavorites failed: $status, $stderr")
            throw IllegalStateException()
        }
    }

    override fun stat(ctx: FSUserContext, path: String): StorageFile? {
        val normalizedPath = File(path).normalize().path
        return try {
            // TODO This is a bit lazy
            val results = ls(ctx, path.removeSuffix("/").substringBeforeLast('/'), true, false)
            results.find { it.path.removeSuffix("/") == normalizedPath.removeSuffix("/") }
        } catch (ex: Exception) {
            when (ex) {
                is FileSystemException.NotFound -> return null
                else -> throw ex
            }
        }
    }

    override fun mkdir(ctx: FSUserContext, path: String) {
        val absolutePath = translateAndCheckFile(path)
        val (status, _, stderr) = ctx.runWithResultAsInMemoryString(
            listOf("mkdir", "-p", absolutePath)
        )

        if (status != 0) {
            when {
                stderr.endsWith("Permission denied") -> throw FileSystemException.PermissionException()
                stderr.endsWith("File exists") -> throw FileSystemException.AlreadyExists(
                    path
                )
                else -> {
                    throw FileSystemException.CriticalException("mkdir failed ${ctx.user}, $path, $stderr")
                }
            }
        } else {
            launch {
                val fileStat = stat(ctx, path) ?: return@launch run {
                    log.warn("Unable to find directory after creation. stat returned null!")
                    log.warn("Path is $path")
                }

                // TODO This will not emit for dirs before that

                eventProducer.emit(
                    StorageEvent.CreatedOrModified(
                        id = fileStat.inode.toString(),
                        path = fileStat.path,
                        owner = fileStat.ownerName,
                        timestamp = fileStat.createdAt,
                        type = fileStat.type
                    )
                )
            }
        }
    }

    override fun rmdir(ctx: FSUserContext, path: String) {
        val absolutePath = translateAndCheckFile(path)

        val removedItems = removeService.remove(ctx, absolutePath).map {
            it.copy(path = it.path.toCloudPath())
        }

        if (removedItems.isEmpty()) {
            // TODO Do we want to give feedback for individual items that could not be deleted?
            throw FileSystemException.PermissionException()
        }

        launch {
            removedItems.forEach {
                eventProducer.emit(it)
            }
        }
    }

    override fun move(ctx: FSUserContext, path: String, newPath: String) {
        val absolutePath = translateAndCheckFile(path)
        val newAbsolutePath = translateAndCheckFile(newPath)

        val stat = stat(ctx, newAbsolutePath)
        if (stat != null && stat.type != FileType.DIRECTORY) {
            throw FileSystemException.AlreadyExists(newPath)
        }

        val (status, _, stderr) = ctx.runWithResultAsInMemoryString(
            listOf("mv", absolutePath, newAbsolutePath)
        )
        if (status != 0) {
            if (stderr.contains("Permission denied")) throw FileSystemException.PermissionException()
            else throw FileSystemException.CriticalException("mv failed $status, ${ctx.user}, $path, $stderr")
        } else {
            launch {
                val fileStat = stat(ctx, newPath) ?: return@launch run {
                    log.warn("Unable to stat file after move. path=$path, newPath=$newPath")
                }

                if (!fileStat.link && fileStat.type == FileType.DIRECTORY) {
                    // TODO This is incorrect if target is moved into directory
                    syncList(ctx, newPath) {
                        eventProducer.emit(
                            StorageEvent.Moved(
                                id = it.uniqueId,
                                path = it.path,
                                owner = it.user,
                                timestamp = it.createdAt,
                                oldPath = it.path.removePrefix(newPath).removePrefix("/").let {
                                    File("$path/$it").normalize().path
                                }
                            )
                        )
                    }
                }

                eventProducer.emit(
                    StorageEvent.Moved(
                        id = fileStat.inode.toString(),
                        path = fileStat.path,
                        owner = fileStat.ownerName,
                        timestamp = fileStat.createdAt,
                        oldPath = path
                    )
                )
            }
        }
    }

    override fun copy(ctx: FSUserContext, path: String, newPath: String) {
        val absolutePath = translateAndCheckFile(path)
        val newAbsolutePath = translateAndCheckFile(newPath)

        val copiedItems =
            copyService.copy(ctx, absolutePath, newAbsolutePath).map { it.copy(path = it.path.toCloudPath()) }

        if (copiedItems.isEmpty()) {
            throw FileSystemException.PermissionException()
        }

        launch { copiedItems.forEach { eventProducer.emit(it) } }
    }

    override fun read(ctx: FSUserContext, path: String): InputStream {
        val absolutePath = translateAndCheckFile(path)
        // TODO Permission (sensitivity) check
        return ctx.run(listOf("cat", absolutePath)).inputStream
    }

    override fun write(ctx: FSUserContext, path: String, writer: OutputStream.() -> Unit) {
        val absolutePath = translateAndCheckFile(path)

        // TODO Permission (sensitivity) check
        val process =
            ctx.run(listOf("cat - > ${BashEscaper.safeBashArgument(absolutePath)}"), noEscape = true)
        process.outputStream.writer()
        process.outputStream.close()
        if (process.waitFor() != 0) {
            log.info("write failed ${ctx.user}, $path")
            log.info(process.errorStream.reader().readText())
            throw IllegalStateException()
        } else {
            launch {
                val fileStat = stat(ctx, path) ?: return@launch run {
                    log.warn("Unable to stat file after copy. path=$path")
                }

                eventProducer.emit(
                    StorageEvent.CreatedOrModified(
                        id = fileStat.inode.toString(),
                        path = fileStat.path,
                        owner = fileStat.ownerName,
                        timestamp = fileStat.createdAt,
                        type = fileStat.type
                    )
                )
            }
        }
    }

    internal fun parseDirListingOutput(
        where: File,
        output: String,
        includeImplicit: Boolean = false,
        parseFavorites: Boolean = false
    ): Pair<List<FavoritedFile>, List<StorageFile>> {
        /*
        Example output:

        D,0,509,root,root,4096,1523862649,1523862649,1523862650,3,user1,14,user2,2,user3,6,CONFIDENTIAL,.
        D,0,493,root,root,4096,1523862224,1523862224,1523862237,0,CONFIDENTIAL,..
        F,0,420,root,root,0,1523862649,1523862649,1523862649,0,CONFIDENTIAL,qwe
        */

        fun parseDirType(token: String): FileType = when (token) {
            "D" -> FileType.DIRECTORY
            "F" -> FileType.FILE
            "L" -> FileType.LINK
            else -> throw IllegalStateException("Bad type from retrieveFavorites section: $token, $output")
        }

        val rawLines = output.lines()
        val (favoriteLines, outputLines) = if (parseFavorites) {
            val linesToTake = rawLines.first().toInt()
            Pair(rawLines.take(linesToTake * 4 + 1).drop(1), rawLines.drop(linesToTake * 4 + 1))
        } else {
            Pair(emptyList(), rawLines)
        }

        val favorites = if (parseFavorites) {
            if (favoriteLines.size % 4 != 0) {
                throw IllegalStateException("Bad output from retrieveFavorites section: $output")
            }

            (0 until favoriteLines.size).step(4).map { i ->
                val type = parseDirType(favoriteLines[i])
                val from = favoriteLines[i + 1]
                val to = favoriteLines[i + 2]
                val inode = favoriteLines[i + 3].toLong()

                FavoritedFile(type, from, to, inode)
            }
        } else {
            emptyList()
        }

        val lexer = CommaSeparatedLexer()
        val files = outputLines.mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            lexer.line = line

            val dirTypeToken = lexer.readToken()
            val dirType = parseDirType(dirTypeToken)
            val isLink = lexer.readToken().toInt() != 0

            val unixPermissions = lexer.readToken().toInt()

            val user = cloudToCephFsDao.findCloudUser(lexer.readToken()) ?: return@mapNotNull null
            val group = lexer.readToken() // TODO translate

            val size = lexer.readToken().toLong()
            val createdAt = lexer.readToken().toLong()
            val modifiedAt = lexer.readToken().toLong()
            val accessedAt = lexer.readToken().toLong()

            val inode = lexer.readToken().toLong()
            val isFavorited = favorites.any { it.inode == inode }

            val aclEntries = lexer.readToken().toInt()
            val entries = (0 until aclEntries).map {
                val aclEntity = lexer.readToken()
                val mode = lexer.readToken().toInt()

                val isGroup = (mode and SHARED_WITH_UTYPE) != 0
                val hasRead = (mode and SHARED_WITH_READ) != 0
                val hasWrite = (mode and SHARED_WITH_WRITE) != 0
                val hasExecute = (mode and SHARED_WITH_EXECUTE) != 0

                val rights = mutableSetOf<AccessRight>()
                if (hasRead) rights += AccessRight.READ
                if (hasWrite) rights += AccessRight.WRITE
                if (hasExecute) rights += AccessRight.EXECUTE

                AccessEntry(aclEntity, isGroup, rights)
            }

            val annotations = lexer.readToken().toCharArray().map { it.toString() }.toSet()

            val sensitivity = try {
                SensitivityLevel.valueOf(lexer.readToken())
            } catch (ex: Exception) {
                log.debug(ex.stackTraceToString())
                throw FileSystemException.CriticalException(
                    "Internal error. " +
                            "Could not resolve sensitivity level ${lexer.line}"
                )
            }

            val fileName = line.substring(lexer.cursor)
            if (!includeImplicit && (fileName == ".." || fileName == "." || fileName == "")) return@mapNotNull null
            val filePath = File(where, fileName).normalize().absolutePath

            // Don't attempt to return details about the parent of mount
            if (filePath == "/") return@mapNotNull null

            StorageFile(
                type = dirType,
                link = isLink,
                path = filePath,
                createdAt = createdAt * 1000L,
                modifiedAt = modifiedAt * 1000L,
                ownerName = user,
                size = size,
                acl = entries,
                favorited = isFavorited,
                sensitivityLevel = sensitivity,
                inode = inode,
                annotations = annotations
            )
        }

        return Pair(favorites, files)
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

    override fun listMetadataKeys(ctx: FSUserContext, path: String): List<String> {
        return xAttrService.getAttributeList(ctx, path).keys.toList()
    }

    override fun getMetaValue(ctx: FSUserContext, path: String, key: String): String {
        return xAttrService.getAttributeList(ctx, translateAndCheckFile(path))[key]
                ?: throw FileSystemException.NotFound("path: $path, key: $key")
    }

    override fun setMetaValue(ctx: FSUserContext, path: String, key: String, value: String) {
        xAttrService.setAttribute(ctx, translateAndCheckFile(path), key, value)
    }

    override suspend fun syncList(
        ctx: FSUserContext,
        path: String,
        modifiedSince: Long,
        itemHandler: suspend (SyncItem) -> Unit
    ) {
        treeService.listAt(ctx, translateAndCheckFile(path), modifiedSince) {
            itemHandler(
                it.copy(
                    user = cloudToCephFsDao.findCloudUser(it.user)
                            ?: throw FileSystemException.CriticalException("Could not resolve user (${it.user})"),
                    path = it.path.toCloudPath(),
                    group = it.group // TODO Should likely translate
                )
            )
        }
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

    private fun translateAndCheckFile(internalPath: String, isDirectory: Boolean = false): String {
        return File(fsRoot, internalPath)
            .normalize()
            .absolutePath
            .takeIf { it.startsWith(fsRoot) }?.let { it + (if (isDirectory) "/" else "") }
                ?: throw IllegalArgumentException("path is not in root ($internalPath)")
    }

    private val dirListingExecutable: String
        get() = if (isDevelopment) File("./bin/osx/dirlisting").absolutePath else "dirlisting"

    private fun String.toCloudPath(): String {
        return "/" + substringAfter(fsRoot).removePrefix("/")
    }

    companion object {
        private val log = LoggerFactory.getLogger(CephFSFileSystemService::class.java)

        private const val SHARED_WITH_UTYPE = 1
        private const val SHARED_WITH_READ = 2
        private const val SHARED_WITH_WRITE = 4
        private const val SHARED_WITH_EXECUTE = 8
    }
}

