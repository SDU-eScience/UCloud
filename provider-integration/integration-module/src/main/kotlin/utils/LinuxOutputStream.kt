package dk.sdu.cloud.utils

import dk.sdu.cloud.plugins.storage.ucloud.DefaultDirectBufferPool
import dk.sdu.cloud.plugins.storage.ucloud.DefaultDirectBufferPoolForFileIo
import dk.sdu.cloud.plugins.storage.ucloud.DefaultDirectBufferPoolLarge
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import libc.clib
import java.io.Closeable
import java.io.EOFException
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

class LinuxOutputStream(private val handle: LinuxFileHandle) : Closeable {
    fun write(b: ByteBuffer): Int {
        val offset = b.position()
        val written = clib.write(handle.fd, b, offset, b.remaining())
        if (written > 0) b.position(offset + written)
        return written
    }

    override fun close() {
        handle.close()
    }
}

fun LinuxOutputStream.writeString(text: String) {
    DefaultDirectBufferPoolLarge.useInstance { buf ->
        buf.put(text.encodeToByteArray())

        buf.flip()
        while (buf.hasRemaining()) write(buf)
    }
}

fun LinuxInputStream.readString(): String {
    return DefaultDirectBufferPoolLarge.useInstance { buf ->
        while (true) {
            if (buf.remaining() == 0 || read(buf) <= 0) break
        }

        buf.flip()
        buf.decodeString()
    }
}

fun LinuxInputStream.copyTo(outs: LinuxOutputStream) {
    val ins = this
    DefaultDirectBufferPoolForFileIo.useInstance { readBuffer ->
        while (true) {
            if (ins.read(readBuffer) < 0) break
            readBuffer.flip()

            while (readBuffer.hasRemaining()) outs.write(readBuffer)
            readBuffer.flip()
        }
    }
}

suspend fun ByteReadChannel.copyTo(output: LinuxOutputStream) {
    while (!isClosedForRead) {
        try {
            read { readBuffer ->
                while (readBuffer.hasRemaining()) {
                    output.write(readBuffer)
                }
            }
        } catch (ex: EOFException) {
            // NOTE(Dan): isClosedForRead does not appear to be super reliable
            // immediately after read() ends. It seems like we need an extra
            // invocation to be sure that EOF is reached. This means that the
            // only reliable way of catching the end is through the exception.
            break
        }
    }
}

suspend fun LinuxInputStream.copyTo(channel: ByteWriteChannel) {
    DefaultDirectBufferPool.useInstance { readBuffer ->
        while (true) {
            if (read(readBuffer) < 0) break
            readBuffer.flip()

            while (readBuffer.hasRemaining()) {
                channel.write(readBuffer.remaining()) { buf ->
                    val oldLimit = readBuffer.limit()
                    readBuffer.limit(readBuffer.position() + min(buf.remaining(), readBuffer.remaining()))
                    buf.put(readBuffer)
                    readBuffer.limit(oldLimit)
                }
            }
            readBuffer.flip()
        }
    }
}
