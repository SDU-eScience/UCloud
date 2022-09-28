package dk.sdu.cloud.utils

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.orchestrator.api.fileName
import io.ktor.utils.io.core.*
import io.ktor.utils.io.streams.*
import libc.O_TRUNC
import libc.O_WRONLY
import libc.clib
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class NativeFile private constructor(
    val jvmFile: File,
    val input: InputStream?,
    val output: OutputStream?,
) {
    fun close(): Int {
        runCatching { input?.close() }
        runCatching { output?.close() }
        return 0
    }

    companion object {
        fun open(
            path: String,
            readOnly: Boolean,
            createIfNeeded: Boolean = true,
            truncateIfNeeded: Boolean = false,
            mode: Int? = "640".toInt(8),
        ): NativeFile {
            val file = File(path)
            val input = if (readOnly) file.inputStream() else null
            val output = if (!readOnly) FileOutputStream(file, !truncateIfNeeded) else null

            if (!readOnly && mode != null) {
                if (clib.chmod(path, mode) != 0) throw IOException("chmod failed for $path")
            }

            return NativeFile(file, input, output)
        }

        fun open(
            path: String,
            flags: Int,
            mode: Int = "640".toInt(8),
        ): NativeFile {
            val readOnly = flags and O_WRONLY == 0
            val truncate = flags and O_TRUNC != 0
            return open(path, truncateIfNeeded = truncate, readOnly = readOnly, mode = mode)
        }
    }
}

fun NativeFile.readText(
    charLimit: Long = 1024 * 1024 * 32L,
    bufferSize: Int = 1024 * 32,
    autoClose: Boolean = true,
    allowLongMessage: Boolean = false,
): String {
    require(!allowLongMessage || !autoClose) { "allowLongMessage can only be true if autoClose = false" }

    try {
        val builder = StringBuilder()
        val input = input ?: error("File ${jvmFile.absolutePath} was not opened for reading but readText() was invoked")

        val buf = ByteArray(bufferSize)
        while (true) {
            val read = input.read(buf)
            if (read <= 0) break
            // technically won't work if we end the read on the wrong byte
            builder.append(buf.decodeToString(0, read))
            if (builder.length >= charLimit) {
                if (allowLongMessage) break
                else throw IllegalStateException("Too long message")
            }
        }
        return builder.toString()
    } finally {
        if (autoClose) close()
    }
}

fun NativeFile.writeText(
    text: String,
    autoClose: Boolean = true,
) {
    writeData(text.encodeToByteArray(), autoClose)
}

fun NativeFile.writeData(
    data: ByteArray,
    autoClose: Boolean = false,
) {
    val output = output ?: error("File ${jvmFile.absolutePath} was not opened for writing but writeData() was invoked")
    try {
        output.write(data)
    } finally {
        if (autoClose) close()
    }
}

fun getNativeErrorMessage(error: Int): String {
    return "Native error $error"
}

class NativeFileException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

fun homeDirectory(): String {
    val unixHome = System.getenv("HOME")
    if (unixHome != null) return unixHome
    val userProfile = System.getenv("USERPROFILE")
    if (userProfile != null) return userProfile
    val homeDrive = System.getenv("HOMEDRIVE")
    val homePath = System.getenv("HOMEPATH")
    if (homeDrive != null && homePath != null) return homeDrive + homePath

    throw IllegalStateException("Could not find home directory of user")
}

fun fileExists(path: String): Boolean = File(path).exists()
fun fileIsExecutable(path: String): Boolean = Files.isExecutable(File(path).toPath())
fun fileIsDirectory(path: String): Boolean = Files.isDirectory(File(path).toPath())
fun fileDelete(path: String) {
    File(path).delete()
}

fun listFiles(internalFile: String): List<String> {
    return File(internalFile).listFiles()?.map { it.absolutePath } ?:
        throw RPCException("File not found: ${internalFile.fileName()}", HttpStatusCode.NotFound)
}

fun renameFile(from: String, to: String) {
    File(from).renameTo(File(to))
}

// TODO(Dan): We definitely need something more roboust than assuming that /tmp is usable.
private val temporaryFilePrefix = secureToken(16).replace("/", "-")
private val temporaryFileAcc = AtomicInteger(0)

data class TemporaryFile(val internalFile: String, val fileHandle: NativeFile)

fun createTemporaryFile(prefix: String? = null, suffix: String? = null): TemporaryFile {
    val path = "/tmp/${prefix ?: ""}${temporaryFilePrefix}${temporaryFileAcc.getAndIncrement()}${suffix ?: ""}"
    return TemporaryFile(
        path,
        NativeFile.open(
            path,
            readOnly = false,
            truncateIfNeeded = true,
            createIfNeeded = true,
            mode = "600".toInt(8)
        )
    )
}
