package dk.sdu.cloud.utils

import libc.clib
import java.io.Closeable
import java.nio.ByteBuffer

private const val SEEK_CUR = 1
private const val EINTR = 4

class LinuxInputStream(private val handle: LinuxFileHandle) : Closeable {
    private var pos = 0L

    fun read(b: ByteBuffer): Int {
        val offset = b.position()
        val read = clib.read(handle.fd, b, offset, b.remaining())
        if (read > 0) b.position(offset + read)
        if (read == 0) return -1
        if (read == -1 && clib.getErrno() == EINTR) return 0
        pos += read
        return read
    }

    fun skip(n: Long): Long {
        val newPos = clib.lseek(handle.fd, n, SEEK_CUR)
        val result = newPos - pos
        pos = newPos
        return result
    }

    override fun close() {
        handle.close()
    }
}
