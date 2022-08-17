package dk.sdu.cloud.io

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

actual class CommonFile actual constructor(actual val path: String) {
    val jvmFile = File(path)

    actual fun exists(): Boolean = jvmFile.exists()
    actual fun isDirectory(): Boolean = jvmFile.isDirectory
}

actual class CommonFileInputStream actual constructor(file: CommonFile) {
    val jvmInputStream = FileInputStream(file.jvmFile)

    actual fun read(destination: ByteArray, offset: Int, size: Int): ReadResult {
        return try {
            ReadResult.create(jvmInputStream.read(destination, offset, size).toLong())
        } catch (ex: IOException) {
            ReadResult.create(-1)
        }
    }

    actual fun close(): Int {
        return try {
            jvmInputStream.close()
            0
        } catch (ex: Throwable) {
            -1
        }
    }
}

actual class CommonFileOutputStream actual constructor(file: CommonFile) {
    val jvmOutputStream = FileOutputStream(file.jvmFile)

    actual fun write(source: ByteArray, offset: Int, size: Int): WriteResult {
        return try {
            jvmOutputStream.write(source, offset, size)
            jvmOutputStream.flush()
            WriteResult.create(size.toLong())
        } catch (ex: Throwable) {
            WriteResult.create(-1)
        }
    }

    actual fun close(): Int {
        return try {
            jvmOutputStream.close()
            0
        } catch (ex: Throwable) {
            -1
        }
    }
}
