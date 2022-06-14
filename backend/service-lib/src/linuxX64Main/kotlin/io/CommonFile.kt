package dk.sdu.cloud.io

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.*

actual class CommonFile actual constructor(actual val path: String) {
    actual fun exists(): Boolean = throw UnsupportedOperationException("macOS not supported")
    actual fun isDirectory(): Boolean = throw UnsupportedOperationException("macOS not supported")
}

actual class CommonFileInputStream actual constructor(file: CommonFile) {
    private val fd = open(file.path, 0, 0)
    actual fun read(destination: ByteArray, offset: Int, size: Int): ReadResult {
        require(offset >= 0) { "offset is negative ($offset)" }
        require(size >= 0) { "size is negative ($size)" }
        require(offset + size <= destination.size) {
            "offset + size is out of bounds ($offset + $size > ${destination.size})"
        }

        return destination.usePinned { pinned ->
            ReadResult.create(platform.posix.read(fd, pinned.addressOf(offset), size.toULong()))
        }
    }

    actual fun close(): Int {
        return platform.posix.close(fd)
    }
}

actual class CommonFileOutputStream actual constructor(file: CommonFile) {
    private val fd = open(file.path, O_CREAT or O_WRONLY or O_TRUNC, "640".toUInt(8))
    actual fun write(source: ByteArray, offset: Int, size: Int): WriteResult {
        require(offset >= 0) { "offset is negative" }
        require(size >= 0) { "size is negative" }
        require(offset + size <= source.size) { "offset + size is out of bounds" }

        return source.usePinned { pinned ->
            WriteResult.create(
                platform.posix.write(fd, pinned.addressOf(offset), size.toULong()),
                errno
            )
        }
    }

    actual fun close(): Int {
        return platform.posix.close(fd)
    }
}
