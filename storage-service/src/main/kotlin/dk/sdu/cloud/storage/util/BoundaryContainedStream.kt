package dk.sdu.cloud.storage.util

import java.io.InputStream
import kotlin.math.min

class BoundaryContainedStream(
    private val boundaryBytes: ByteArray,
    private val delegate: InputStream
) : InputStream() {
    private val searcher = StreamSearcher(boundaryBytes)
    private var clearedBytes = 0
    private var preclearedBytes = 0L
    private var preclaredRecentlyEmptied = false

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

    fun manualClearNextBytes(numberOfBytesToClear: Long) {
        assert(preclearedBytes == 0L)
        preclearedBytes = numberOfBytesToClear

        // All bytes from now on should detract from preclared bytes, thus we need to reset clearedBytes
        clearedBytes = 0
        boundaryFound = false
        clearAsMuchAsPossible()
    }

    private fun clearAsMuchAsPossible(): Boolean {
        assert(clearedBytes == 0)
        assert(internalPointer >= 0)

        val len = internalBufferSize - internalPointer
        return if (preclearedBytes > 0) {
            val min = min(preclearedBytes, len.toLong())
            assert(min <= Int.MAX_VALUE)

            preclearedBytes -= min
            clearedBytes = min.toInt()
            assert(clearedBytes >= 0)

            if (preclearedBytes == 0L) preclaredRecentlyEmptied = true

            assert(preclearedBytes >= 0)
            assert(clearedBytes >= 0)
            false
        } else {
            val offset = searcher.search(internalBuffer, internalPointer, internalBufferSize)
            if (offset == -1) {
                clearedBytes = if (boundaryBytes.size > len) len else len - (boundaryBytes.size - 1)
                assert(clearedBytes >= 0)
                false
            } else {
                clearedBytes = offset - internalPointer
                assert(clearedBytes >= 0)
                true
            }
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

            if (internalBufferSize == -1) throw IllegalStateException("Unexpected end of stream. Boundary not found")
        }
    }

    override fun read(): Int {
        val buffer = ByteArray(1)
        val result = read(buffer)
        if (result != 1) return result
        return buffer[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (preclaredRecentlyEmptied && clearedBytes == 0) {
            boundaryFound = clearAsMuchAsPossible()
            preclaredRecentlyEmptied = false
        }

        return if (clearedBytes > 0) {
            val bytesToMove = min(len, clearedBytes)
            System.arraycopy(internalBuffer, internalPointer, b, off, bytesToMove)
            internalPointer += bytesToMove
            clearedBytes -= bytesToMove
            assert(clearedBytes >= 0)
            return bytesToMove
        } else if (!boundaryFound) {
            readMoreData()
            if (internalBufferSize == -1) {
                throw IllegalStateException("Unexpected end of stream")
            } else {
                boundaryFound = clearAsMuchAsPossible()
            }

            if (clearedBytes <= 0) {
                -1
            } else {
                val bytesToMove = min(len, clearedBytes)
                System.arraycopy(internalBuffer, internalPointer, b, off, bytesToMove)
                internalPointer += bytesToMove
                clearedBytes -= bytesToMove
                assert(clearedBytes >= 0)
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