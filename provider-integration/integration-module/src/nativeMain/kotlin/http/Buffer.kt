package dk.sdu.cloud.http

import kotlinx.cinterop.*
import platform.posix.memcpy
import kotlin.math.min

class ByteBuffer {
    private val arena: Arena?
    val capacity: Int
    val rawMemory: CPointer<ByteVar>

    constructor(capacity: Int) {
        this.capacity = capacity
        this.arena = Arena()
        this.rawMemory = arena.allocArray(capacity)
    }

    constructor(rawMemory: CPointer<ByteVar>, capacity: Int) {
        this.capacity = capacity
        this.arena = null
        this.rawMemory = rawMemory
    }

    private var _writerIndex: Int = 0
    var writerIndex: Int
        get() = _writerIndex
        set(value) {
            require(value >= 0 && value < capacity())
            _writerIndex = value
        }
    private var _readerIndex: Int = 0
    var readerIndex: Int
        get() = _readerIndex
        set(value) {
            require(value >= 0)
            _readerIndex = value
        }

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
        check(index + bytes.size < capacity)

        bytes.usePinned { pin ->
            memcpy(rawMemory.plus(index), pin.addressOf(0), bytes.size.toULong())
        }
    }

    fun put(buffer: ByteBuffer) {
        writerIndex += put(writerIndex, buffer)
    }

    fun put(index: Int, buffer: ByteBuffer): Int {
        val readerRemaining = buffer.readerRemaining()
        check(index + readerRemaining < capacity)
        memcpy(rawMemory.plus(index), buffer.rawMemory.plus(buffer.readerIndex), readerRemaining.toULong())
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
        rawMemory[index] = (toInt and 0xFF).toByte()
        rawMemory[index + 1] = ((toInt shr 8) and 0xFF).toByte()
    }

    fun putInt(value: Int) {
        putInt(writerIndex, value)
        writerIndex += 4
    }

    fun putInt(index: Int, value: Int) {
        rawMemory[index] = (value and 0xFF).toByte()
        rawMemory[index + 1] = ((value shr 8) and 0xFF).toByte()
        rawMemory[index + 2] = ((value shr 16) and 0xFF).toByte()
        rawMemory[index + 3] = ((value shr 24) and 0xFF).toByte()
    }

    fun putLong(value: Long) {
        putLong(writerIndex, value)
        writerIndex += 8
    }

    fun putLong(index: Int, value: Long) {
        rawMemory[index] = (value and 0xFF).toByte()
        rawMemory[index + 1] = ((value shr 8) and 0xFF).toByte()
        rawMemory[index + 2] = ((value shr 16) and 0xFF).toByte()
        rawMemory[index + 3] = ((value shr 24) and 0xFF).toByte()
        rawMemory[index + 4] = ((value shr 32) and 0xFF).toByte()
        rawMemory[index + 5] = ((value shr 40) and 0xFF).toByte()
        rawMemory[index + 6] = ((value shr 48) and 0xFF).toByte()
        rawMemory[index + 7] = ((value shr 56) and 0xFF).toByte()
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
        (rawMemory[index].toInt()) or
            (rawMemory[index + 1].toInt() shl 8)
        ).toShort()

    fun getInt(index: Int): Int = (
        (rawMemory[index].toInt()) or
            (rawMemory[index + 1].toInt() shl 8) or
            (rawMemory[index + 2].toInt() shl 16) or
            (rawMemory[index + 3].toInt() shl 24)
        )

    fun getLong(index: Int): Long = (
        (rawMemory[index].toLong()) or
            (rawMemory[index + 1].toLong() shl 8) or
            (rawMemory[index + 2].toLong() shl 16) or
            (rawMemory[index + 3].toLong() shl 24) or
            (rawMemory[index + 4].toLong() shl 32) or
            (rawMemory[index + 5].toLong() shl 40) or
            (rawMemory[index + 6].toLong() shl 48) or
            (rawMemory[index + 7].toLong() shl 56)
        )

    fun getFloat(index: Int): Float = Float.fromBits(getInt(index))

    fun getDouble(index: Int): Double = Double.fromBits(getLong(index))

    fun get(destination: ByteArray): Int {
        val get = get(readerIndex, destination)
        readerIndex += get
        return get
    }

    fun get(index: Int, destination: ByteArray): Int {
        check(index in 0 until capacity)
        val bytesToRead = min(writerIndex - index, destination.size)
        require(bytesToRead >= 0)
        destination.usePinned { pin ->
            memcpy(pin.addressOf(0), rawMemory + index, bytesToRead.toULong())
        }
        return bytesToRead
    }

    fun slice(): ByteBuffer = ByteBuffer(rawMemory, capacity).also {
        it._readerIndex = _readerIndex
        it._writerIndex = _writerIndex
    }

    fun compact() {
        val bytesToMove = readerRemaining()
        memcpy(rawMemory, rawMemory.plus(readerIndex), bytesToMove.toULong())
        readerIndex = 0
        writerIndex = bytesToMove
    }

    fun clear() {
        _readerIndex = 0
        _writerIndex = 0
    }
}

fun allocateDirect(size: Int): ByteBuffer = ByteBuffer(size)
