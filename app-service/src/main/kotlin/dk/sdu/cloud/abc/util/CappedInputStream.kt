package dk.sdu.cloud.abc.util

import java.io.InputStream

internal class CappedInputStream(private val delegate: InputStream, private val maxBytes: Long) : InputStream() {
    private var remainingBytes = maxBytes

    val isEmpty get() = remainingBytes == 0L

    fun skipRemaining() {
        skip(remainingBytes)
    }

    override fun read(): Int {
        if (remainingBytes == 0L) return -1
        return delegate.read().also { remainingBytes-- }
    }

    override fun read(b: ByteArray): Int = read(b, 0, b.size)

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remainingBytes == 0L) return -1

        val actualLength = if (remainingBytes < len) remainingBytes.toInt() else len
        return delegate.read(b, off, actualLength).also { remainingBytes -= it }
    }

    override fun skip(n: Long): Long {
        val actualLength = if (remainingBytes < n) remainingBytes else n
        return delegate.skip(actualLength)
    }

    override fun available(): Int {
        val avail = delegate.available()
        return if (remainingBytes < avail) remainingBytes.toInt() else avail
    }

    override fun reset() {
        throw UnsupportedOperationException("reset not supported")
    }

    override fun close() {
        // Do nothing
    }

    override fun mark(readlimit: Int) {
        throw UnsupportedOperationException("mark not supported")
    }

    override fun markSupported(): Boolean = false
}