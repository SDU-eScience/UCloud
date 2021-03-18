package dk.sdu.cloud.utils

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.O_RDONLY
import platform.posix.O_WRONLY
import platform.posix.getenv

class NativeFile(val fd: Int) {
    init {
        require(fd >= 0) { "fd < 0" }
    }

    fun close() {
        platform.posix.close(fd)
    }

    companion object {
        fun open(path: String, readOnly: Boolean): NativeFile {
            val fd = platform.posix.open(path, O_RDONLY or if (readOnly) 0 else O_WRONLY)
            if (fd < 0) throw IllegalArgumentException("Could not open file: $path")
            return NativeFile(fd)
        }
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
fun NativeFile.readText(
    charLimit: Long = 1024 * 1024 * 32,
    bufferSize: Int = 1024 * 32,
    autoClose: Boolean = true,
): String {
    val builder = StringBuilder()
    ByteArray(bufferSize).usePinned { buf ->
        while (true) {
            val read = platform.posix.read(fd, buf.addressOf(0), bufferSize.toULong())
            require(read >= 0) { "Exception stuff" }
            if (read == 0L) break
            // technically won't work if we end the read on the wrong byte
            builder.append(buf.get().decodeToString(0, read.toInt()))
            if (builder.length >= charLimit) throw IllegalStateException("Too long message")
        }
    }
    if (autoClose) close()
    return builder.toString()
}

fun homeDirectory(): String {
    val unixHome = getenv("HOME")?.toKString()
    if (unixHome != null) return unixHome
    val userProfile = getenv("USERPROFILE")?.toKString()
    if (userProfile != null) return userProfile
    val homeDrive = getenv("HOMEDRIVE")?.toKString()
    val homePath = getenv("HOMEPATH")?.toKString()
    if (homeDrive != null && homePath != null) return homeDrive + homePath

    throw IllegalStateException("Could not find home directory of user")
}
