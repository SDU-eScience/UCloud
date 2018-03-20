package dk.sdu.cloud.app.util

import java.io.InputStream

class GuardedInputStream(private val delegate: InputStream) : InputStream() {
    override fun read(): Int {
        return delegate.read()
    }

    override fun read(b: ByteArray?): Int {
        return delegate.read(b)
    }

    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        return delegate.read(b, off, len)
    }

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
        // Do nothing
    }

    override fun mark(readlimit: Int) {
        return delegate.mark(readlimit)
    }

    override fun markSupported(): Boolean {
        return delegate.markSupported()
    }
}

fun InputStream.guarded(): InputStream = GuardedInputStream(this)