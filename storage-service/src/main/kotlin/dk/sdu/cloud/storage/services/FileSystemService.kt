package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.api.*
import dk.sdu.cloud.storage.util.BashEscaper
import org.slf4j.LoggerFactory
import java.io.File
import java.io.OutputStream
import java.lang.Math.abs
import java.util.*

data class FavoritedFile(val type: FileType, val from: String, val to: String, val inode: Long)

class FileSystemService(
    private val cloudToCephFsDao: CloudToCephFsDao,
    private val isDevelopment: Boolean = false
) {
    private fun runAsUser(user: String, command: List<String>, directory: String? = null): Process {
        return ProcessBuilder().apply {
            val prefix = asUser(user)

            val bashCommand = if (directory == null) {
                command.joinToString(" ") { BashEscaper.safeBashArgument(it) }
            } else {
                "cd ${BashEscaper.safeBashArgument(directory)} ; " +
                        command.joinToString(" ") { BashEscaper.safeBashArgument(it) }
            }

            val wrappedCommand = listOf("bash", "-c", bashCommand)
            log.info(wrappedCommand.toString())
            command(prefix + wrappedCommand)
        }.start()
    }

    private val dirListingExecutable: String
        get() = if (isDevelopment) File("./bin/osx/dirlisting").absolutePath else "dirlisting"

    fun ls(
        user: String,
        path: String,
        includeImplicit: Boolean = false,
        includeFavorites: Boolean = true
    ): List<StorageFile> {
        val absolutePath = File(fsRoot, path).normalize().absolutePath

        if (!absolutePath.startsWith(fsRoot)) throw IllegalArgumentException("File is not in root")

        val cloudPath = File("/" + absolutePath.substringAfter(fsRoot).removePrefix("/"))

        val command = mutableListOf(dirListingExecutable)
        if (includeFavorites) command += listOf("--fav", translateAndCheckFile(favoritesDirectory(user), true))
        val process = runAsUser(user, command, absolutePath)

        val status = process.waitFor()

        if (status != 0) {
            log.info("ls failed $user, $path")
            log.info(process.errorStream.reader().readText())
            throw IllegalStateException()
        } else {
            return parseDirListingOutput(
                cloudPath,
                process.inputStream.bufferedReader().readText(),
                includeImplicit,
                includeFavorites
            ).second
        }
    }

    fun favorites(
        user: String
    ): List<FavoritedFile> {
        val command = mutableListOf(
            dirListingExecutable,
            "--fav",
            translateAndCheckFile(favoritesDirectory(user), true),
            "--just-fav"
        )

        val process = runAsUser(user, command)
        return parseDirListingOutput(
            File(translateAndCheckFile(homeDirectory(user))),
            process.inputStream.bufferedReader().readText(),
            false,
            true
        ).first
    }

    // TODO This is a bit lazy
    fun stat(user: String, path: String): StorageFile? {
        return try {
            val results = ls(user, path.removeSuffix("/").substringBeforeLast('/'), true, false)
            results.find { it.path == path }
        } catch (ex: Exception) {
            null
        }
    }

    fun mkdir(user: String, path: String) {
        val root = File(fsRoot).normalize().absolutePath
        val absolutePath = File(root, path).normalize().absolutePath

        if (!absolutePath.startsWith(root)) throw IllegalArgumentException("File is not in root")

        val process = runAsUser(user, listOf("mkdir", "-p", absolutePath))
        val status = process.waitFor()

        if (status != 0) {
            log.info("mkdir failed $user, $path")
            log.info(process.errorStream.reader().readText())
            throw IllegalStateException()
        }
    }

    fun rmdir(user: String, path: String) {
        val root = File(fsRoot).normalize().absolutePath
        val absolutePath = File(root, path).normalize().absolutePath

        if (!absolutePath.startsWith(root)) throw IllegalArgumentException("File is not in root")

        val process = runAsUser(user, listOf("rm", "-rf", absolutePath))

        val status = process.waitFor()

        if (status != 0) {
            log.info("rmdir failed $user, $path")
            log.info(process.errorStream.reader().readText())
            throw IllegalStateException()
        }
    }

    fun move(user: String, path: String, newPath: String) {
        val root = File(fsRoot).normalize().absolutePath
        val absolutePath = File(root, path).normalize().absolutePath
        val newAbsolutePath = File(root, newPath).normalize().absolutePath

        if (!absolutePath.startsWith(root)) throw IllegalArgumentException("File is not in root")
        if (!newAbsolutePath.startsWith(root)) throw IllegalArgumentException("File is not in root")

        val process = runAsUser(user, listOf("mv", absolutePath, newAbsolutePath))
        val status = process.waitFor()

        if (status != 0) {
            log.info("mv failed $user, $path")
            log.info(process.errorStream.reader().readText())
            throw IllegalStateException()
        }
    }

    fun copy(user: String, path: String, newPath: String) {
        val root = File(fsRoot).normalize().absolutePath
        val absolutePath = File(root, path).normalize().absolutePath
        val newAbsolutePath = File(root, newPath).normalize().absolutePath

        if (!absolutePath.startsWith(root)) throw IllegalArgumentException("File is not in root")
        if (!newAbsolutePath.startsWith(root)) throw IllegalArgumentException("File is not in root")
        val process = runAsUser(user, listOf("cp", "-r", absolutePath, newAbsolutePath))

        val status = process.waitFor()

        if (status != 0) {
            log.info("cp failed $user, $path")
            log.info(process.errorStream.reader().readText())
            throw IllegalStateException()
        }
    }

    fun read(user: String, path: String): Process {
        val root = File(fsRoot).normalize().absolutePath
        val absolutePath = File(root, path).normalize().absolutePath

        if (!absolutePath.startsWith(root)) throw IllegalArgumentException("File is not in root")

        return runAsUser(user, listOf("cat", absolutePath))
    }

    fun write(user: String, path: String, writer: OutputStream.() -> Unit) {
        val root = File(fsRoot).normalize().absolutePath
        val absolutePath = File(root, path).normalize().absolutePath

        if (!absolutePath.startsWith(root)) throw IllegalArgumentException("File is not in root")

        val process = runAsUser(user, listOf("bash", "-c", "cat > ${BashEscaper.safeBashArgument(absolutePath)}"))

        process.outputStream.writer()
        process.outputStream.close()
        if (process.waitFor() != 0) {
            log.info("write failed $user, $path")
            log.info(process.errorStream.reader().readText())
            throw IllegalStateException()
        }
    }

    fun parseDirListingOutput(
        where: File,
        output: String,
        includeImplicit: Boolean = false,
        parseFavorites: Boolean = false
    ): Pair<List<FavoritedFile>, List<StorageFile>> {
        /*
        Example output:

        D,509,root,root,4096,1523862649,1523862649,1523862650,3,user1,14,user2,2,user3,6,CONFIDENTIAL,.
        D,493,root,root,4096,1523862224,1523862224,1523862237,0,CONFIDENTIAL,..
        F,420,root,root,0,1523862649,1523862649,1523862649,0,CONFIDENTIAL,qwe
        */

        fun parseDirType(token: String): FileType = when (token) {
            "D" -> FileType.DIRECTORY
            "F" -> FileType.FILE
            "L" -> FileType.LINK
            else -> throw IllegalStateException("Bad type from favorites section: $token, $output")
        }

        val rawLines = output.lines()
        val (favoriteLines, outputLines) = if (parseFavorites) {
            val linesToTake = rawLines.first().toInt()
            log.info("Taking $linesToTake")

            Pair(rawLines.take(linesToTake * 4 + 1).drop(1), rawLines.drop(linesToTake * 4 + 1))
        } else {
            Pair(emptyList(), rawLines)
        }

        val favorites = if (parseFavorites) {
            if (favoriteLines.size % 4 != 0) {
                throw IllegalStateException("Bad output from favorites section: $output")
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
            if (!includeImplicit && (fileName == "." || fileName == "..")) return@mapNotNull null
            val filePath = File(where, fileName).normalize().absolutePath

            // Don't attempt to return details about the parent of mount
            if (filePath == "/..") return@mapNotNull null

            StorageFile(
                type = dirType,
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

    fun createSoftSymbolicLink(user: String, linkFile: String, pointsTo: String) {
        val absLinkPath = translateAndCheckFile(linkFile)
        val absPointsToPath = translateAndCheckFile(pointsTo)

        // We only need to check target, the rest will be enforced. Ideally we wouldn't do this as two forks,
        // but can work for prototypes. TODO Performance
        if (stat(user, pointsTo) == null) {
            throw IllegalArgumentException("Cannot point to target")
        }

        val process = runAsUser(user, listOf("ln", "-s", absPointsToPath, absLinkPath))
        val status = process.waitFor()
        if (status != 0) {
            log.info("ln failed $user, $absLinkPath $absPointsToPath")
            log.info(process.errorStream.reader().readText())
            throw IllegalStateException()
        }
    }

    fun createFavorite(user: String, fileToFavorite: String) {
        // TODO Hack, but highly unlikely that we will have duplicates in practice.
        // TODO Create favorites folder if it does not exist yet
        val suffix = abs(random.nextInt()).toString(16)
        val targetLocation = joinPath(favoritesDirectory(user), fileToFavorite.fileName() + ".$suffix")

        createSoftSymbolicLink(user, targetLocation, fileToFavorite)
    }

    fun removeFavorite(user: String, favoriteFileToRemove: String) {
        val stat = stat(user, favoriteFileToRemove) ?: throw IllegalStateException()
        val allFavorites = favorites(user)
        val toRemove = allFavorites.filter { it.inode == stat.inode }
        if (toRemove.isEmpty()) return
        val command = listOf("rm") + toRemove.map { it.from }
        val process = runAsUser(user, command)
        val status = process.waitFor()
        if (status != 0) {
            log.info("rm failed $user")
            log.info(process.errorStream.reader().readText())
            throw IllegalStateException()
        }
    }

    private fun joinPath(vararg components: String, isDirectory: Boolean = false): String {
        return components.joinToString("/") + (if (isDirectory) "/" else "")
    }

    private fun homeDirectory(user: String): String {
        return "/home/$user/"
    }

    private fun favoritesDirectory(user: String): String {
        return joinPath(homeDirectory(user), "Favorites", isDirectory = true)
    }

    private fun String.fileName(): String = File(this).name

    private fun translateAndCheckFile(internalPath: String, isDirectory: Boolean = false): String {
        return File(fsRoot, internalPath)
            .normalize()
            .absolutePath
            .takeIf { it.startsWith(fsRoot) }?.let { it + (if (isDirectory) "/" else "") }
                ?: throw IllegalArgumentException("path is not in root ($internalPath)")
    }

    private val fsRoot = File(if (isDevelopment) "./fs/" else "/mnt/cephfs/").normalize().absolutePath

    private fun asUser(cloudUser: String): List<String> {
        val user = cloudToCephFsDao.findUnixUser(cloudUser) ?: throw IllegalStateException("Could not find user")
        return if (!isDevelopment) listOf("sudo", "-u", user) else emptyList()
    }

    companion object {
        private val log = LoggerFactory.getLogger(FileSystemService::class.java)
        private val random = Random()

        private const val SHARED_WITH_UTYPE = 1
        private const val SHARED_WITH_READ = 2
        private const val SHARED_WITH_WRITE = 4
        private const val SHARED_WITH_EXECUTE = 8
    }
}
