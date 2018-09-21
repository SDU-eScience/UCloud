package dk.sdu.cloud.storage.util

import java.io.InputStream
import java.io.OutputStream

class LoggingInputStream(val delegate: InputStream, val outputStream: OutputStream) : InputStream() {
    override fun skip(n: Long): Long {
        return delegate.skip(n)
    }

    override fun available(): Int {
        return delegate.available()
    }

    override fun reset() {
        return delegate.reset()
    }

    override fun close() {
        outputStream.write("~~CLOSE~~".toByteArray())
        return delegate.close()
    }

    override fun mark(readlimit: Int) {
        delegate.mark(readlimit)
    }

    override fun markSupported(): Boolean {
        return delegate.markSupported()
    }

    override fun read(): Int {
        val read = delegate.read()
        outputStream.write(read)
        outputStream.flush()
        return read
    }

    override fun read(b: ByteArray): Int {
        val read = delegate.read(b)
        if (read > 0) {
            outputStream.write(b, 0, read)
            outputStream.flush()
        }
        return read
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val read = delegate.read(b, off, len)
        if (read > 0) {
            outputStream.write(b, off, read)
            outputStream.flush()
        }
        return read
    }
}