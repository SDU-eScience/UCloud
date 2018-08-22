package dk.sdu.cloud.sync

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.client.HttpClient
import dk.sdu.cloud.client.asJson
import dk.sdu.cloud.client.setJsonBody
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import org.asynchttpclient.request.body.multipart.FilePart
import org.asynchttpclient.request.body.multipart.StringPart
import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarHeader
import org.kamranzafar.jtar.TarInputStream
import org.kamranzafar.jtar.TarOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.math.BigInteger
import java.nio.file.Files
import java.security.MessageDigest
import java.text.DecimalFormat
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.system.exitProcess

private val mapper = jacksonObjectMapper().apply {
    configure(JsonParser.Feature.ALLOW_COMMENTS, true)
    configure(SerializationFeature.INDENT_OUTPUT, true)
}
private val printStackTraces = false

data class SyncSettings(val token: String)

sealed class SyncResult {
    abstract val localPath: String
    abstract val remotePath: String

    data class LocalIsNewest(override val localPath: String, override val remotePath: String) : SyncResult()
    data class LocalIsOutdated(override val localPath: String, override val remotePath: String) : SyncResult()
    data class LocalIsInSync(override val localPath: String, override val remotePath: String) : SyncResult()
}

data class SyncItem(
    val fileType: String,
    val uniqueId: String,
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
    val uniqueId = readToken()
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
        uniqueId,
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

        // TODO This is probably not reliable (clock-skew). Maybe ask user?
        if (item.modifiedAt < localFile.lastModified()) {
            SyncResult.LocalIsNewest(localPath, item.path)
        } else {
            SyncResult.LocalIsOutdated(localPath, item.path)
        }
    }
}

data class AccessToken(val accessToken: String)

suspend fun auth(token: String): AccessToken? {
    val resp = HttpClient.post("https://cloud.sdu.dk/auth/refresh") {
        addHeader("Authorization", "Bearer $token")
    }

    return if (resp.statusCode in 200..399) {
        resp.asJson()
    } else {
        null
    }
}

fun printHelp() {
    println("Usage abc2-sync <targetDirectory> [--pull] [--push]")
    println()
    println("--pull: Pull files from SDUCloud to your current working directory")
    println("--push: Push files from your current working directory to SDUCloud")
    println()
    println("--auth: Perform authentication")
    println("--help: This message")
}

suspend fun runAbcSyncCli(args: Array<String>): Int {
    val shouldPull = args.contains("--pull")
    val shouldPush = args.contains("--push")
    val shouldAuth = args.contains("--auth")
    val shouldHelp = args.contains("--help")

    try {
        if (shouldHelp) {
            printHelp()
            return 0
        }

        if (shouldAuth) {
            println("Go to https://cloud.sdu.dk/auth/login?service=sync and login")
            println("Once you have authenticated you will be presented with an access token.")
            println()
            println("Enter your access token below:")
            val token = Scanner(System.`in`).nextLine()
            if (auth(token) == null) {
                println("Invalid token. Try again.")
                return 1
            }
            mapper.writeValue(File(System.getProperty("user.home"), ".sdu-sync"), SyncSettings(token))
            return 0
        }

        val workingDirectory = File(".").absoluteFile.normalize()
        val targetDirectory = args.firstOrNull() ?: run {
            printHelp()
            return 0
        }

        val settings = try {
            mapper.readValue<SyncSettings>(File(System.getProperty("user.home"), ".sdu-sync"))
        } catch (ignored: Exception) {
            throw IllegalStateException("Could not read settings", ignored)
        }

        val jwt = auth(settings.token)?.accessToken
            ?: throw IllegalStateException("Invalid token. Retry with abc2-sync --auth")

        data class SyncPayload(val path: String, val modifiedSince: Long)

        val response = HttpClient.post("https://cloud.sdu.dk/api/files/sync") {
            addHeader("Authorization", "Bearer $jwt")
            setJsonBody(SyncPayload(path = targetDirectory, modifiedSince = 0))
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

        if (shouldPull) {
            pullFilesFromRemote(allResults, targetDirectory, jwt, workingDirectory)
        }

        if (shouldPush) {
            pushFilesToRemote(allResults, workingDirectory, jwt, targetDirectory)
        }

        return 0
    } catch (ex: Exception) {
        if (printStackTraces) {
            ex.printStackTrace()
        } else {
            errPrintln(ex.message)
        }
        return 1
    }
}

fun main(args: Array<String>) {
    runBlocking { exitProcess(runAbcSyncCli(args)) }
}

private suspend fun pushFilesToRemote(
    allResults: List<SyncResult>,
    workingDirectory: File,
    jwt: String,
    targetDirectory: String
) {
    var filesAdded = 0
    var foundAny = false
    val uploadFile = Files.createTempFile("upload", ".tar.gz").toFile()
    TarOutputStream(GZIPOutputStream(FileOutputStream(uploadFile))).use { out ->
        val isUpToDate = allResults
            .filter { it is SyncResult.LocalIsInSync || it is SyncResult.LocalIsOutdated }
            .map { it.localPath }
            .toSet()

        Files.walk(workingDirectory.toPath())
            .filter {
                val toFile = it.toFile()
                toFile != workingDirectory.normalize() &&
                        toFile.absolutePath !in isUpToDate
            }
            .forEach {

                val file = it.toFile()
                val localPath = file.relativeTo(workingDirectory).path

                if (file.isDirectory) {
                    out.putNextEntry(
                        TarEntry(
                            TarHeader.createHeader(
                                localPath, 0, 0, true, 511
                            )
                        )
                    )
                } else {
                    foundAny = true
                    filesAdded++

                    out.putNextEntry(
                        TarEntry(
                            TarHeader.createHeader(
                                localPath, file.length(), 0, false, 511
                            )
                        )
                    )

                    file.inputStream().copyTo(out)
                }
            }
    }

    if (foundAny) {
        println("Uploading files ($filesAdded files)")
        val result = HttpClient.post("https://cloud.sdu.dk/api/upload/bulk") {
            addHeader("Authorization", "Bearer $jwt")
            addBodyPart(StringPart("policy", "OVERWRITE"))
            addBodyPart(StringPart("path", targetDirectory))
            addBodyPart(StringPart("format", "tgz"))
            addBodyPart(FilePart("upload", uploadFile))
        }

        if (result.statusCode !in 200..399) {
            debug(result.statusCode)
            debug(result.responseBody)
            throw IllegalStateException("Bad server response")
        }
    }
}

private suspend fun pullFilesFromRemote(
    allResults: List<SyncResult>,
    targetDirectory: String,
    jwt: String,
    workingDirectory: File
) {
    data class BulkDownloadRequest(val prefix: String, val files: List<String>)

    val filesToDownload = allResults.filterIsInstance<SyncResult.LocalIsOutdated>()
        .map { it.remotePath.removePrefix(targetDirectory) }

    val downloadResponse = HttpClient.post("https://cloud.sdu.dk/api/files/bulk") {
        addHeader("Authorization", "Bearer $jwt")
        setJsonBody(BulkDownloadRequest(targetDirectory, filesToDownload))
    }

    if (downloadResponse.statusCode !in 200..299) throw IllegalStateException("Bad server response")

    TarInputStream(GZIPInputStream(downloadResponse.responseBodyAsStream)).use {
        val buffer = ByteArray(8 * 1024)
        var mutableEntry: TarEntry? = it.nextEntry
        while (mutableEntry != null) {
            val currentEntry = mutableEntry
            println("Downloading ${currentEntry.name} (${currentEntry.size} bytes)")

            val file = File(workingDirectory, currentEntry.name)

            if (currentEntry.isDirectory) {
                file.mkdirs()
            } else {
                file.parentFile.mkdirs()

                val fileOut = file.outputStream()

                val bar = ProgressBar().apply {
                    current = 0
                    maximum = currentEntry.size
                    message = currentEntry.name
                }

                bar.render()

                CappedInputStream(it, currentEntry.size).use {
                    var bytes = it.read(buffer)
                    if (bytes > 0) bar.current += bytes
                    bar.render()
                    while (bytes >= 0) {
                        fileOut.write(buffer, 0, bytes)
                        bytes = it.read(buffer)
                        if (bytes > 0) bar.current += bytes
                        bar.render()
                    }
                }
                println()
                println()
            }
            mutableEntry = it.nextEntry
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

class ProgressBar {
    var current: Long = 0
    var maximum: Long = 100
    var message: String = ""
    var terminalSize = 80

    var stream: PrintStream = System.out

    fun render() {
        val percentage = if (maximum == 0L) 1.0 else current / maximum.toDouble()
        val format = DecimalFormat("#0.00")
        val percentageString = format.format(percentage * 100)
        stream.print('\r')
        when {
            terminalSize >= 80 -> {
                val spaces = 40
                val filledSpaces = (percentage * spaces).toInt()
                val bar = String(CharArray(spaces) {
                    when {
                        it == filledSpaces - 1 && it != spaces - 1 -> '>'
                        it < filledSpaces -> '='
                        else -> ' '
                    }
                })
                val message = "[$bar] $percentageString% $message"
                stream.print(message)
                if (message.length < terminalSize) {
                    stream.print(String(CharArray(terminalSize - message.length) { ' ' }))
                }
            }

            else -> {
                stream.print("$percentageString%")
            }
        }
    }
}