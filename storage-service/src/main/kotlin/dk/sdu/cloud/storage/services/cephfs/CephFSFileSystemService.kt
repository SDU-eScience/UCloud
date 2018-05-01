package dk.sdu.cloud.storage.services.cephfs

import dk.sdu.cloud.storage.api.*
import dk.sdu.cloud.storage.services.FileSystemException
import dk.sdu.cloud.storage.services.FileSystemService
import dk.sdu.cloud.storage.services.ShareException
import dk.sdu.cloud.storage.util.BashEscaper
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.io.OutputStream

data class FavoritedFile(val type: FileType, val from: String, val to: String, val inode: Long)

class CephFSFileSystemService(
    private val cloudToCephFsDao: CloudToCephFsDao,
    private val processRunner: CephFSProcessRunner,
    private val fileACLService: FileACLService,
    private val xAttrService: XAttrService,
    private val fsRoot: String,
    private val isDevelopment: Boolean = false
) : FileSystemService {
    override fun ls(
        user: String,
        path: String,
        includeImplicit: Boolean,
        includeFavorites: Boolean
    ): List<StorageFile> {
        val absolutePath = translateAndCheckFile(path)
        val cloudPath = absolutePath.toCloudPath()

        val command = mutableListOf(dirListingExecutable)
        if (includeFavorites) command += listOf("--fav", translateAndCheckFile(favoritesDirectory(user), true))
        val (status, stdout, stderr) = processRunner.runAsUserWithResultAsInMemoryString(user, command, absolutePath)

        if (status != 0) {
            log.info("ls failed $user, $path")
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
        user: String
    ): List<FavoritedFile> {
        val command = mutableListOf(
            dirListingExecutable,
            "--fav",
            translateAndCheckFile(favoritesDirectory(user), true),
            "--just-fav"
        )

        val (status, stdout, stderr) = processRunner.runAsUserWithResultAsInMemoryString(user, command)

        if (status == 0) {
            return parseDirListingOutput(
                File(translateAndCheckFile(homeDirectory(user))),
                stdout,
                false,
                true
            ).first
        } else {
            log.warn("retrieveFavorites failed: $status, $stderr")
            throw IllegalStateException()
        }
    }

    override fun stat(user: String, path: String): StorageFile? {
        return try {
            // TODO This is a bit lazy
            val results = ls(user, path.removeSuffix("/").substringBeforeLast('/'), true, false)
            results.find { it.path == path }
        } catch (ex: Exception) {
            when (ex) {
                is FileSystemException.NotFound -> return null
                else -> throw ex
            }
        }
    }

    override fun mkdir(user: String, path: String) {
        val absolutePath = translateAndCheckFile(path)
        val (status, _, stderr) = processRunner.runAsUserWithResultAsInMemoryString(
            user,
            listOf("mkdir", "-p", absolutePath)
        )

        if (status != 0) {
            when {
                stderr.endsWith("Permission denied") -> throw FileSystemException.PermissionException()
                stderr.endsWith("File exists") -> throw FileSystemException.AlreadyExists(
                    path
                )
                else -> {
                    throw FileSystemException.CriticalException("mkdir failed $user, $path, $stderr")
                }
            }
        }
    }

    override fun rmdir(user: String, path: String) {
        val absolutePath = translateAndCheckFile(path)

        val (status, _, stderr) = processRunner.runAsUserWithResultAsInMemoryString(
            user,
            listOf("rm", "-rf", absolutePath)
        )
        if (status != 0) {
            if (stderr.contains("Permission denied")) throw FileSystemException.PermissionException()
            else throw FileSystemException.CriticalException("rm failed $status, $user, $path, $stderr")
        }
    }

    override fun move(user: String, path: String, newPath: String) {
        val absolutePath = translateAndCheckFile(path)
        val newAbsolutePath = translateAndCheckFile(newPath)

        val stat = stat(user, newAbsolutePath)
        if (stat != null && stat.type != FileType.DIRECTORY) {
            throw FileSystemException.AlreadyExists(newPath)
        }

        val (status, _, stderr) = processRunner.runAsUserWithResultAsInMemoryString(
            user,
            listOf("mv", absolutePath, newAbsolutePath)
        )
        if (status != 0) {
            if (stderr.contains("Permission denied")) throw FileSystemException.PermissionException()
            else throw FileSystemException.CriticalException("mv failed $status, $user, $path, $stderr")
        }
    }

    override fun copy(user: String, path: String, newPath: String) {
        val absolutePath = translateAndCheckFile(path)
        val newAbsolutePath = translateAndCheckFile(newPath)

        val (status, _, stderr) = processRunner.runAsUserWithResultAsInMemoryString(
            user,
            listOf("cp", "-r", absolutePath, newAbsolutePath)
        )
        if (status != 0) {
            when {
                stderr.contains("Permission denied") -> throw FileSystemException.PermissionException()
                stderr.contains("Not a directory") -> throw FileSystemException.BadRequest(
                    "Cannot copy to this location"
                )
                else -> throw FileSystemException.CriticalException("cp failed $status, $user, $path, $stderr")
            }
        }
    }

    override fun read(user: String, path: String): InputStream {
        val absolutePath = translateAndCheckFile(path)
        // TODO Permission (sensitivity) check
        return processRunner.runAsUser(user, listOf("cat", absolutePath)).inputStream
    }

    override fun write(user: String, path: String, writer: OutputStream.() -> Unit) {
        val absolutePath = translateAndCheckFile(path)

        // TODO Permission (sensitivity) check
        val process =
            processRunner.runAsUser(user, listOf("bash", "-c", "cat - > ${BashEscaper.safeBashArgument(absolutePath)}"))
        process.outputStream.writer()
        process.outputStream.close()
        if (process.waitFor() != 0) {
            log.info("write failed $user, $path")
            log.info(process.errorStream.reader().readText())
            throw IllegalStateException()
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

        val files = outputLines.mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null

            var cursor = 0
            val chars = line.toCharArray()
            fun readToken(): String {
                val builder = StringBuilder()
                while (cursor < chars.size) {
                    val c = chars[cursor++]
                    if (c == ',') break
                    builder.append(c)
                }
                return builder.toString()
            }

            val dirTypeToken = readToken()
            val dirType = parseDirType(dirTypeToken)
            val isLink = readToken().toInt() != 0

            val unixPermissions = readToken().toInt()

            val user = cloudToCephFsDao.findCloudUser(readToken()) ?: return@mapNotNull null
            val group = readToken() // TODO translate

            val size = readToken().toLong()
            val createdAt = readToken().toLong()
            val modifiedAt = readToken().toLong()
            val accessedAt = readToken().toLong()

            val inode = readToken().toLong()
            val isFavorited = favorites.any { it.inode == inode }

            val aclEntries = readToken().toInt()
            val entries = (0 until aclEntries).map {
                val aclEntity = readToken()
                val mode = readToken().toInt()

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

            val sensitivity = SensitivityLevel.valueOf(readToken())
            val fileName = line.substring(cursor)
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
                inode = inode
            )
        }

        return Pair(favorites, files)
    }

    override fun createSoftSymbolicLink(user: String, linkFile: String, pointsTo: String) {
        val absLinkPath = translateAndCheckFile(linkFile)
        val absPointsToPath = translateAndCheckFile(pointsTo)

        // We only need to check target, the rest will be enforced. Ideally we wouldn't do this as two forks,
        // but can work for prototypes. TODO Performance

        // TODO Stat needs to not ls parent dir. Disabled for now
        // TODO Stat needs to not ls parent dir. Disabled for now
        // TODO Stat needs to not ls parent dir. Disabled for now
        // TODO Stat needs to not ls parent dir. Disabled for now
        if (false && stat(user, pointsTo) == null) {
            throw IllegalArgumentException("Cannot point to target ($linkFile, $pointsTo)")
        }

        val process = processRunner.runAsUser(user, listOf("ln", "-s", absPointsToPath, absLinkPath))
        val status = process.waitFor()
        if (status != 0) {
            log.info("ln failed $user, $absLinkPath $absPointsToPath")
            log.info(process.errorStream.reader().readText())
            throw IllegalStateException()
        }
    }

    override fun createFavorite(user: String, fileToFavorite: String) {
        // TODO Hack, but highly unlikely that we will have duplicates in practice.
        // TODO Create retrieveFavorites folder if it does not exist yet
//        val suffix = abs(random.nextInt()).toString(16)
        val targetLocation = findFreeNameForNewFile(user, joinPath(favoritesDirectory(user), fileToFavorite.fileName()))

        createSoftSymbolicLink(user, targetLocation, fileToFavorite)
    }

    override fun removeFavorite(user: String, favoriteFileToRemove: String) {
        val stat = stat(user, favoriteFileToRemove) ?: throw IllegalStateException()
        val allFavorites = retrieveFavorites(user)
        val toRemove = allFavorites.filter { it.inode == stat.inode }
        if (toRemove.isEmpty()) return
        val command = listOf("rm") + toRemove.map { it.from }
        val process = processRunner.runAsUser(user, command)
        val status = process.waitFor()
        if (status != 0) {
            log.info("rm failed $user")
            log.info(process.errorStream.reader().readText())
            throw IllegalStateException()
        }
    }

    override fun grantRights(fromUser: String, toUser: String, path: String, rights: Set<AccessRight>) {
        val parents: List<String> = run {
            if (path == "/") throw ShareException.BadRequest("Cannot grant rights on root")
            val parents = path.parents()

            parents.filter { it != "/" && it != "/home/" }
        }.map { translateAndCheckFile(it) }

        // Execute rights are required on all parent directories (otherwise we cannot perform the
        // traversal to the share)
        parents.forEach { fileACLService.createEntry(fromUser, toUser, it, setOf(AccessRight.EXECUTE)) }

        // Add to both the default and the actual list. This needs to be recursively applied
        val mountedPath = translateAndCheckFile(path)
        fileACLService.createEntry(fromUser, toUser, mountedPath, rights, defaultList = true, recursive = true)
        fileACLService.createEntry(fromUser, toUser, mountedPath, rights, defaultList = false, recursive = true)
    }

    override fun revokeRights(fromUser: String, toUser: String, path: String) {
        // TODO Need to look up share API to determine if we should delete execute rights on parent dirs
        val mountedPath = translateAndCheckFile(path)
        fileACLService.removeEntry(fromUser, toUser, mountedPath, defaultList = true, recursive = true)
        fileACLService.removeEntry(fromUser, toUser, mountedPath, defaultList = false, recursive = true)
    }

    private val duplicateNamingRegex = Regex("""\((\d+)\)""")
    override fun findFreeNameForNewFile(user: String, desiredPath: String): String {
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
        val names = ls(user, parentPath).map { it.path.fileName() }

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

    override fun homeDirectory(user: String): String {
        return "/home/$user/"
    }

    override fun listMetadataKeys(user: String, path: String): List<String> {
        return xAttrService.getAttributeList(user, path).keys.toList()
    }

    override fun getMetaValue(user: String, path: String, key: String): String {
        return xAttrService.getAttributeList(user, path)[key]
                ?: throw FileSystemException.NotFound("path: $path, key: $key")
    }

    override fun setMetaValue(user: String, path: String, key: String, value: String) {
        xAttrService.setAttribute(user, path, key, value)
    }

    private fun favoritesDirectory(user: String): String {
        return joinPath(homeDirectory(user), "Favorites", isDirectory = true)
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
