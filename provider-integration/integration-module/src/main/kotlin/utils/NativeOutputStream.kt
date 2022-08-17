package dk.sdu.cloud.utils

import java.io.IOException
import java.io.OutputStream

sealed class WriteException(message: String) : RuntimeException(message) {
    class Error(val errno: Int) : WriteException(getNativeErrorMessage(errno))
}

@JvmInline
value class WriteResult(private val bytesWrittenOrNegativeErrno: Long) {
    val isError: Boolean get() = bytesWrittenOrNegativeErrno < 0L
    fun getOrThrow(): Int {
        if (isError) throw ReadException.Error(bytesWrittenOrNegativeErrno.toInt() * -1)
        return bytesWrittenOrNegativeErrno.toInt()
    }

    companion object {
        fun create(result: Long): WriteResult {
            return if (result < 0) WriteResult(-1L)
            else WriteResult(result)
        }
    }
}

class NativeOutputStream(val jvmStream: OutputStream) {
    fun write(source: ByteArray, offset: Int = 0, size: Int = source.size): WriteResult {
        require(offset >= 0) { "offset is negative" }
        require(size >= 0) { "size is negative" }
        require(offset + size <= source.size) { "offset + size is out of bounds" }

        val code = try {
            jvmStream.write(source, offset, size)
            size
        } catch (ex: IOException) {
            -1
        }

        return WriteResult(code.toLong())
    }

    fun close(): Int {
        return try {
            jvmStream.close()
            0
        } catch (ex: IOException) {
            -1
        }
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
                throw ex
            }
        }
    } finally {
        if (autoClose) close()
    }
}
