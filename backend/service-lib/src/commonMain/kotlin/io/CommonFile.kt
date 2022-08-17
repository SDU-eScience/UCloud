package dk.sdu.cloud.io

import kotlin.jvm.JvmInline

// Error codes
// =====================================================================================================================
const val EAGAIN = 35
const val EINTR = 4

// File utilities
// =====================================================================================================================
expect class CommonFile(path: String) {
    val path: String
    fun exists(): Boolean
    fun isDirectory(): Boolean
}

fun CommonFile.child(subPath: String): CommonFile = CommonFile("$path/$subPath")

// Input utilities
// =====================================================================================================================
expect class CommonFileInputStream(file: CommonFile) {
    fun read(destination: ByteArray, offset: Int = 0, size: Int = destination.size): ReadResult
    fun close(): Int
}

sealed class ReadException(message: String) : RuntimeException(message) {
    class EndOfFile : ReadException("End of file has been reached but was not expected")
    class Error(val errno: Int) : ReadException("Error code = $errno")
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
        fun create(result: Long, errno: Int = 1): ReadResult {
            return if (result < 0) ReadResult(-1L * errno)
            else ReadResult(result)
        }
    }
}

fun CommonFileInputStream.readBytes(limit: Int = 1024 * 128, autoClose: Boolean = true): ByteArray {
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

// Output utilities
// =====================================================================================================================
expect class CommonFileOutputStream(file: CommonFile) {
    fun write(source: ByteArray, offset: Int = 0, size: Int = source.size): WriteResult
    fun close(): Int
}

sealed class WriteException(message: String) : RuntimeException(message) {
    class Error(val errno: Int) : WriteException("Error code = $errno")
}

@JvmInline
value class WriteResult(private val bytesWrittenOrNegativeErrno: Long) {
    val isError: Boolean get() = bytesWrittenOrNegativeErrno < 0L
    fun getOrThrow(): Int {
        if (isError) throw WriteException.Error(bytesWrittenOrNegativeErrno.toInt() * -1)
        return bytesWrittenOrNegativeErrno.toInt()
    }

    companion object {
        fun create(result: Long, errno: Int = 1): WriteResult {
            return if (result < 0) WriteResult(-1L * errno)
            else WriteResult(result)
        }
    }
}

fun CommonFileOutputStream.writeFully(
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

// IO utilities
// =====================================================================================================================
fun CommonFileInputStream.copyTo(
    outputStream: CommonFileOutputStream,
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

fun CommonFile.writeText(text: String) {
    CommonFileOutputStream(this).writeFully(text.encodeToByteArray())
}
