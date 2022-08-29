package dk.sdu.cloud.utils

import java.io.InputStream

sealed class ReadException(message: String) : RuntimeException(message) {
    class EndOfFile : ReadException("End of file has been reached but was not expected")
    class Error(val errno: Int) : ReadException(getNativeErrorMessage(errno))
}

@JvmInline
value class ReadResult(private val bytesReadOrNegativeErrno: Long) {
    val isEof: Boolean get() = bytesReadOrNegativeErrno == 0L
    val isError: Boolean get() = bytesReadOrNegativeErrno < 0L

    fun getErrorOrThrow(): Int {
        if (bytesReadOrNegativeErrno >= 0) throw IllegalStateException("Not an error")
        return bytesReadOrNegativeErrno.toInt() * -1
    }

    fun getErrorOrNull(): Int? {
        return if (bytesReadOrNegativeErrno >= 0) null
        else bytesReadOrNegativeErrno.toInt() * -1
    }

    fun getOrThrow(): Int {
        if (isEof) throw ReadException.EndOfFile()
        else if (isError) throw ReadException.Error(bytesReadOrNegativeErrno.toInt() * -1)
        return bytesReadOrNegativeErrno.toInt()
    }

    companion object {
        fun create(result: Long): ReadResult {
            return if (result < 0) ReadResult(-1L)
            else ReadResult(result.toLong())
        }
    }
}

class NativeInputStream(val jvmStream: InputStream) {
    fun read(destination: ByteArray, offset: Int = 0, size: Int = destination.size): ReadResult {
        require(offset >= 0) { "offset is negative ($offset)" }
        require(size >= 0) { "size is negative ($size)" }
        require(offset + size <= destination.size) {
            "offset + size is out of bounds ($offset + $size > ${destination.size})"
        }

        return ReadResult.create(jvmStream.read(destination, offset, size).toLong())
    }

    fun close(): Int {
        return try {
            jvmStream.close()
            0
        } catch (ex: Throwable) {
            -1
        }
    }
}

fun NativeInputStream.readBytes(limit: Int = 1024 * 128, autoClose: Boolean = true): ByteArray {
    val buffer = ByteArray(limit)
    var ptr = 0

    try {
        while (ptr < limit) {
            val maxBytesToRead = limit - ptr
            val result = read(buffer, ptr, maxBytesToRead)
            if (result.isEof) break
            ptr += result.getOrThrow()
        }

        return buffer.copyOf(ptr)
    } finally {
        if (autoClose) close()
    }
}

fun NativeInputStream.copyTo(
    outputStream: NativeOutputStream,
    autoCloseInput: Boolean = true,
    autoCloseOutput: Boolean = true
) {
    try {
        val buffer = ByteArray(1024 * 64)
        while (true) {
            val read = read(buffer)
            if (read.isEof) break
            outputStream.write(buffer, size = read.getOrThrow())
        }
    } finally {
        if (autoCloseInput) close()
        if (autoCloseOutput) outputStream.close()
    }
}
