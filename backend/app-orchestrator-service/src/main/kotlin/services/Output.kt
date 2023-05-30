package dk.sdu.cloud.app.orchestrator.services

import io.ktor.utils.io.*
import kotlinx.serialization.json.JsonElement

@JvmInline value class Fie private constructor(
    override val data: Array<Any?> = arrayOfNulls(globalSchema.fieldTypes.size)
): BinaryMessage {
    override inline fun schema() = globalSchema

    var dynamic: ByteArray
        inline get() {
            val b = data[0] as ByteArray
            return b
        }

        inline set(value) {
            data[0] = value
        }
    var fixed: List<ByteArray>
        inline get() {
            val b = data[1] as List<ByteArray>
            return b
        }

        inline set(value) {
            data[1] = value
        }
    var wide: List<ByteArray>
        inline get() {
            val b = data[2] as List<ByteArray>
            return b
        }

        inline set(value) {
            data[2] = value
        }
    var optional: ByteArray?
        inline get() {
            val b = data[3] as List<ByteArray>?
            if (b.isNullOrEmpty()) return null
            return b.single()
        }

        inline set(value) {
            data[3] = value?.let { listOf(it) }
        }
    var shortStringRepeated: List<String>
        inline get() {
            val b = data[4] as List<String>
            return b
        }

        inline set(value) {
            data[4] = value
        }

    suspend fun encode(channel: ByteWriteChannel) {
        globalSchema.encode(channel, this)
    }

    suspend fun encodeToJson(): JsonElement {
        return globalSchema.encodeToJson(this)
    }

    companion object {
        fun create(
            dynamic: ByteArray,
            fixed: List<ByteArray>,
            wide: List<ByteArray>,
            optional: ByteArray?,
            shortStringRepeated: List<String>,
        ): Fie {
            return Fie().apply {
                this.dynamic = dynamic
                this.fixed = fixed
                this.wide = wide
                this.optional = optional
                this.shortStringRepeated = shortStringRepeated
            }
        }
        val globalSchema = BinarySchema.Record(
            "Fie",
            arrayOf(
                "dynamic",
                "fixed",
                "wide",
                "optional",
                "shortStringRepeated",
            ),
            arrayOf(
                BinarySchema.Bytes(minSize = 0, maxSize = 131072),
                BinarySchema.Repeated(BinarySchema.Bytes(minSize = 0, maxSize = 131072), 0, 4123),
                BinarySchema.Repeated(BinarySchema.Bytes(minSize = 0, maxSize = 131072), 1, 4923),
                BinarySchema.Repeated(BinarySchema.Bytes(minSize = 0, maxSize = 131072), 0, 1),
                BinarySchema.Repeated(BinarySchema.Text(minSize = 0, maxSize = 120), 0, 64),
            ),
            { Fie(it) },
        )

        suspend fun decode(channel: ByteReadChannel): Fie {
            return globalSchema.decode(channel) as Fie
        }

        suspend fun decodeFromJson(element: JsonElement): Fie {
            return globalSchema.decodeFromJson(element) as Fie
        }
    }
}

@JvmInline value class LinkedList private constructor(
    override val data: Array<Any?> = arrayOfNulls(globalSchema.fieldTypes.size)
): BinaryMessage {
    override inline fun schema() = globalSchema

    var value: Int
        inline get() {
            val b = data[0] as Int
            return b
        }

        inline set(value) {
            data[0] = value
        }
    var next: LinkedList?
        inline get() {
            val b = data[1] as List<LinkedList>?
            if (b.isNullOrEmpty()) return null
            return b.single()
        }

        inline set(value) {
            data[1] = value?.let { listOf(it) }
        }

    suspend fun encode(channel: ByteWriteChannel) {
        globalSchema.encode(channel, this)
    }

    suspend fun encodeToJson(): JsonElement {
        return globalSchema.encodeToJson(this)
    }

    companion object {
        fun create(
            value: Int,
            next: LinkedList?,
        ): LinkedList {
            return LinkedList().apply {
                this.value = value
                this.next = next
            }
        }
        val globalSchema = BinarySchema.Record(
            "LinkedList",
            arrayOf(
                "value",
                "next",
            ),
            arrayOf(
                BinarySchema.I32,
                BinarySchema.Repeated(BinarySchema.Self, 0, 1),
            ),
            { LinkedList(it) },
        )

        suspend fun decode(channel: ByteReadChannel): LinkedList {
            return globalSchema.decode(channel) as LinkedList
        }

        suspend fun decodeFromJson(element: JsonElement): LinkedList {
            return globalSchema.decodeFromJson(element) as LinkedList
        }
    }
}
