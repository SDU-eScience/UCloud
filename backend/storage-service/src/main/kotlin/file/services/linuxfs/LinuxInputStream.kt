package dk.sdu.cloud.file.services.linuxfs

import com.sun.jna.Native
import kotlinx.io.pool.useInstance
import java.io.InputStream
import kotlin.math.min

private const val SEEK_CUR = 1
private const val EINTR = 4

class LinuxInputStream(private val fd: Int) : InputStream() {
    private var pos = 0L

    override fun read(): Int {
        DefaultByteArrayPool.useInstance { buf ->
            val read = CLibrary.INSTANCE.read(fd, buf, 1L).toInt()
            if (read == 0) return -1
            if (read == -1 && Native.getLastError() == EINTR) return 0
            pos += 1
            return buf[0].toInt() and 0xFF
        }
    }

    override fun read(b: ByteArray): Int {
        val read = CLibrary.INSTANCE.read(fd, b, b.size.toLong()).toInt()
        if (read == 0) return -1
        if (read == -1 && Native.getLastError() == EINTR) return 0
        pos += read
        return read
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return DefaultByteArrayPool.useInstance { buf ->
            val maxLength = min(len, buf.size).toLong()
            val read = CLibrary.INSTANCE.read(fd, buf, maxLength).toInt()
            if (read == 0) return -1
            if (read == -1 && Native.getLastError() == EINTR) return 0
            System.arraycopy(buf, 0, b, off, read)
            pos += read
            read
        }
    }

    override fun skip(n: Long): Long {
        val newPos = CLibrary.INSTANCE.lseek(fd, n, SEEK_CUR)
        val result = newPos - pos
        pos = newPos
        return result
    }

    override fun close() {
        CLibrary.INSTANCE.close(fd)
    }
}
