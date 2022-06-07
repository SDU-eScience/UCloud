package dk.sdu.cloud.io

actual class CommonFile actual constructor(actual val path: String) {
    actual fun exists(): Boolean = throw UnsupportedOperationException("macOS not supported")
    actual fun isDirectory(): Boolean = throw UnsupportedOperationException("macOS not supported")
}

actual class CommonFileInputStream actual constructor(file: CommonFile) {
    actual fun read(destination: ByteArray, offset: Int, size: Int): ReadResult =
        throw UnsupportedOperationException("macOS not supported")
    actual fun close(): Int = throw UnsupportedOperationException("macOS not supported")
}

actual class CommonFileOutputStream actual constructor(file: CommonFile) {
    actual fun write(source: ByteArray, offset: Int, size: Int): WriteResult =
        throw UnsupportedOperationException("macOS not supported")
    actual fun close(): Int = throw UnsupportedOperationException("macOS not supported")
}
