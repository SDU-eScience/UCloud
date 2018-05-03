package dk.sdu.cloud.sync

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.client.HttpClient
import dk.sdu.cloud.client.setJsonBody
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarInputStream
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.util.*
import java.util.zip.GZIPInputStream
import kotlin.system.exitProcess

private val mapper = jacksonObjectMapper().apply {
    configure(JsonParser.Feature.ALLOW_COMMENTS, true)
    configure(SerializationFeature.INDENT_OUTPUT, true)
}
private val printStackTraces = true

data class SyncSettings(val token: String)

const val SDU_SYNCMETA = ".sdu-syncmeta"

sealed class SyncResult {
    abstract val localPath: String
    abstract val remotePath: String

    data class LocalIsNewest(override val localPath: String, override val remotePath: String) : SyncResult()
    data class LocalIsOutdated(override val localPath: String, override val remotePath: String) : SyncResult()
    data class LocalIsInSync(override val localPath: String, override val remotePath: String) : SyncResult()
}

data class SyncItem(
    val fileType: String,
    val user: String,
    val modifiedAt: Long,
    val checksum: String?,
    val checksumType: String?,
    val path: String
)

fun parseSyncItem(syncLine: String): SyncItem {
    var cursor = 0
    val chars = syncLine.toCharArray()
    fun readToken(): String {
        val builder = StringBuilder()
        while (cursor < chars.size) {
            val c = chars[cursor++]
            if (c == ',') break
            builder.append(c)
        }
        return builder.toString()
    }

    val fileType = readToken()
    val user = readToken()
    val modifiedAt = readToken().toLong()
    val hasChecksum = when (readToken()) {
        "0" -> false
        "1" -> true
        else -> throw IllegalStateException("Bad server response")
    }

    val checksum = if (hasChecksum) readToken() else null
    val checksumType = if (hasChecksum) readToken() else null

    val path = syncLine.substring(cursor)

    return SyncItem(
        fileType,
        user,
        modifiedAt,
        checksum,
        checksumType,
        path
    )
}

fun compareWithLocal(workingDirectory: File, targetDirectory: String, item: SyncItem): SyncResult {
    debug("compareWithLocal($workingDirectory, $targetDirectory, $item)")

    if (!item.path.startsWith(targetDirectory)) throw IllegalStateException()
    val relativeToTarget = item.path.removePrefix(targetDirectory)
    val localFile = File(workingDirectory, relativeToTarget).normalize()
    val localPath = localFile.absolutePath

    if (item.checksum == null || item.checksumType == null) return SyncResult.LocalIsOutdated(localPath, item.path)
    if (item.checksumType != "sha1") throw IllegalStateException("Unsupported checksum type")

    if (!localFile.exists()) return SyncResult.LocalIsOutdated(localPath, item.path)

    val sha1Digest = MessageDigest.getInstance("SHA1")
            ?: throw IllegalStateException("Unable to compute checksums (Not supported)")
    val localBuffer = ByteArray(1024 * 1024)

    val isDirectory = localFile.isDirectory

    // Need to handle local is dir and remote is not
    val checksum = if (isDirectory) {
        ""
    } else {
        val inputStream = localFile.inputStream()
        inputStream.use {
            while (true) {
                val read = inputStream.read(localBuffer)
                if (read == -1) break
                sha1Digest.update(localBuffer, 0, read)
            }
        }
        sha1Digest.digest().toHexString()
    }

    return if (checksum.equals(item.checksum, ignoreCase = true)) {
        SyncResult.LocalIsInSync(localPath, item.path)
    } else {
        debug(
            "Item does not match: Remote modified at ${item.modifiedAt}. " +
                    "Local modified at ${localFile.lastModified()}"
        )

        // TODO This is probably not reliable. Maybe ask user?
        if (item.modifiedAt < localFile.lastModified()) {
            SyncResult.LocalIsNewest(localPath, item.path)
        } else {
            SyncResult.LocalIsOutdated(localPath, item.path)
        }
    }
}

fun main(args: Array<String>) {
    runBlocking {
        try {
            val workingDirectory = File(".").absoluteFile.normalize()
            val targetDirectory = args.firstOrNull() ?: throw IllegalArgumentException("Missing target directory")

            val settings = try {
                mapper.readValue<SyncSettings>(File(System.getProperty("user.home"), ".sdu-sync"))
            } catch (ignored: Exception) {
                throw IllegalStateException("Could not read settings", ignored)
            }

            val meta = try {
                mapper.readValue<PullMeta>(File(SDU_SYNCMETA))
            } catch (ignored: Exception) {
                null
            }

            data class SyncPayload(val path: String, val modifiedSince: Long)

            val response = HttpClient.post("https://cloud.sdu.dk/api/files/sync") {
                //            addHeader("Job-Id", UUID.randomUUID().toString())
                addHeader("Authorization", "Bearer ${settings.token}")
                setJsonBody(
                    SyncPayload(
                        path = targetDirectory,
                        modifiedSince = meta?.lastSynchronizedAt ?: 0
                    )
                )
            }

            if (response.statusCode !in 200..299) {
                throw IllegalStateException("Server error. ${response.statusCode} ${response.responseBody}")
            }

            val jobs = ArrayList<Deferred<SyncResult>>()
            response.responseBodyAsStream.bufferedReader().use {
                var line: String? = it.readLine()
                var lines = 0
                while (line != null) {
                    val lineCopy: String = line
                    debug(lineCopy)
                    val item = parseSyncItem(lineCopy)
                    if (item.fileType == "F") {
                        jobs += async {
                            compareWithLocal(workingDirectory, targetDirectory, item)
                        }
                    }
                    line = it.readLine()
                    lines++
                }

                debug("Processed $lines lines")
            }

            val allResults = jobs.map { it.await() }

            val filesToDownload = allResults.filterIsInstance<SyncResult.LocalIsOutdated>()
                .map { it.remotePath.removePrefix(targetDirectory) }

            data class BulkDownloadRequest(val prefix: String, val files: List<String>)

            val downloadResponse = HttpClient.post("https://cloud.sdu.dk/api/files/bulk") {
                addHeader("Authorization", "Bearer ${settings.token}")
                setJsonBody(BulkDownloadRequest(targetDirectory, filesToDownload))
            }

            if (downloadResponse.statusCode !in 200..299) throw IllegalStateException("Bad server response")

            TarInputStream(GZIPInputStream(downloadResponse.responseBodyAsStream)).use {
                var entry: TarEntry? = it.nextEntry
                while (entry != null) {
                    println("Downloading ${entry.name} (${entry.size} bytes)")

                    val fileOut = File(workingDirectory, entry.name).outputStream()
                    CappedInputStream(it, entry.size).copyTo(fileOut)
                    entry = it.nextEntry
                }
            }

            mapper.writeValue(File(SDU_SYNCMETA), PullMeta(System.currentTimeMillis()))
            exitProcess(0)
        } catch (ex: Exception) {
            if (printStackTraces) {
                ex.printStackTrace()
            } else {
                errPrintln(ex.message)
            }
            exitProcess(1)
        }
    }
}

fun errPrintln(message: Any?) {
    System.err.println(message)
}

fun debug(message: Any?) {
    if (printStackTraces) {
        errPrintln(message)
    }
}

fun ByteArray.toHexString(): String {
    val bi = BigInteger(1, this)
    val hex = bi.toString(16)
    val paddingLength = this.size * 2 - hex.length
    return if (paddingLength > 0) {
        String.format("%0" + paddingLength + "d", 0) + hex
    } else {
        hex
    }
}

data class PullMeta(val lastSynchronizedAt: Long)