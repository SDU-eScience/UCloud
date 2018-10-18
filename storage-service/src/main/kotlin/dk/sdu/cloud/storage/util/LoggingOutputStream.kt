package dk.sdu.cloud.storage.util

import java.io.OutputStream

class LoggingOutputStream(val delegate: OutputStream, val logStream: OutputStream) : OutputStream() {
    override fun write(b: Int) {
        delegate.write(b)
        logStream.write(b)
        logStream.flush()
    }

    override fun write(b: ByteArray) {
        delegate.write(b)
        logStream.write(b)
        logStream.flush()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len)
        logStream.write(b, off, len)
    }

    override fun flush() {
        delegate.flush()
    }

    override fun close() {
        delegate.close()
    }
}
