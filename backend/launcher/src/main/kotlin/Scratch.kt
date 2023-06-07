import com.github.jasync.sql.db.util.size
import dk.sdu.cloud.defaultMapper
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.kotlin.ir.interpreter.toIrConst
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

@JvmInline
value class ResourceMetadata(override val buffer: BufferAndOffset) : BinaryType {
    var id: Long
        inline get() = buffer.data.getLong(0 + buffer.offset)
        inline set(value) { buffer.data.putLong(0 + buffer.offset, value) }

    var createdAt: Long
        inline get() = buffer.data.getLong(8 + buffer.offset)
        inline set(value) { buffer.data.putLong(8 + buffer.offset, value) }

    var owner: ResourceOwner
        inline get() = ResourceOwner(buffer.copy(offset = buffer.data.getInt(16 + buffer.offset)))
        inline set(value) { buffer.data.putInt(16 + buffer.offset, value.buffer.offset) }

    override fun encodeToJson(): JsonElement = JsonObject(
        mapOf(
            "id" to JsonPrimitive(id),
            "createdAt" to JsonPrimitive(id),
            "owner" to owner.encodeToJson()
        )
    )

    companion object : BinaryTypeCompanion<ResourceMetadata> {
        override val size = 20
        override fun create(buffer: BufferAndOffset) = ResourceMetadata(buffer)

        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): ResourceMetadata {
            if (json !is JsonObject) invalidInput("Element is not an object: $json")

            val id = run {
                val id = json["id"]
                if (id !is JsonPrimitive) invalidInput("Missing key id: $json")
                id.content.toLong()
            }

            val createdAt = run {
                val element = json["createdAt"]
                if (element !is JsonPrimitive) invalidInput("Missing key createdAt: $json")
                element.content.toLong()
            }

            val owner = run {
                val element = json["owner"]
                if (element == null) invalidInput("Missing key owner: $json")
                ResourceOwner.decodeFromJson(allocator, element)
            }

            return allocator.ResourceMetadata(id, createdAt, owner)
        }
    }
}

fun BinaryAllocator.ResourceMetadata(id: Long, createdAt: Long, owner: ResourceOwner): ResourceMetadata {
    val result = allocate(ResourceMetadata)
    result.id = id
    result.createdAt = createdAt
    result.owner = owner
    return result
}

@JvmInline
value class ResourceOwner(override val buffer: BufferAndOffset) : BinaryType {
    var _createdBy: Text
        inline get() = Text(buffer.copy(offset = buffer.data.getInt(0 + buffer.offset)))
        inline set(value) { buffer.data.putInt(0 + buffer.offset, value.buffer.offset) }

    val createdBy: String
        inline get() = _createdBy.decode()

    var _project: Text?
        inline get() {
            val offset = buffer.data.getInt(4 + buffer.offset)
            return if (offset == 0) null
            else Text(buffer.copy(offset = offset))
        }
        inline set(value) { buffer.data.putInt(4 + buffer.offset, value?.buffer?.offset ?: 0) }

    val project: String?
        inline get() = _project?.decode()


    override fun encodeToJson(): JsonElement = JsonObject(
        mapOf(
            "createdBy" to JsonPrimitive(createdBy),
            "project" to JsonPrimitive(project),
        )
    )

    companion object : BinaryTypeCompanion<ResourceOwner> {
        override val size = 8
        override fun create(buffer: BufferAndOffset) = ResourceOwner(buffer)

        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): ResourceOwner {
            if (json !is JsonObject) invalidInput("Element is not an object: $json")

            val createdBy = run {
                val element = json["createdBy"]
                if (element == null || element !is JsonPrimitive || !element.isString) invalidInput("Missing key createdBy: $json")
                element.content
            }

            val project = run {
                val element = json["project"]
                when (element) {
                    null, JsonNull -> null
                    !is JsonPrimitive -> invalidInput("Bad key project: $json")
                    else -> element.content
                }
            }

            return allocator.ResourceOwner(createdBy, project)
        }
    }
}

fun BinaryAllocator.ResourceOwner(createdBy: String, project: String? = null): ResourceOwner {
    val result = this.allocate(ResourceOwner)
    result._createdBy = allocateText(createdBy)
    result._project = project?.let { allocateText(it) }
    return result
}

class BinaryAllocator {
    private val buf = ByteBuffer.allocate(1024 * 64)
    private var ptr = 4
    private val textEncoder by lazy { Charsets.UTF_8.newEncoder() }
    private val duplicateStrings = arrayOfNulls<String>(128)
    private val duplicateStringsPointers = IntArray(duplicateStrings.size)
    private var duplicateStringsIdx = 0

    init {
        buf.putInt(0, ptr)
    }

    fun <T : BinaryType> allocate(companion: BinaryTypeCompanion<T>): T {
        require(companion.size != 0) { "Cannot allocate a dynamic type through this type" }
        val buffer = BufferAndOffset(buf, ptr)
        ptr += companion.size
        return companion.create(buffer)
    }

    fun allocateDynamic(size: Int): BufferAndOffset {
        val buffer = BufferAndOffset(buf, ptr)
        ptr += size
        return buffer
    }

    fun allocateText(text: String): Text {
        // NOTE(Dan): Here we are sacrificing a bit of speed during encode to improve space efficiency. We are quite
        // often encoding the same strings many times.
        for ((index, str) in duplicateStrings.withIndex()) {
            if (text == str) {
                return Text(BufferAndOffset(buf, duplicateStringsPointers[index]))
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

        return Text(BufferAndOffset(buf, resultPtr))
    }

    fun reset() {
        ptr = 4
        buf.putInt(0, ptr)
        duplicateStringsIdx++
    }

    fun debug(): ByteArray {
        val result = ByteArray(ptr)
        buf.get(0, result)
        return result
    }
}

data class BufferAndOffset(val data: ByteBuffer, val offset: Int)

fun <R> useAllocator(block: BinaryAllocator.() -> R): R {
    return BinaryAllocator().run(block)
}

fun main() {
    val json = useAllocator {
        val metadata = ResourceMetadata(
            id = 42,
            createdAt = 1337,
            owner = ResourceOwner("hund", project = "hund")
        )
        println(metadata.id)
        println(metadata.createdAt)
        println(metadata.owner.createdBy)
        println(metadata.owner.project)

        val result = debug()
        println("Size is: ${result.size}")
        println("${result.toList()}")

        val json = defaultMapper.encodeToString(JsonElement.serializer(), metadata.encodeToJson())
        println("Json size is: ${json.size}")
        println(json)
        json
    }

    println("--- Json decode ---")

    useAllocator {
        val decoded = ResourceMetadata.decodeFromJson(this, defaultMapper.decodeFromString(JsonElement.serializer(), json))
        println(decoded.id)
        println(decoded.createdAt)
        println(decoded.owner.createdBy)
        println(decoded.owner.project)

        val result = debug()
        println("Size is: ${result.size}")
        println("${result.toList()}")
    }

    println("Hi!")
}
