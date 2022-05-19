package dk.sdu.cloud.debug

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.AttributeKey
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Time
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

@Serializable
sealed class DebugContext {
    abstract val id: String
    abstract val parent: String?
    abstract var depth: Int

    @SerialName("server")
    @Serializable
    data class Server(
        override val id: String,
        override val parent: String? = null,
        override var depth: Int = 0,
    ) : DebugContext()

    @SerialName("client")
    @Serializable
    data class Client(
        override val id: String,
        override val parent: String? = null,
        override var depth: Int = 0,
    ) : DebugContext()

    @SerialName("job")
    @Serializable
    data class Job(
        override val id: String,
        override val parent: String? = null,
        override var depth: Int = 0,
    ) : DebugContext()
}

@Serializable
sealed class DebugMessage {
    abstract val context: DebugContext
    abstract val timestamp: Long
    abstract val principal: SecurityPrincipal?
    abstract val importance: MessageImportance
    abstract val messageType: MessageType
    abstract val id: Int

    @SerialName("client_request")
    @Serializable
    data class ClientRequest(
        override val context: DebugContext,
        override val timestamp: Long,
        override val principal: SecurityPrincipal?,
        override val importance: MessageImportance,
        val call: String?,
        val payload: JsonElement?,
        val resolvedHost: String,
    ) : DebugMessage() {
        override val messageType = MessageType.CLIENT
        override val id = idGenerator.getAndIncrement()
    }

    @SerialName("client_response")
    @Serializable
    data class ClientResponse(
        override val context: DebugContext,
        override val timestamp: Long,
        override val principal: SecurityPrincipal?,
        override val importance: MessageImportance,
        val call: String?,
        val response: JsonElement?,
        val responseCode: Int
    ) : DebugMessage() {
        override val messageType = MessageType.CLIENT
        override val id = idGenerator.getAndIncrement()
    }

    @SerialName("server_request")
    @Serializable
    data class ServerRequest(
        override val context: DebugContext,
        override val timestamp: Long,
        override val principal: SecurityPrincipal?,
        override val importance: MessageImportance,
        val call: String?,
        val payload: JsonElement?,
    ) : DebugMessage() {
        override val messageType = MessageType.SERVER
        override val id = idGenerator.getAndIncrement()
    }

    @SerialName("server_response")
    @Serializable
    data class ServerResponse(
        override val context: DebugContext,
        override val timestamp: Long,
        override val principal: SecurityPrincipal?,
        override val importance: MessageImportance,
        val call: String?,
        val response: JsonElement?,
        val responseCode: Int
    ) : DebugMessage() {
        override val messageType = MessageType.SERVER
        override val id = idGenerator.getAndIncrement()
    }

    @SerialName("database_connection")
    @Serializable
    data class DatabaseConnection(
        override val context: DebugContext,
        val isOpen: Boolean,
        override val timestamp: Long = Time.now(),
        override val principal: SecurityPrincipal? = null,
        override val importance: MessageImportance = MessageImportance.TELL_ME_EVERYTHING,
    ) : DebugMessage() {
        override val messageType = MessageType.DATABASE
        override val id = idGenerator.getAndIncrement()
    }

    enum class DBTransactionEvent {
        OPEN,
        COMMIT,
        ROLLBACK
    }

    @SerialName("database_transaction")
    @Serializable
    data class DatabaseTransaction(
        override val context: DebugContext,
        val event: DBTransactionEvent,
        override val timestamp: Long = Time.now(),
        override val principal: SecurityPrincipal? = null,
        override val importance: MessageImportance = MessageImportance.TELL_ME_EVERYTHING,
    ) : DebugMessage() {
        override val messageType = MessageType.DATABASE
        override val id = idGenerator.getAndIncrement()
    }

    @SerialName("database_query")
    @Serializable
    data class DatabaseQuery(
        override val context: DebugContext,
        val query: String,
        val parameters: JsonObject,
        override val importance: MessageImportance = MessageImportance.THIS_IS_NORMAL,
        override val timestamp: Long = Time.now(),
        override val principal: SecurityPrincipal? = null,
    ) : DebugMessage() {
        override val messageType = MessageType.DATABASE
        override val id = idGenerator.getAndIncrement()
    }

    @SerialName("database_response")
    @Serializable
    data class DatabaseResponse(
        override val context: DebugContext,
        override val importance: MessageImportance = MessageImportance.IMPLEMENTATION_DETAIL,
        override val timestamp: Long = Time.now(),
        override val principal: SecurityPrincipal? = null,
    ) : DebugMessage() {
        override val messageType = MessageType.DATABASE
        override val id = idGenerator.getAndIncrement()
    }

    @SerialName("log")
    @Serializable
    data class Log(
        override val context: DebugContext,
        val message: String,
        val extras: JsonObject? = null,
        override val importance: MessageImportance = MessageImportance.IMPLEMENTATION_DETAIL,
        override val timestamp: Long = Time.now(),
        override val principal: SecurityPrincipal? = null,
    ) : DebugMessage() {
        override val messageType = MessageType.LOG
        override val id = idGenerator.getAndIncrement()
    }

    companion object {
        val idGenerator = atomicInt(0)
    }
}

enum class MessageType {
    SERVER,
    DATABASE,
    CLIENT,
    LOG
}

enum class MessageImportance {
    /**
     * You (the developer/operator) only want to see this message if something is badly misbehaving, and you are
     * desperate for clues. It should primarily contain the insignificant details.
     */
    TELL_ME_EVERYTHING,

    /**
     * You (the developer/operator) want to see this message if something is mildly misbehaving. It should contain the
     * most important implementation details.
     */
    IMPLEMENTATION_DETAIL,

    /**
     * Indicates that an ordinary event has occurred. Most RPCs, which aren't chatty, fall into this category.
     */
    THIS_IS_NORMAL,

    /**
     * Indicates that something might be wrong, but not for certain. A developer/operator might want to see this, but
     * it is not critical.
     */
    THIS_IS_ODD,

    /**
     * A clear message that something is wrong. A developer/operator want to see this as soon as possible, but it can
     * probably wait until the next morning.
     */
    THIS_IS_WRONG,

    /**
     * This should never happen. This event needs to be investigated immediately.
     *
     * Only pick this category if you feel comfortable waking up your co-workers in the middle of the night to tell
     * them about this.
     */
    THIS_IS_DANGEROUS
}

class DebugCoroutineContext(
    val context: DebugContext,
) : AbstractCoroutineContextElement(DebugCoroutineContext) {
    companion object Key : CoroutineContext.Key<DebugCoroutineContext>
}

suspend inline fun parentContextId(): String? {
    val ctx = coroutineContext
    return ctx[DebugCoroutineContext]?.context?.id
}

val logIdGenerator = atomicInt(0)
suspend fun DebugSystem.log(message: String, structured: JsonObject?, level: MessageImportance) {
    sendMessage(
        DebugMessage.Log(
            DebugContext.Job(
                logIdGenerator.getAndIncrement().toString(),
                parentContextId(),
            ),
            message,
            structured,
            level
        )
    )
}

suspend fun DebugSystem.everything(message: String, structured: JsonObject? = null) {
    log(message, structured, MessageImportance.TELL_ME_EVERYTHING)
}

suspend fun DebugSystem.detail(message: String, structured: JsonObject? = null) {
    log(message, structured, MessageImportance.IMPLEMENTATION_DETAIL)
}

suspend fun DebugSystem.normal(message: String, structured: JsonObject? = null) {
    log(message, structured, MessageImportance.THIS_IS_NORMAL)
}

suspend fun DebugSystem.odd(message: String, structured: JsonObject? = null) {
    log(message, structured, MessageImportance.THIS_IS_ODD)
}

suspend fun DebugSystem.wrong(message: String, structured: JsonObject? = null) {
    log(message, structured, MessageImportance.THIS_IS_WRONG)
}

suspend fun DebugSystem.dangerous(message: String, structured: JsonObject? = null) {
    log(message, structured, MessageImportance.THIS_IS_DANGEROUS)
}

suspend inline fun <reified R> DebugSystem?.logD(
    message: String,
    structured: R,
    level: MessageImportance,
    context: DebugContext? = null
) {
    if (this == null) return
    val encoded = when (R::class) {
        kotlin.Array::class,
        kotlin.collections.List::class,
        kotlin.collections.Set::class,
        kotlin.collections.Collection::class -> {
            defaultMapper.encodeToJsonElement(
                serializer<Map<String, R>>(),
                mapOf("wrapper" to structured)
            ) as JsonObject
        }

        else -> {
            defaultMapper.encodeToJsonElement(
                serializer<R>(),
                structured
            ) as JsonObject
        }
    }

    sendMessage(
        DebugMessage.Log(
            context ?: DebugContext.Job(
                logIdGenerator.getAndIncrement().toString(),
                parentContextId(),
            ),
            message,
            encoded,
            level
        )
    )
}

suspend inline fun <reified R> DebugSystem?.everythingD(message: String, structured: R, context: DebugContext? = null) {
    logD(message, structured, MessageImportance.TELL_ME_EVERYTHING, context)
}

suspend inline fun <reified R> DebugSystem?.detailD(message: String, structured: R, context: DebugContext? = null) {
    logD(message, structured, MessageImportance.IMPLEMENTATION_DETAIL, context)
}

suspend inline fun <reified R> DebugSystem?.normalD(message: String, structured: R, context: DebugContext? = null) {
    logD(message, structured, MessageImportance.THIS_IS_NORMAL, context)
}

suspend inline fun <reified R> DebugSystem?.oddD(message: String, structured: R, context: DebugContext? = null) {
    logD(message, structured, MessageImportance.THIS_IS_ODD, context)
}

suspend inline fun <reified R> DebugSystem?.dangerousD(message: String, structured: R, context: DebugContext? = null) {
    logD(message, structured, MessageImportance.THIS_IS_DANGEROUS, context)
}

suspend inline fun <reified R> DebugSystem?.wrongD(message: String, structured: R) {
    logD(message, structured, MessageImportance.THIS_IS_WRONG)
}

class DebugSystemLogContext(
    val name: String,
    val debug: DebugSystem?,
    val debugContext: DebugContext,
) {
    suspend fun logExit(
        message: String,
        data: JsonObject? = null,
        level: MessageImportance = MessageImportance.THIS_IS_NORMAL
    ) {
        if (debug == null) return
        debug.sendMessage(
            DebugMessage.Log(
                debugContext,
                if (message.isBlank()) name else "$name: $message",
                data,
                level
            )
        )
    }
}

suspend inline fun <R> DebugSystem?.enterContext(
    name: String,
    crossinline block: suspend DebugSystemLogContext.() -> R
): R {
    val debug = this

    val debugContext = DebugContext.Job(logIdGenerator.getAndIncrement().toString(), parentContextId())
    val logContext = DebugSystemLogContext(name, debug, debugContext)
    if (debug == null) return block(logContext)

    return withContext(DebugCoroutineContext(debugContext)) {
        debug.sendMessage(
            DebugMessage.Log(
                debugContext,
                "$name: Start",
                null,
                MessageImportance.IMPLEMENTATION_DETAIL
            )
        )
        block(logContext)
    }
}

interface DebugSystem {
    suspend fun sendMessage(message: DebugMessage)
}

fun DebugSystem.installCommon(client: RpcClient) {
    val key = AttributeKey<String>("debug-id")
    client.attachFilter(object : OutgoingCallFilter.BeforeCall() {
        private val baseKey = "Client-"
        private val idGenerator = atomicInt(0)
        override fun canUseContext(ctx: OutgoingCall): Boolean = true

        override suspend fun run(context: OutgoingCall, callDescription: CallDescription<*, *, *>, request: Any?) {
            @Suppress("UNCHECKED_CAST") val call = callDescription as CallDescription<Any, Any, Any>
            val id = baseKey + idGenerator.getAndIncrement()
            context.attributes[key] = id
            sendMessage(
                DebugMessage.ClientRequest(
                    DebugContext.Client(
                        id,
                        parentContextId(),
                    ),
                    Time.now(),
                    null,
                    MessageImportance.THIS_IS_NORMAL,
                    callDescription.fullName,
                    if (request == null) JsonNull
                    else defaultMapper.encodeToJsonElement(call.requestType, request),
                    context.attributes.outgoingTargetHost.toString(),
                )
            )
        }
    })

    client.attachFilter(object : OutgoingCallFilter.AfterCall() {
        override fun canUseContext(ctx: OutgoingCall): Boolean = true

        override suspend fun run(
            context: OutgoingCall,
            callDescription: CallDescription<*, *, *>,
            response: IngoingCallResponse<*, *>
        ) {
            val id = context.attributes[key]
            @Suppress("UNCHECKED_CAST") val call = callDescription as CallDescription<Any, Any, Any>
            sendMessage(
                DebugMessage.ClientResponse(
                    DebugContext.Client(
                        id,
                        parentContextId(),
                    ),
                    Time.now(),
                    null,
                    MessageImportance.THIS_IS_NORMAL,
                    callDescription.fullName,
                    when (response) {
                        is IngoingCallResponse.Error -> {
                            if (response.error == null) {
                                JsonNull
                            } else {
                                defaultMapper.encodeToJsonElement(call.errorType, response.error)
                            }
                        }
                        is IngoingCallResponse.Ok -> {
                            defaultMapper.encodeToJsonElement(call.successType, response.result)
                        }
                    },
                    response.statusCode.value
                )
            )
        }
    })
}
