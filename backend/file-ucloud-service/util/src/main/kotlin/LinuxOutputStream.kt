package dk.sdu.cloud.file.ucloud.services

import java.io.OutputStream

class LinuxOutputStream(private val handle: LinuxFileHandle) : OutputStream() {
    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()))
    }

    override fun write(b: ByteArray) {
        CLibrary.INSTANCE.write(handle.fd, b, b.size.toLong())
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
