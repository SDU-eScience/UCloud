package dk.sdu.cloud.storage.util

import java.io.InputStream
import kotlin.math.min

class BoundaryContainedStream(
    private val boundaryBytes: ByteArray,
    private val delegate: InputStream
) : InputStream() {
    private var clearedBytes = 0

    private val internalBuffer = ByteArray(8 * 1024)
    private var internalPointer = -1
    private var internalBufferSize = -1

    private var boundaryFound = false

    // We need to, at all times, be sure that we don't deliver data that is part of the boundary.
    // This means we cannot deliver data if it might be part of the boundary.

    // This might become complicated if the boundary is split across internal buffers.
    // Given the boundary: "boundary" and the byte stream:
    // buffer1: [abcdefqboun], buffer2: [ary\n0]
    // it is important that we don't  return the 'boun' bytes, even if the client isn't requesting more data

    /**
     * Discards the remaining data in the stream. Boundary and anything after is left in the stream.
     * See [readRemainingAfterBoundary]
     */
    fun discardAll() {
        val discardBuffer = ByteArray(8 * 1024)
        while (true) {
            if (read(discardBuffer) == -1) break
        }
    }

    /**
     * Includes the boundary bytes too.
     */
    fun readRemainingAfterBoundary(): ByteArray {
        return internalBuffer.copyOfRange(internalPointer, internalBufferSize)
    }

    private fun clearAsMuchAsPossible(): Boolean {
        assert(clearedBytes == 0)
        var boundaryPtr = 0
        var internalPtr = internalPointer
        while (internalPtr < internalBufferSize) {
            if (boundaryPtr == boundaryBytes.size) {
                // Full match
                break
            } else if (internalBuffer[internalPtr] != boundaryBytes[boundaryPtr]) {
                // Not a match, reset the search. TODO Incorrect! Implement KMP
                boundaryPtr = 0
            } else {
                // A match, continue the search
                boundaryPtr++
            }

            internalPtr++
        }

        // We don't clear bytes that could be a prefix (or a full match)
        clearedBytes = (internalPtr - internalPointer) - boundaryPtr
        return boundaryPtr == boundaryBytes.size
    }

    private fun readMoreData() {
        assert(clearedBytes == 0)

        if (internalPointer != -1 && internalPointer != internalBufferSize) {
            // Copy existing data and read remaining
            val remainingBufferSize = internalBufferSize - internalPointer
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
        return if (clearedBytes > 0) {
            clearedBytes--
            internalBuffer[internalPointer++].toInt()
        } else if (!boundaryFound) {
            readMoreData()
            boundaryFound = clearAsMuchAsPossible()

            if (clearedBytes == 0) {
                -1
            } else {
                clearedBytes--
                internalBuffer[internalPointer++].toInt()
            }
        } else {
            -1
        }
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

            if (clearedBytes == 0) {
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

            if (clearedBytes == 0) {
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