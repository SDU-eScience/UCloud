package dk.sdu.cloud.storage.util

import java.io.InputStream
import kotlin.math.min

class BoundaryContainedStream(
    private val boundaryBytes: ByteArray,
    private val delegate: InputStream
) : InputStream() {
    private val searcher = StreamSearcher(boundaryBytes)
    private var clearedBytes = 0

    private val internalBuffer = ByteArray(32 * 1024)
    private var internalPointer = -1
    private var internalBufferSize = -1

    private var boundaryFound = false

    fun discardAll() {
        val discardBuffer = ByteArray(8 * 1024)
        while (true) {
            if (read(discardBuffer) == -1) break
        }
    }

    fun discardAndReset() {
        discardAll()
        resetStream()
    }

    fun resetStream() {
        assert(boundaryFound)
        assert(clearedBytes == 0)

        boundaryFound = false
        internalPointer += boundaryBytes.size

        if (internalPointer >= internalBufferSize) {
            // No more data in buffer invalidate it
            internalPointer = -1
            internalBufferSize = -1
        } else {
            boundaryFound = clearAsMuchAsPossible()
        }
    }


    private fun clearAsMuchAsPossible(): Boolean {
        assert(clearedBytes == 0)
        assert(internalPointer >= 0)

        val offset = searcher.search(internalBuffer, internalPointer, internalBufferSize)
        val len = internalBufferSize - internalPointer
        return if (offset == -1) {
            clearedBytes = if (boundaryBytes.size > len) len else len - (boundaryBytes.size - 1)
            false
        } else {
            clearedBytes = offset
            true
        }
    }

    private fun readMoreData() {
        assert(clearedBytes == 0)

        if (internalPointer != -1 && internalPointer != internalBufferSize) {
            // Copy existing data and read remaining
            val remainingBufferSize = internalBufferSize - internalPointer
            assert(remainingBufferSize >= 0)
            assert(remainingBufferSize < internalBuffer.size)
            System.arraycopy(
                internalBuffer,
                internalPointer,
                internalBuffer,
                0,
                remainingBufferSize
            )

            val read = delegate.read(
                internalBuffer,
                remainingBufferSize,
                internalBuffer.size - remainingBufferSize
            )

            if (read == -1) throw IllegalStateException("Unexpected end of stream. Boundary not found")

            internalBufferSize = remainingBufferSize + read
            internalPointer = 0
        } else {
            // Do a complete read
            internalBufferSize = delegate.read(internalBuffer)
            internalPointer = 0
        }
    }

    override fun read(): Int {
        val buffer = ByteArray(1)
        val result = read(buffer)
        if (result != 1) return result
        return buffer[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray): Int {
        return if (clearedBytes > 0) {
            val bytesToMove = min(b.size, clearedBytes)
            System.arraycopy(internalBuffer, internalPointer, b, 0, bytesToMove)
            internalPointer += bytesToMove
            clearedBytes -= bytesToMove
            return bytesToMove
        } else if (!boundaryFound) {
            readMoreData()
            boundaryFound = clearAsMuchAsPossible()

            if (clearedBytes <= 0) {
                -1
            } else {
                val bytesToMove = min(b.size, clearedBytes)
                System.arraycopy(internalBuffer, internalPointer, b, 0, bytesToMove)
                internalPointer += bytesToMove
                clearedBytes -= bytesToMove
                return bytesToMove
            }
        } else {
            -1
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return if (clearedBytes > 0) {
            val bytesToMove = min(len, clearedBytes)
            System.arraycopy(internalBuffer, internalPointer, b, off, bytesToMove)
            internalPointer += bytesToMove
            clearedBytes -= bytesToMove
            return bytesToMove
        } else if (!boundaryFound) {
            readMoreData()
            boundaryFound = clearAsMuchAsPossible()

            if (clearedBytes <= 0) {
                -1
            } else {
                val bytesToMove = min(len, clearedBytes)
                System.arraycopy(internalBuffer, internalPointer, b, off, bytesToMove)
                internalPointer += bytesToMove
                clearedBytes -= bytesToMove
                return bytesToMove
            }
        } else {
            -1
        }
    }

    override fun reset() {
        // No-op
    }

    override fun close() {
        // No-op
    }

    override fun markSupported(): Boolean {
        return false
    }
}