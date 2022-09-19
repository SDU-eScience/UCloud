package dk.sdu.cloud.debug

import dk.sdu.cloud.io.CommonFile
import dk.sdu.cloud.io.CommonFileOutputStream
import dk.sdu.cloud.io.*
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.ThreadLocal
import dk.sdu.cloud.calls.client.atomicInt
import dk.sdu.cloud.defaultMapper
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

sealed class DebugMessageTransformer {
    abstract fun transform(message: DebugMessage): DebugMessage?
    open fun <T> transformPayload(serializer: KSerializer<T>, payload: T?): JsonElement {
        return if (payload == null) JsonNull
        else defaultMapper.encodeToJsonElement(serializer, payload)
    }

    object Development : DebugMessageTransformer() {
        override fun transform(message: DebugMessage): DebugMessage = message
    }

    object Production : DebugMessageTransformer() {
        override fun <T> transformPayload(serializer: KSerializer<T>, payload: T?): JsonElement {
            return if (payload is DebugSensitive) {
                payload.removeSensitiveInformation()
            } else {
                super.transformPayload(serializer, payload)
            }
        }

        override fun transform(message: DebugMessage): DebugMessage? {
            return when (message) {
                is DebugMessage.ClientRequest -> null
                is DebugMessage.ClientResponse -> {
                    if (message.importance.ordinal < MessageImportance.THIS_IS_ODD.ordinal) return null
                    message.copy(response = null)
                }
                is DebugMessage.DatabaseConnection -> null
                is DebugMessage.DatabaseQuery -> null
                is DebugMessage.DatabaseResponse -> message.copy(parameters = JsonObject(emptyMap()))
                is DebugMessage.DatabaseTransaction -> null
                is DebugMessage.Log -> {
                    if (message.importance.ordinal < MessageImportance.THIS_IS_ODD.ordinal) return null
                    return message
                }
                is DebugMessage.ServerRequest -> null
                is DebugMessage.ServerResponse -> {
                    // Always keep this regardless of importance
                    return message.copy(response = null)
                }
            }
        }
    }

    object Disabled : DebugMessageTransformer() {
        override fun transform(message: DebugMessage): DebugMessage? = null
        override fun <T> transformPayload(serializer: KSerializer<T>, payload: T?): JsonElement = JsonNull
    }
}

/**
 * Information which is considered sensitive in the context of logs. Payload types (request/response) which implement
 * this interface has the option of removing some or all of their contents.
 */
interface DebugSensitive {
    fun removeSensitiveInformation(): JsonElement
}

class CommonDebugSystem(
    id: String,
    directory: CommonFile,
    private val transformer: DebugMessageTransformer = DebugMessageTransformer.Development
) : DebugSystem {
    private val idGen = atomicInt(0)
    private val local = ThreadLocal { ThreadLocalDebugSystem(id, directory, idGen.getAndIncrement().toString()) }

    override suspend fun <T> transformPayload(serializer: KSerializer<T>, payload: T?): JsonElement {
        return transformer.transformPayload(serializer, payload)
    }

    override suspend fun sendMessage(message: DebugMessage) {
        val newMessage = transformer.transform(message) ?: return
        local.get().sendMessage(newMessage)
    }
}

class ThreadLocalDebugSystem(
    private val id: String,
    private val directory: CommonFile,
    private val suffix: String,
) {
    private val outputStream: CommonFileOutputStream
    private val newLine = "\n".encodeToByteArray()

    init {
        val now = Time.now()
        directory.child("${id.replace("/", "-")}-${now}-${suffix}.meta.json").writeText(
            """
                {
                    "path": "$id",
                    "startedAt": ${Time.now()}
                }
            """.trimIndent()
        )

        outputStream = CommonFileOutputStream(directory.child("${id.replace("/", "-")}-${now}-${suffix}.json"))
    }

    fun sendMessage(message: DebugMessage) {
        outputStream.writeFully(
            defaultMapper.encodeToString(DebugMessage.serializer(), message).encodeToByteArray(),
            autoClose = false
        )

        outputStream.writeFully(newLine, autoClose = false)
    }
}
