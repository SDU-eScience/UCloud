package dk.sdu.cloud.messages

import dk.sdu.cloud.defaultMapper
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.lang.UnsupportedOperationException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.util.*
import kotlin.collections.AbstractMap

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

class BinaryTypeList<T : BinaryType>(val companion: BinaryTypeCompanion<T>, override val buffer: BufferAndOffset) : BinaryType, AbstractMutableList<T>() {
    var count: Int
        get() = buffer.data.getInt(0 + buffer.offset)
        set(value) { buffer.data.putInt(0 + buffer.offset, value) }

    override val size: Int get() = count

    override fun get(index: Int): T {
        if (index !in 0 until count) throw IndexOutOfBoundsException("$index is out of bounds of array with length $count")

        val elementPointer = buffer.data.getInt(4 + buffer.offset + index * 4)
//        println("Reading element $index from $elementPointer")
        return companion.create(buffer.copy(offset = elementPointer))
    }

    override fun add(index: Int, element: T) = throw UnsupportedOperationException("BinaryTypeLists cannot change size")
    override fun removeAt(index: Int): T = throw UnsupportedOperationException("BinaryTypeLists cannot change size")

    override fun set(index: Int, element: T): T {
        buffer.data.putInt((buffer.offset + (1 + index) * 4).also {
            println("set($index, $element) at offset $it")
        }, element.buffer.offset)
        return element
    }

    override fun encodeToJson(): JsonElement {
        return JsonArray(this.map { it.encodeToJson() })
    }

    companion object {
        fun <T : BinaryType> create(companion: BinaryTypeCompanion<T>, allocator: BinaryAllocator, size: Int): BinaryTypeList<T> {
            return BinaryTypeList(companion, allocator.allocateDynamic((size + 1) * 4)).also {
                it.count = size
            }
        }

        fun <T : BinaryType> create(companion: BinaryTypeCompanion<T>, allocator: BinaryAllocator, source: List<T>): BinaryTypeList<T> {
            val result = create(companion, allocator, source.size)
            for ((index, item) in source.withIndex()) {
                result[index] = item
            }
            return result
        }

        fun <T : BinaryType> decodeFromJson(companion: BinaryTypeCompanion<T>, allocator: BinaryAllocator, json: JsonElement): BinaryTypeList<T> {
            if (json !is JsonArray) invalidInput("Element is not an array: $json")
            val result = create(companion, allocator, json.size)
            result.count = json.size

            for ((index, item) in json.withIndex()) {
                val decoded = companion.decodeFromJson(allocator, item)
                result[index] = decoded
            }
            return result
        }
    }
}

class BinaryTypeDictionary<T : BinaryType>(val companion: BinaryTypeCompanion<T>, override val buffer: BufferAndOffset) : BinaryType {
    var count: Int
        get() = buffer.data.getInt(0 + buffer.offset)
        set(value) { buffer.data.putInt(0 + buffer.offset, value) }

    private var nextIdx: Int = 0

    operator fun get(key: String): T? {
        for (i in 0 until count) {
            val textPtr = buffer.data.getInt(buffer.offset + (1 + (i * 2) + 0) * 4)
            val text = Text(buffer.copy(offset = textPtr))
            if (text.decode() == key) {
                val dataPtr = buffer.data.getInt(buffer.offset + (1 + (i * 2) + 1) * 4)
                if (dataPtr == 0) return null
                return companion.create(buffer.copy(offset = dataPtr))
            }
        }
        return null
    }

    operator fun set(key: String, value: T) {
        set(buffer.allocator.allocateText(key), value)
    }

    operator fun set(key: Text, value: T) {
        val index = nextIdx++
        if (index !in 0 until count) {
            throw IndexOutOfBoundsException("Too many entries in dictionary! " +
                    "Wanted to set value at $index but size is $count.")
        }

        buffer.data.putInt(buffer.offset + (1 + (index * 2) + 0) * 4, key.buffer.offset)
        buffer.data.putInt(buffer.offset + (1 + (index * 2) + 1) * 4, value.buffer.offset)
    }

    fun keys(): List<Text> {
        return (0 until count).mapNotNull { i ->
            val textPtr = buffer.data.getInt(buffer.offset + (1 + (i * 2) + 0) * 4)
            if (textPtr == 0) return@mapNotNull null

            Text(buffer.copy(offset = textPtr))
        }
    }

    override fun encodeToJson(): JsonElement {
        return JsonObject(buildMap {
            for (i in 0 until count) {
                val textPtr = buffer.data.getInt(buffer.offset + (1 + (i * 2) + 0) * 4)
                if (textPtr == 0) continue

                val text = Text(buffer.copy(offset = textPtr))
                val valuePtr = buffer.data.getInt(buffer.offset + (1 + (i * 2) + 1) * 4)
                put(text.decode(), companion.create(buffer.copy(offset = valuePtr)).encodeToJson())
            }
        })
    }

    companion object {
        fun <T : BinaryType> create(
            companion: BinaryTypeCompanion<T>,
            allocator: BinaryAllocator,
            source: Map<String, T>
        ): BinaryTypeDictionary<T> {
            val result = createWithSize(companion, allocator, source.size)
            for ((key, value) in source) {
                result[key] = value
            }
            return result
        }

        fun <T : BinaryType> createWithSize(
            companion: BinaryTypeCompanion<T>,
            allocator: BinaryAllocator,
            size: Int
        ): BinaryTypeDictionary<T> {
            val buf = allocator.allocateDynamic(4 + size * 2 * 4)
            val result = BinaryTypeDictionary(companion, buf)
            result.count = size
            return result
        }

        fun <T : BinaryType> decodeFromJson(
            companion: BinaryTypeCompanion<T>,
            allocator: BinaryAllocator,
            json: JsonElement
        ): BinaryTypeDictionary<T> {
            if (json !is JsonObject) invalidInput("Element is not an object: $json")
            val builder = HashMap<String, T>()
            for ((k, v) in json) {
                val decoded = companion.decodeFromJson(allocator, v)
                builder[k] = decoded
            }
            return create(companion, allocator, builder)
        }
    }
}

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

class BinaryAllocator(
    bufferSize: Int = 1024 * 512,
    duplicateStringsSize: Int = 128,
    val readOnly: Boolean = false,
) {
    private val buf = ByteBuffer.allocateDirect(bufferSize + (32 - (bufferSize % 32)))
    private val zeroBuffer = ByteBuffer.allocateDirect(32)
    private var ptr = 4
    private val textEncoder by lazy { Charsets.UTF_8.newEncoder() }
    private val duplicateStrings = arrayOfNulls<String>(duplicateStringsSize)
    private val duplicateStringsPointers = IntArray(duplicateStringsSize)
    private var duplicateStringsIdx = 0

    init {
        buf.putInt(0, ptr)
        require(buf.capacity() % 32 == 0) { "${buf.capacity()} is not a multiple of 32. This shouldn't happen." }
        require(readOnly || duplicateStringsSize > 0) { "only read only buffers can have duplicateStringsSize = 0" }
    }

    suspend fun load(channel: ByteReadChannel) {
        while (!channel.isClosedForRead) {
            channel.readAvailable(buf)
        }
        ptr = buf.position()
        buf.position(0)
    }

    fun load(data: ByteArray) {
        buf.put(0, data)
        ptr = data.size
    }

    fun <T : BinaryType> allocate(companion: BinaryTypeCompanion<T>): T {
        require(!readOnly) { "This allocator is marked as read only. Please copy the data to a new allocator for modification." }
        require(companion.size != 0) { "Cannot allocate a dynamic type through this type" }

        val buffer = BufferAndOffset(this, buf, ptr)
        ptr += companion.size
        return companion.create(buffer)
    }

    fun allocateDynamic(size: Int): BufferAndOffset {
        require(!readOnly) { "This allocator is marked as read only. Please copy the data to a new allocator for modification." }

        val buffer = BufferAndOffset(this, buf, ptr)
        ptr += size
        return buffer
    }

    fun allocateText(text: String): Text {
        require(!readOnly) { "This allocator is marked as read only. Please copy the data to a new allocator for modification." }

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
        ptr += zeroBuffer.capacity() - (ptr % zeroBuffer.capacity())
        buf.position(ptr)
        buf.flip()
        while (buf.hasRemaining()) {
            buf.put(zeroBuffer)
            zeroBuffer.clear()
        }

        buf.clear()
        ptr = 4
        buf.putInt(0, ptr)

        duplicateStringsIdx = 0
        Arrays.fill(duplicateStrings, null)
        Arrays.fill(duplicateStringsPointers, 0)
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
    override val descriptor = buildClassSerialDescriptor(companion.javaClass?.canonicalName?.removeSuffix(".Companion") ?: "Unknown")

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

fun BinaryAllocator.stringListOf(vararg elems: String): BinaryTypeList<Text> {
    return BinaryTypeList.create(Text, this, elems.map { allocateText(it) })
}

fun <V : BinaryType> BinaryAllocator.dictOf(
    companion: BinaryTypeCompanion<V>,
    vararg pairs: Pair<String, V>
): BinaryTypeDictionary<V> {
    val underlyingMap = mapOf(*pairs)
    return BinaryTypeDictionary.create(companion, this, underlyingMap)
}

fun <V : BinaryType> BinaryAllocator.dictOf(
    companion: BinaryTypeCompanion<V>,
    pairs: List<Pair<String, V>>
): BinaryTypeDictionary<V> {
    val underlyingMap = HashMap<String, V>()
    for (pair in pairs) underlyingMap[pair.first] = pair.second

    return BinaryTypeDictionary.create(companion, this, underlyingMap)
}
