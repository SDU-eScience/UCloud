package dk.sdu.cloud.messages

import dk.sdu.cloud.defaultMapper
import io.ktor.utils.io.pool.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import java.nio.ByteBuffer
import java.nio.CharBuffer

interface BinaryType {
    val buffer: BufferAndOffset

    fun encodeToJson(): JsonElement
}

interface BinaryTypeCompanion<T : BinaryType> {
    val size: Int
    fun create(buffer: BufferAndOffset): T

    fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): T
}

fun invalidInput(message: String): Nothing = error(message)

@JvmInline
value class Text(override val buffer: BufferAndOffset) : BinaryType {
    var count: Int
        get() = buffer.data.getInt(0 + buffer.offset)
        set(value) { buffer.data.putInt(0 + buffer.offset, value) }

    val data: ByteBuffer
        get() = buffer.data.slice(4 + buffer.offset, count)

    fun decode(): String = Charsets.UTF_8.decode(data).toString()

    override fun encodeToJson(): JsonElement = JsonPrimitive(decode())

    companion object : BinaryTypeCompanion<Text> {
        override val size = 0
        override fun create(buffer: BufferAndOffset): Text = Text(buffer)

        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): Text {
            if (json !is JsonPrimitive || !json.isString) invalidInput("Element is not a string: $json")
            return allocator.allocateText(json.content)
        }
    }
}

class BinaryAllocator(bufferSize: Int = 1024 * 512) {
    private val buf = ByteBuffer.allocateDirect(bufferSize)
    private var ptr = 4
    private val textEncoder by lazy { Charsets.UTF_8.newEncoder() }
    private val duplicateStrings = arrayOfNulls<String>(128)
    private val duplicateStringsPointers = IntArray(duplicateStrings.size)
    private var duplicateStringsIdx = 0

    init {
        buf.putInt(0, ptr)
    }

    fun load(data: ByteArray) {
        buf.put(0, data)
        ptr = data.size
    }

    fun <T : BinaryType> allocate(companion: BinaryTypeCompanion<T>): T {
        require(companion.size != 0) { "Cannot allocate a dynamic type through this type" }
        val buffer = BufferAndOffset(this, buf, ptr)
        ptr += companion.size
        return companion.create(buffer)
    }

    fun allocateDynamic(size: Int): BufferAndOffset {
        val buffer = BufferAndOffset(this, buf, ptr)
        ptr += size
        return buffer
    }

    fun allocateText(text: String): Text {
        // NOTE(Dan): Here we are sacrificing a bit of speed during encode to improve space efficiency. We are quite
        // often encoding the same strings many times.
        for ((index, str) in duplicateStrings.withIndex()) {
            if (text == str) {
                return Text(BufferAndOffset(this, buf, duplicateStringsPointers[index]))
            }
        }

        val bufferSlice = buf.slice(ptr + 4, buf.capacity() - ptr - 4)
        if (textEncoder.encode(CharBuffer.wrap(text), bufferSlice, true).isError) {
            error("could not encode text")
        }

        val length = bufferSlice.position()
        buf.putInt(ptr, length)
        textEncoder.reset()
        val resultPtr = ptr
        ptr = resultPtr + 4 + length

        run {
            // Store the text in our array to reduce string duplication
            val key = duplicateStringsIdx++ % duplicateStrings.size
            duplicateStrings[key] = text
            duplicateStringsPointers[key] = resultPtr
        }

        return Text(BufferAndOffset(this, buf, resultPtr))
    }

    fun reset() {
        ptr = 4
        buf.putInt(0, ptr)
        duplicateStringsIdx++
    }

    fun updateRoot(root: BinaryType) {
        buf.putInt(0, root.buffer.offset)
    }

    fun root(): BufferAndOffset {
        return BufferAndOffset(this, buf, buf.getInt(0))
    }

    fun slicedBuffer(): ByteBuffer {
        return buf.slice(0, ptr)
    }
}

fun <T : BinaryType> BinaryTypeCompanion<T>.decodeFromJson(allocator: BinaryAllocator, jsonData: String): T {
    return decodeFromJson(allocator, defaultMapper.decodeFromString(JsonElement.serializer(), jsonData))
}

data class BufferAndOffset(val allocator: BinaryAllocator, val data: ByteBuffer, val offset: Int)

class BinaryTypeSerializer<T : BinaryType>(val companion: BinaryTypeCompanion<T>) : KSerializer<T> {
    override val descriptor = buildClassSerialDescriptor(companion.javaClass.canonicalName.removeSuffix(".Companion"))

    override fun deserialize(decoder: Decoder): T {
        if (decoder !is JsonDecoder) error("can only deserialize BinaryTypes using a JSON decoder!")

        val allocator = BinaryAllocator(1024 * 2)
        return companion.decodeFromJson(allocator, decoder.decodeJsonElement())
    }

    override fun serialize(encoder: Encoder, value: T) {
        if (encoder !is JsonEncoder) error("can only serialize BinaryTypes using a JSON encoder!")
        encoder.encodeJsonElement(value.encodeToJson())
    }
}

object AllocatorPool : DefaultPool<BinaryAllocator>(Runtime.getRuntime().availableProcessors() * 2) {
    override fun produceInstance(): BinaryAllocator = BinaryAllocator()
    override fun clearInstance(instance: BinaryAllocator): BinaryAllocator {
        instance.reset()
        return instance
    }
}

inline fun <R> useAllocator(block: BinaryAllocator.() -> R): R {
    return AllocatorPool.useInstance(block)
}
