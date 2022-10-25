package dk.sdu.cloud.utils

import libc.clib
import java.io.OutputStream

class LinuxOutputStream(private val handle: LinuxFileHandle) : OutputStream() {
    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()))
    }

    override fun write(b: ByteArray) {
        clib.write(handle.fd, b, b.size.toLong())
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        write(b.sliceArray(off until (off + len)))
    }

    override fun flush() {
        // Do nothing
    }

    override fun close() {
        handle.close()
    }
}
