package dk.sdu.cloud.storage.ext

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * An output stream which delegates all write calls to an underlying stream. However, this wrapper will
 * protect the underlying stream from being closed. This is useful for passing stdout/stderr to wrappers
 * that ordinarily need to close their stream when a file-writer or similar is passed, but not when using
 * stdout/stderr.
 */
class GuardedOutputStream(private val guardedStream: OutputStream) : OutputStream() {
    @Throws(IOException::class)
    override fun write(b: Int) {
        guardedStream.write(b)
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray) {
        guardedStream.write(b)
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        guardedStream.write(b, off, len)
    }

    @Throws(IOException::class)
    override fun flush() {
        guardedStream.flush()
    }

    @Throws(IOException::class)
    override fun close() {
        // Do nothing
    }
}

/**
 * An input stream which delegates all write calls to an underlying stream. However, this wrapper will
 * protect the underlying stream from being closed. This is useful for passing stdout/stderr to wrappers
 * that ordinarily need to close their stream when a file-writer or similar is passed, but not when using
 * stdout/stderr.
 */
class GuardedInputStream(private val guardedStream: InputStream) : InputStream() {
    override fun skip(n: Long): Long {
        return guardedStream.skip(n)
    }

    override fun available(): Int {
        return guardedStream.available()
    }

    override fun reset() {
        guardedStream.reset()
    }

    override fun mark(readlimit: Int) {
        guardedStream.mark(readlimit)
    }

    override fun markSupported(): Boolean {
        return guardedStream.markSupported()
    }

    override fun read(): Int {
        return guardedStream.read()
    }

    override fun read(b: ByteArray?): Int {
        return guardedStream.read(b)
    }

    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        return guardedStream.read(b, off, len)
    }

    @Throws(IOException::class)
    override fun close() {
        // Do nothing
    }
}
