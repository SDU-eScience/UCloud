package dk.sdu.cloud.debugger

import java.nio.ByteBuffer
import kotlin.reflect.KProperty

@Suppress("unused")
abstract class BinaryFrameSchema(parent: BinaryFrameSchema? = null) {
    var size: Int = (parent?.size ?: 0)

    protected fun int1() = BinaryFrameField.Int1(size).also { size += 1 }
    protected fun int2() = BinaryFrameField.Int2(size).also { size += 2 }
    protected fun int4() = BinaryFrameField.Int4(size).also { size += 4 }
    protected fun int8() = BinaryFrameField.Int8(size).also { size += 8 }
    protected fun float4() = BinaryFrameField.Float4(size).also { size += 4 }
    protected fun float8() = BinaryFrameField.Float8(size).also { size += 8 }
    protected fun bool() = BinaryFrameField.Bool(size).also { size += 1 }
    protected fun bytes(size: Short) = BinaryFrameField.Bytes(this.size, size).also { this.size += size.toInt() + 2 }
    protected fun text(maxSize: Int = 128) = BinaryFrameField.Text(this.size, maxSize).also { this.size += maxSize }
    protected inline fun <reified E : Enum<E>> enum() = BinaryFrameField.Enumeration(this.size, enumValues<E>())
        .also { this.size += 1 }
}

abstract class BinaryFrame(val buf: ByteBuffer, var offset: Int = 0) {
    abstract val schema: BinaryFrameSchema
}

sealed class BinaryFrameField(val offset: Int) {
    class Int1(offset: Int) : BinaryFrameField(offset) {
        operator fun getValue(thisRef: BinaryFrame, property: KProperty<*>): Byte {
            return thisRef.buf.get(thisRef.offset + offset)
        }

        operator fun setValue(thisRef: BinaryFrame, property: KProperty<*>, value: Byte) {
            thisRef.buf.put(thisRef.offset + offset, value)
        }
    }

    class Int2(offset: Int) : BinaryFrameField(offset) {
        operator fun getValue(thisRef: BinaryFrame, property: KProperty<*>): Short {
            return thisRef.buf.getShort(thisRef.offset + offset)
        }

        operator fun setValue(thisRef: BinaryFrame, property: KProperty<*>, value: Short) {
            thisRef.buf.putShort(thisRef.offset + offset, value)
        }
    }

    class Int4(offset: Int) : BinaryFrameField(offset) {
        operator fun getValue(thisRef: BinaryFrame, property: KProperty<*>): Int {
            return thisRef.buf.getInt(thisRef.offset + offset)
        }

        operator fun setValue(thisRef: BinaryFrame, property: KProperty<*>, value: Int) {
            thisRef.buf.putInt(thisRef.offset + offset, value)
        }
    }

    class Int8(offset: Int) : BinaryFrameField(offset) {
        operator fun getValue(thisRef: BinaryFrame, property: KProperty<*>): Long {
            return thisRef.buf.getLong(thisRef.offset + offset)
        }

        operator fun setValue(thisRef: BinaryFrame, property: KProperty<*>, value: Long) {
            thisRef.buf.putLong(thisRef.offset + offset, value)
        }
    }

    class Float4(offset: Int) : BinaryFrameField(offset) {
        operator fun getValue(thisRef: BinaryFrame, property: KProperty<*>): Float {
            return thisRef.buf.getFloat(thisRef.offset + offset)
        }

        operator fun setValue(thisRef: BinaryFrame, property: KProperty<*>, value: Float) {
            thisRef.buf.putFloat(thisRef.offset + offset, value)
        }
    }

    class Float8(offset: Int) : BinaryFrameField(offset) {
        operator fun getValue(thisRef: BinaryFrame, property: KProperty<*>): Double {
            return thisRef.buf.getDouble(thisRef.offset + offset)
        }

        operator fun setValue(thisRef: BinaryFrame, property: KProperty<*>, value: Double) {
            thisRef.buf.putDouble(thisRef.offset + offset, value)
        }
    }

    class Bool(offset: Int) : BinaryFrameField(offset) {
        operator fun getValue(thisRef: BinaryFrame, property: KProperty<*>): Boolean {
            return thisRef.buf.get(thisRef.offset + offset) != 0.toByte()
        }

        operator fun setValue(thisRef: BinaryFrame, property: KProperty<*>, value: Boolean) {
            thisRef.buf.put(thisRef.offset + offset, if (value) 1 else 0)
        }
    }

    class Bytes(offset: Int, val size: Short) : BinaryFrameField(offset) {
        operator fun getValue(thisRef: BinaryFrame, property: KProperty<*>): ByteArray {
            val length = thisRef.buf.getShort(thisRef.offset + offset)
            val output = ByteArray(length.toInt())
            thisRef.buf.get(thisRef.offset + offset + 2, output, 0, length.toInt())
            return output
        }

        operator fun setValue(thisRef: BinaryFrame, property: KProperty<*>, value: ByteArray) {
            val length = if (value.size >= size) size else value.size.toShort()
            thisRef.buf.putShort(thisRef.offset + offset, length)
            thisRef.buf.put(thisRef.offset + offset + 2, value, 0, length.toInt())
        }
    }

    class Text(offset: Int, val maxSize: Int = 128) : BinaryFrameField(offset) {
        private val delegate = Bytes(offset, (maxSize - 2).toShort())

        operator fun getValue(thisRef: BinaryFrame, property: KProperty<*>): LargeText {
            return LargeText(delegate.getValue(thisRef, property))
        }

        operator fun setValue(thisRef: BinaryFrame, property: KProperty<*>, value: LargeText) {
            delegate.setValue(thisRef, property, value.value)
        }
    }

    class Enumeration<E : Enum<E>>(offset: Int, val enumeration: Array<E>) : BinaryFrameField(offset) {
        operator fun getValue(thisRef: BinaryFrame, property: KProperty<*>): E {
            val ordinal = thisRef.buf.get(thisRef.offset + offset).toInt() and 0xFF
            return enumeration[ordinal]
        }

        operator fun setValue(thisRef: BinaryFrame, property: KProperty<*>, value: E) {
            thisRef.buf.put(thisRef.offset + offset, value.ordinal.toByte())
        }
    }
}

@JvmInline
value class LargeText internal constructor(val value: ByteArray) {
    companion object {
        const val OVERFLOW_PREFIX = "#OF#"
        const val OVERFLOW_SEP = "#"
    }
    override fun toString(): String = value.decodeToString()
}
