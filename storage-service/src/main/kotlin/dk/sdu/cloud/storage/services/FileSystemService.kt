package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.storage.api.StorageFile
import dk.sdu.cloud.storage.util.BashEscaper
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime


class FileSystemService(
    private val cloudToCephFsDao: CloudToCephFsDao,
    private val isDevelopment: Boolean = false
) {
    fun ls(user: String, path: String): List<StorageFile> {
        /*
        val root = File(fsRoot).normalize().absolutePath
        val absolutePath = File(root, path).normalize().absolutePath

        if (!absolutePath.startsWith(root)) throw IllegalArgumentException("File is not in root")

        val process = ProcessBuilder().apply {
            val prefix = asUser(user)
            val command = listOf("ls", "-1Al", "--time-style=\"+%Y-%m-%dT%H:%M:%S%:z\"", BashEscaper.safeBashArgument(absolutePath))

            command(prefix + command)
        }.start()

        val status = process.waitFor()

        if (status != 0) {
            log.info("ls failed $user, $path")
            log.info(process.errorStream.reader().readText())
            throw IllegalStateException()
        }
        */

        //val files = process.inputStream.reader().readLines().drop(1)
        val files = """
            -rw-r--r--. 1 root    root    0 2018-04-11T14:16:47+02:00 annoying file
            drwxr-xr-x. 2 root    root    6 2018-04-11T14:19:19+02:00 f
            -rw-r--r--. 1 dthrane wheel 173 2018-04-11T12:59:17+02:00 README.md
            -rw-r--r--. 1 root    root    0 2018-04-11T14:25:41+02:00 really annoying   file
            -rwxr-xr-x. 1 dthrane wheel 172 2018-04-11T12:59:17+02:00 sduls
            -rwxr-xr-x. 1 dthrane wheel 139 2018-04-11T12:39:37+02:00 sduls.sh
            -rw-r--r--. 1 root    root   88 2018-04-11T14:27:09+02:00 test.txt
            -rwxr-xr-x. 1 dthrane wheel 157 2018-04-11T12:59:17+02:00 utils.sh
        """.trimIndent().split("\n")

        return files.mapNotNull { line ->
            val chars = line.toCharArray()

            var cursor = 0
            fun readToken(): String {
                val builder = StringBuilder()
                var hasFoundSpace = false
                while (cursor < chars.size) {
                    val next = chars[cursor++]
                    if (next != ' ') {
                        if (hasFoundSpace) {
                            cursor--
                            break
                        }
                        builder.append(next)
                    } else {
                        hasFoundSpace = true
                    }
                }
                return builder.toString()
            }

            val isDirectory = readToken().startsWith('d')
            readToken() // discard ?
            val owner = readToken()
            readToken() // discard group
            val fileSize = readToken().toLongOrNull() ?: return@mapNotNull null
            val parsedDate = OffsetDateTime.parse(readToken())
            val name = line.substring(cursor)

            StorageFile(
                if (isDirectory) FileType.DIRECTORY else FileType.FILE,
                name,
                0L,
                parsedDate.toEpochSecond() * 1000L,
                owner,
                fileSize
            )
        }
    }

    private val fsRoot = if (isDevelopment) "./fs/" else "/mnt/cephfs/"

    private fun asUser(user: String): List<String> =
        if (!isDevelopment) listOf("sudo", "-u", BashEscaper.safeBashArgument(user))
        else emptyList()

    companion object {
        private val log = LoggerFactory.getLogger(FileSystemService::class.java)
    }
}

fun main(args: Array<String>) {
    val service = FileSystemService(CloudToCephFsDao(), true)
    service.ls("me", ".").forEach { println(it) }
}