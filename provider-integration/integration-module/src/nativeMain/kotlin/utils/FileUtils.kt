package dk.sdu.cloud.utils

import dk.sdu.cloud.renameat2_kt
import kotlinx.cinterop.*
import platform.posix.*

class NativeFile(val fd: Int) {
    init {
        require(fd >= 0) { "fd < 0" }
    }

    fun close() {
        platform.posix.close(fd)
    }

    companion object {
        fun open(
            path: String,
            readOnly: Boolean,
            createIfNeeded: Boolean = true,
            truncateIfNeeded: Boolean = false,
            mode: Int = "640".toInt(8),
        ): NativeFile {
            var flags = O_RDONLY
            if (!readOnly) {
                flags = flags or O_WRONLY
                if (createIfNeeded) flags = flags or O_CREAT
                if (truncateIfNeeded) flags = flags or O_TRUNC
            }

            val fd = platform.posix.open(path, flags, mode)
            if (fd < 0) throw NativeFileException("Could not open file: $path (${getNativeErrorMessage(errno)}")
            return NativeFile(fd)
        }

        fun open(
            path: String,
            flags: Int,
            mode: Int = "640".toInt(8),
        ): NativeFile {
            val fd = platform.posix.open(path, flags, mode)
            if (fd < 0) throw NativeFileException("Could not open file: $path (${getNativeErrorMessage(errno)}")
            return NativeFile(fd)
        }
    }
}

fun NativeFile.readText(
    charLimit: Long = 1024 * 1024 * 32,
    bufferSize: Int = 1024 * 32,
    autoClose: Boolean = true,
): String {
    try {
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
        return builder.toString()
    } finally {
        if (autoClose) close()
    }
}

fun NativeFile.writeText(
    text: String,
    autoClose: Boolean = true,
) {
    try {
        val encoded = text.encodeToByteArray()
        var ptr = 0
        encoded.usePinned { pinned ->
            while (ptr < encoded.size) {
                val written = platform.posix.write(fd, pinned.addressOf(ptr), encoded.size.toULong() - ptr.toULong())
                if (written < 0) {
                    throw NativeFileException(getNativeErrorMessage(errno))
                }
                ptr += written.toInt()
            }
        }
    } finally {
        if (autoClose) close()
    }
}

fun getNativeErrorMessage(error: Int): String {
    val messageBuffer = ByteArray(1024)
    messageBuffer.usePinned { pin ->
        if (strerror_r(error, pin.addressOf(0), messageBuffer.size.toULong()) != 0) {
            return "Failed to retrieve error message: $error"
        }
    }
    return messageBuffer.toKString()
}

class NativeFileException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

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


fun fileExists(path: String): Boolean = memScoped {
    val st = alloc<stat>()
    return stat(path, st.ptr) == 0
}

fun fileIsDirectory(path: String): Boolean = memScoped {
    val st = alloc<stat>()
    return stat(path, st.ptr) == 0 && st.st_mode and S_IFDIR.toUInt() != 0u
}

fun renameFile(from: String, to: String, flags: UInt) {
    val fromDir = from.substringBeforeLast('/')
    val toDir = from.substringBeforeLast('/')
    val fromName = from.substringAfterLast('/')
    val toName = to.substringAfterLast('/')

    val fromFd = NativeFile.open(fromDir, flags = O_PATH)
    val toFd = if (fromDir == toDir) fromFd else NativeFile.open(toDir, flags = O_PATH)

    try {
        val result = renameat2_kt(fromFd.fd, fromName, toFd.fd, toName, flags)
        if (result != 0) {
            throw NativeFileException("renameFile failed: ${getNativeErrorMessage(errno)}")
        }
    } finally {
        fromFd.close()
        if (toFd.fd != fromFd.fd) toFd.close()
    }
}

const val RENAME_EXCHANGE = 2u
const val RENAME_NOREPLACE = 1u
const val RENAME_WHITEOUT = 4u
const val O_PATH = 0x200000