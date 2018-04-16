package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.api.*
import dk.sdu.cloud.storage.util.BashEscaper
import org.slf4j.LoggerFactory
import java.io.File
import java.io.OutputStream

class FileSystemService(
    private val cloudToCephFsDao: CloudToCephFsDao,
    private val isDevelopment: Boolean = false
) {
    fun ls(user: String, path: String, includeImplicit: Boolean = false): List<StorageFile> {
        val root = File(fsRoot).normalize().absolutePath
        val absolutePath = File(root, path).normalize().absolutePath

        if (!absolutePath.startsWith(root)) throw IllegalArgumentException("File is not in root")

        val cloudPath = File("/" + absolutePath.substringAfter(root).removePrefix("/"))

        val process = ProcessBuilder().apply {
            val prefix = asUser(user)
            val command = if (isDevelopment) listOf(File("./bin/osx/dirlisting").absolutePath) else listOf("dirlisting")

            directory(File(absolutePath))
            command(prefix + command)
        }.start()

        val status = process.waitFor()

        if (status != 0) {
            log.info("ls failed $user, $path")
            log.info(process.errorStream.reader().readText())
            throw IllegalStateException()
        } else {
            return parseDirListingOutput(cloudPath, process.inputStream.bufferedReader().readText(), includeImplicit)
        }
    }

    // TODO This is a bit lazy
    fun stat(user: String, path: String): StorageFile? {
        return try {
            val targetPath = File(path).normalize()
            val parentPath = targetPath.parentFile.absolutePath

            ls(user, parentPath, true).find { it.path == targetPath.absolutePath }
        } catch (ex: Exception) {
            null
        }
    }

    fun mkdir(user: String, path: String) {
        val root = File(fsRoot).normalize().absolutePath
        val absolutePath = File(root, path).normalize().absolutePath

        if (!absolutePath.startsWith(root)) throw IllegalArgumentException("File is not in root")

        val process = ProcessBuilder().apply {
            val prefix = asUser(user)
            val command = listOf("mkdir", "-p", absolutePath)
            command(prefix + command)
        }.start()

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

        val process = ProcessBuilder().apply {
            val prefix = asUser(user)
            val command = listOf("rm", "-rf", absolutePath)
            command(prefix + command)
        }.start()

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
        val process = ProcessBuilder().apply {
            val prefix = asUser(user)
            val command = listOf("mv", absolutePath, newAbsolutePath)
            command(prefix + command)
        }.start()

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
        val process = ProcessBuilder().apply {
            val prefix = asUser(user)
            val command = listOf("cp", absolutePath, newAbsolutePath)
            command(prefix + command)
        }.start()

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

        val process = ProcessBuilder().apply {
            val prefix = asUser(user)
            val command = listOf("cat", absolutePath)
            command(prefix + command)
        }.start()

        return process
    }

    fun write(user: String, path: String, writer: OutputStream.() -> Unit) {
        val root = File(fsRoot).normalize().absolutePath
        val absolutePath = File(root, path).normalize().absolutePath

        if (!absolutePath.startsWith(root)) throw IllegalArgumentException("File is not in root")

        val process = ProcessBuilder().apply {
            val prefix = asUser(user)
            val command = listOf("bash", "-c", "cat > ${BashEscaper.safeBashArgument(absolutePath)}")
            command(prefix + command)
        }.start()

        process.outputStream.writer()
        process.outputStream.close()
        if (process.waitFor() != 0) {
            log.info("write failed $user, $path")
            log.info(process.errorStream.reader().readText())
            throw IllegalStateException()
        }
    }

    fun parseDirListingOutput(where: File, output: String, includeImplicit: Boolean = false): List<StorageFile> {
        /*
        Example output:

        D,509,root,root,4096,1523862649,1523862649,1523862650,3,user1,14,user2,2,user3,6,CONFIDENTIAL,.
        D,493,root,root,4096,1523862224,1523862224,1523862237,0,CONFIDENTIAL,..
        F,420,root,root,0,1523862649,1523862649,1523862649,0,CONFIDENTIAL,qwe
        */

        return output.lines().mapNotNull { line ->
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
            val dirType = when (dirTypeToken) {
                "D" -> FileType.DIRECTORY
                "F" -> FileType.FILE
                "L" -> FileType.LINK
                else -> throw IllegalStateException("Unexpected dir type: $dirTypeToken")
            }

            val unixPermissions = readToken().toInt()

            val user = cloudToCephFsDao.findCloudUser(readToken()) ?: return@mapNotNull null
            val group = readToken() // TODO translate

            val size = readToken().toLong()
            val createdAt = readToken().toLong()
            val modifiedAt = readToken().toLong()
            val accessedAt = readToken().toLong()

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
                favorited = false,
                sensitivityLevel = sensitivity
            )
        }
    }

    private val fsRoot = if (isDevelopment) "./fs/" else "/mnt/cephfs/"

    private fun asUser(cloudUser: String): List<String> {
        val user = cloudToCephFsDao.findUnixUser(cloudUser) ?: throw IllegalStateException("Could not find user")
        return if (!isDevelopment) listOf("sudo", "-u", user) else emptyList()
    }

    companion object {
        private val log = LoggerFactory.getLogger(FileSystemService::class.java)

        private const val SHARED_WITH_UTYPE = 1
        private const val SHARED_WITH_READ = 2
        private const val SHARED_WITH_WRITE = 4
        private const val SHARED_WITH_EXECUTE = 8
    }
}

fun main(args: Array<String>) {
    val root = File("./fs").normalize().absolutePath
    val myPath = File(root, "/dir").normalize().absolutePath
    println(myPath)

}