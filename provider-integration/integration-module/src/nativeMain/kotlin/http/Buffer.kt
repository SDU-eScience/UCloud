package dk.sdu.cloud.http

import kotlinx.cinterop.*
import platform.posix.memcpy
import kotlin.math.min

class ByteBuffer {
    val capacity: Int
    val rawMemory: ByteArray
    val rawMemoryPinned: Pinned<ByteArray>

    constructor(capacity: Int) {
        this.capacity = capacity
        this.rawMemory = ByteArray(capacity)
        this.rawMemoryPinned = rawMemory.pin()
    }

    constructor(other: ByteBuffer) {
        this.capacity = other.capacity
        this.rawMemory = other.rawMemory
        this.rawMemoryPinned = other.rawMemoryPinned
    }

    var writerIndex: Int = 0
    var readerIndex: Int = 0

    fun capacity(): Int = capacity

    fun limit(): Int = writerIndex

    fun limit(newLimit: Int): ByteBuffer {
        writerIndex = newLimit
        return this
    }

    inline fun readerRemaining(): Int = writerIndex - readerIndex
    inline fun writerSpaceRemaining(): Int = capacity - writerIndex

    fun put(bytes: ByteArray) {
        put(writerIndex, bytes)
        writerIndex += bytes.size
    }

    fun put(index: Int, bytes: ByteArray) {
        bytes.copyInto(rawMemory, index)
    }

    fun put(buffer: ByteBuffer) {
        writerIndex += put(writerIndex, buffer)
    }

    fun putAscii(buffer: String) {
        for (char in buffer) {
            rawMemory[writerIndex++] = char.code.toByte()
        }
    }

    fun put(index: Int, buffer: ByteBuffer): Int {
        val readerRemaining = buffer.readerRemaining()
        buffer.rawMemory.copyInto(rawMemory, index, buffer.readerIndex, buffer.writerIndex)
        return readerRemaining
    }

    fun put(value: Byte) {
        put(writerIndex++, value)
    }

    fun put(index: Int, value: Byte) {
        rawMemory[index] = value
    }

    fun putShort(value: Short) {
        putShort(writerIndex, value)
        writerIndex += 2
    }

    fun putShort(index: Int, value: Short) {
        val toInt = value.toInt()
        rawMemory[index + 1] = (toInt and 0xFF).toByte()
        rawMemory[index + 0] = ((toInt shr 8) and 0xFF).toByte()
    }

    fun putInt(value: Int) {
        putInt(writerIndex, value)
        writerIndex += 4
    }

    fun putInt(index: Int, value: Int) {
        rawMemory[index + 3] = (value and 0xFF).toByte()
        rawMemory[index + 2] = ((value shr 8) and 0xFF).toByte()
        rawMemory[index + 1] = ((value shr 16) and 0xFF).toByte()
        rawMemory[index + 0] = ((value shr 24) and 0xFF).toByte()
    }

    fun putLong(value: Long) {
        putLong(writerIndex, value)
        writerIndex += 8
    }

    fun putLong(index: Int, value: Long) {
        rawMemory[index + 7] = (value and 0xFF).toByte()
        rawMemory[index + 6] = ((value shr 8) and 0xFF).toByte()
        rawMemory[index + 5] = ((value shr 16) and 0xFF).toByte()
        rawMemory[index + 4] = ((value shr 24) and 0xFF).toByte()
        rawMemory[index + 3] = ((value shr 32) and 0xFF).toByte()
        rawMemory[index + 2] = ((value shr 40) and 0xFF).toByte()
        rawMemory[index + 1] = ((value shr 48) and 0xFF).toByte()
        rawMemory[index + 0] = ((value shr 56) and 0xFF).toByte()
    }

    fun putFloat(value: Float) {
        putFloat(writerIndex, value)
        writerIndex += 4
    }

    fun putFloat(index: Int, value: Float) {
        putInt(index, value.toBits())
    }

    fun putDouble(value: Double) {
        putDouble(writerIndex, value)
        writerIndex += 8
    }

    fun putDouble(index: Int, value: Double) {
        putLong(index, value.toBits())
    }

    fun getUnsigned(index: Int): UByte = rawMemory[index].toUByte()

    fun get(index: Int): Byte = rawMemory[index]

    fun getShort(index: Int): Short = (
        (rawMemory[index + 1].toInt()) or
            (rawMemory[index + 0].toInt() shl 8)
        ).toShort()

    fun getInt(index: Int): Int = (
        (rawMemory[index + 3].toInt()) or
            (rawMemory[index + 2].toInt() shl 8) or
            (rawMemory[index + 1].toInt() shl 16) or
            (rawMemory[index + 0].toInt() shl 24)
        )

    fun getLong(index: Int): Long = (
        (rawMemory[index + 7].toLong()) or
            (rawMemory[index + 6].toLong() shl 8) or
            (rawMemory[index + 5].toLong() shl 16) or
            (rawMemory[index + 4].toLong() shl 24) or
            (rawMemory[index + 3].toLong() shl 32) or
            (rawMemory[index + 2].toLong() shl 40) or
            (rawMemory[index + 1].toLong() shl 48) or
            (rawMemory[index + 0].toLong() shl 56)
        )

    fun getFloat(index: Int): Float = Float.fromBits(getInt(index))

    fun getDouble(index: Int): Double = Double.fromBits(getLong(index))

    fun get(destination: ByteArray): Int {
        val get = get(readerIndex, destination)
        readerIndex += get
        return get
    }

    fun get(index: Int, destination: ByteArray): Int {
        val bytesToRead = min(writerIndex - index, destination.size)
        rawMemory.copyInto(destination, 0, index, index + bytesToRead)
        return bytesToRead
    }

    fun slice(): ByteBuffer = ByteBuffer(this)

    fun compact() {
        val bytesToMove = readerRemaining()
        rawMemory.copyInto(rawMemory, 0, readerIndex, writerIndex)
        readerIndex = 0
        writerIndex = bytesToMove
    }

    fun clear() {
        readerIndex = 0
        writerIndex = 0
    }
}

fun allocateDirect(size: Int): ByteBuffer = ByteBuffer(size)
