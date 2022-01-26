package dk.sdu.cloud.utils

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.EAGAIN
import platform.posix.EINTR
import platform.posix.errno

sealed class WriteException(message: String) : RuntimeException(message) {
    class Error(val errno: Int) : WriteException(getNativeErrorMessage(errno))
}

value class WriteResult(private val bytesWrittenOrNegativeErrno: Long) {
    val isError: Boolean get() = bytesWrittenOrNegativeErrno < 0L
    fun getOrThrow(): Int {
        if (isError) throw ReadException.Error(bytesWrittenOrNegativeErrno.toInt() * -1)
        return bytesWrittenOrNegativeErrno.toInt()
    }

    companion object {
        fun create(result: Long): WriteResult {
            return if (result < 0) WriteResult(-1L * errno)
            else WriteResult(result)
        }
    }
}

class NativeOutputStream(val fd: Int) {
    fun write(source: ByteArray, offset: Int = 0, size: Int = source.size): WriteResult {
        require(offset >= 0) { "offset is negative" }
        require(size >= 0) { "size is negative" }
        require(offset + size <= source.size) { "offset + size is out of bounds" }

        return source.usePinned { pinned ->
            WriteResult.create(
                platform.posix.write(fd, pinned.addressOf(offset), size.toULong())
            )
        }
    }

    fun close() {
        platform.posix.close(fd)
    }
}

fun NativeOutputStream.writeFully(
    source: ByteArray,
    offset: Int = 0,
    size: Int = source.size,
    autoClose: Boolean = true
) {
    try {
        var ptr = offset
        val limit = offset + size
        while (ptr < limit) {
            val bytesToWrite = limit - ptr
            try {
                ptr += write(source, offset, bytesToWrite).getOrThrow()
            } catch (ex: WriteException.Error) {
                if (ex.errno == EAGAIN || ex.errno == EINTR) continue
                throw ex
            }
        }
    } finally {
        if (autoClose) close()
    }
}
