package dk.sdu.cloud.file.services.linuxfs

import java.io.OutputStream

class LinuxOutputStream(private val fd: Int) : OutputStream() {
    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()))
    }

    override fun write(b: ByteArray) {
        CLibrary.INSTANCE.write(fd, b, b.size.toLong())
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        write(b.sliceArray(off until (off + len)))
    }

    override fun flush() {
        // Do nothing
    }

    override fun close() {
        CLibrary.INSTANCE.close(fd)
    }
}
