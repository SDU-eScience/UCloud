package dk.sdu.cloud.debug

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.client.AtomicInteger
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.OutgoingCall
import dk.sdu.cloud.calls.client.OutgoingCallFilter
import dk.sdu.cloud.calls.client.outgoingTargetHost
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.Time
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.serializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class DebugCoroutineContext(
    val context: DebugContext,
) : AbstractCoroutineContextElement(DebugCoroutineContext) {
    companion object Key : CoroutineContext.Key<DebugCoroutineContext>
}

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
        val idGenerator = AtomicInteger(0)
    }
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

@Serializable
sealed class DebugListenRequest {
    @SerialName("init")
    @Serializable
    object Init : DebugListenRequest()

    @SerialName("context_filter")
    @Serializable
    class SetContextFilter(
        val ids: List<String>?,
        val minimumLevel: MessageImportance? = null,
        val requireTopLevel: Boolean? = null,
        val types: List<MessageType>? = null,
        val query: String? = null,
    ) : DebugListenRequest()

    @SerialName("clear")
    @Serializable
    object Clear : DebugListenRequest()
}

@Serializable
sealed class DebugSystemListenResponse {
    @SerialName("append")
    @Serializable
    data class Append(val messages: List<DebugMessage>) : DebugSystemListenResponse()

    @SerialName("clear")
    @Serializable
    object Clear : DebugSystemListenResponse()

    @SerialName("acknowledge")
    @Serializable
    object Acknowledge : DebugSystemListenResponse()
}

object DebugApi : CallDescriptionContainer("debug") {
    val listen = call<DebugListenRequest, DebugSystemListenResponse, CommonErrorMessage>("listen") {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ
        }

        websocket("/api/debug")
    }
}

enum class MessageType {
    SERVER,
    DATABASE,
    CLIENT,
    LOG
}

class DebugSystem : MicroFeature {
    private var developmentMode: Boolean = false
    private val mutex = Mutex()
    private val contextGraph = HashMap<String, ArrayList<String>>()
    private val reverseContext = HashMap<String, ArrayList<String>>()
    private val messages = ArrayList<DebugMessage>()

    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        developmentMode = ctx.developmentModeEnabled
        configure(ctx.server)

        val key = AttributeKey<String>("debug-id")
        ctx.client.attachFilter(object : OutgoingCallFilter.BeforeCall() {
            private val baseKey = "Client-"
            private val idGenerator = AtomicInteger(0)
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

        ctx.client.attachFilter(object : OutgoingCallFilter.AfterCall() {
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
                                    defaultMapper.encodeToJsonElement(call.errorType, response.error!!)
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

        ctx.server.attachFilter(object : IngoingCallFilter.AfterParsing() {
            override fun canUseContext(ctx: IngoingCall): Boolean = true
            override suspend fun run(context: IngoingCall, call: CallDescription<*, *, *>, request: Any) {
                if (call.fullName == "debug.listen") return
                @Suppress("UNCHECKED_CAST")
                call as CallDescription<Any, Any, Any>

                sendMessage(
                    DebugMessage.ServerRequest(
                        DebugContext.Server(context.jobIdOrNull ?: "Unknown"),
                        Time.now(),
                        context.securityPrincipalOrNull,
                        MessageImportance.IMPLEMENTATION_DETAIL,
                        call.fullName,
                        defaultMapper.encodeToJsonElement(call.requestType, request),
                    )
                )
            }
        })

        ctx.server.attachFilter(object : IngoingCallFilter.AfterResponse() {
            override fun canUseContext(ctx: IngoingCall): Boolean = true
            override suspend fun run(
                context: IngoingCall,
                call: CallDescription<*, *, *>,
                request: Any?,
                result: OutgoingCallResponse<*, *>
            ) {
                if (call.fullName == "debug.listen") return
                @Suppress("UNCHECKED_CAST")
                call as CallDescription<Any, Any, Any>

                sendMessage(
                    DebugMessage.ServerResponse(
                        DebugContext.Server(context.jobIdOrNull ?: "Unknown"),
                        Time.now(),
                        rpcContext()?.call?.securityPrincipalOrNull,
                        MessageImportance.THIS_IS_NORMAL,
                        call.fullName,
                        when (val res = result) {
                            is OutgoingCallResponse.Ok -> {
                                defaultMapper.encodeToJsonElement(call.successType, res.result)
                            }

                            is OutgoingCallResponse.Error -> {
                                if (res.error != null) {
                                    defaultMapper.encodeToJsonElement(call.errorType, res.error)
                                } else {
                                    JsonNull
                                }
                            }

                            else -> JsonNull
                        },
                        result.statusCode.value
                    )
                )
            }
        })
    }

    data class DebugSession(
        val streamId: String,
        val session: WSSession,
        var query: String? = null,
        var requireTopLevel: Boolean = false,
        var filterTypes: List<MessageType> = listOf(MessageType.SERVER),
        var minimumLevel: MessageImportance = MessageImportance.THIS_IS_NORMAL,
        var interestedIn: List<String>? = null,
    )

    private val sessions = ArrayList<DebugSession>()

    private fun configure(server: RpcServer) {
        if (!developmentMode) return

        try {
            val key = AttributeKey<DebugSession>("debug-session")
            server.implement(DebugApi.listen) {
                withContext<WSCall> {
                    when (request) {
                        DebugListenRequest.Init -> {
                            val debugSession = DebugSession(ctx.streamId, ctx.session)
                            ctx.session.attributes[key] = debugSession
                            sessions.add(debugSession)

                            ctx.session.addOnCloseHandler {
                                mutex.withLock {
                                    val idx = sessions.indexOfFirst { it.session == ctx.session }
                                    if (idx != -1) {
                                        sessions.removeAt(idx)
                                    }
                                }
                            }

                            while (coroutineContext.isActive) {
                                delay(500)
                            }

                            okContentAlreadyDelivered()
                        }

                        is DebugListenRequest.SetContextFilter -> {
                            val debugSession = ctx.session.attributes[key]
                            debugSession.interestedIn = request.ids
                            if (request.minimumLevel != null) debugSession.minimumLevel = request.minimumLevel
                            if (request.types != null) debugSession.filterTypes = request.types
                            if (request.query != null) debugSession.query = request.query
                            if (request.requireTopLevel != null) debugSession.requireTopLevel = request.requireTopLevel

                            mutex.withLock {
                                debugSession.session.sendMessage(debugSession.streamId, DebugSystemListenResponse.Clear,
                                    DebugSystemListenResponse.serializer())

                                debugSession.session.sendMessage(debugSession.streamId, DebugSystemListenResponse.Append(
                                    messages.filter { shouldSendMessage(debugSession, it) }
                                ), DebugSystemListenResponse.serializer())
                            }

                            ok(DebugSystemListenResponse.Acknowledge)
                        }

                        DebugListenRequest.Clear -> {
                            val debugSession = ctx.session.attributes[key]
                            mutex.withLock {
                                messages.clear()
                                debugSession.session.sendMessage(
                                    debugSession.streamId, DebugSystemListenResponse.Clear,
                                    DebugSystemListenResponse.serializer()
                                )

                            }
                            ok(DebugSystemListenResponse.Acknowledge)
                        }
                    }
                }
            }
        } catch (ex: Throwable) {
            log.warn("Failed to start DebugSystem: ${ex.stackTraceToString()}")
        }
    }

    private fun shouldSendMessage(session: DebugSession, message: DebugMessage): Boolean {
        if (session.requireTopLevel && message.context.parent != null) return false
        val allIds = session.interestedIn?.flatMap { (reverseContext[it] ?: emptyList()) + it }?.toSet()
        if (allIds != null && message.context.id !in allIds) return false
        if (message.importance.ordinal < session.minimumLevel.ordinal) return false
        if (message.messageType !in session.filterTypes) return false
        val query = session.query?.takeIf { it.isNotBlank() }?.lowercase()
        if (query != null) {
            val matchesUsername = (message.principal?.username ?: "")?.lowercase()?.contains(query)
            val callName = when (message) {
                is DebugMessage.ClientRequest -> message.call
                is DebugMessage.ClientResponse -> message.call
                is DebugMessage.ServerRequest -> message.call
                is DebugMessage.ServerResponse -> message.call
                else -> null
            }?.lowercase() ?: ""
            val matchesCall = callName.contains(query)

            if (!matchesUsername && !matchesCall) return false
        }
        return true
    }

    suspend fun sendMessage(message: DebugMessage) {
        if (!developmentMode) {
            // Log the message in some way
        } else {
            mutex.withLock {
                val existingEntry = contextGraph[message.context.id]
                if (existingEntry == null) {
                    val chain = ArrayList<String>()
                    val parent = message.context.parent
                    if (parent != null) {
                        val elements = contextGraph[parent]
                        elements?.forEach {
                            reverseContext[it]?.add(message.context.id)
                        }
                        if (elements != null) chain.addAll(elements)
                    }
                    chain.add(message.context.id)

                    contextGraph[message.context.id] = chain
                    reverseContext[message.context.id] = ArrayList()

                    message.context.depth = chain.size - 1
                } else {
                    message.context.depth = existingEntry.size - 1
                }

                messages.add(message)

                sessions.forEach { session ->
                    if (shouldSendMessage(session, message)) {
                        session.session.sendMessage(
                            session.streamId,
                            DebugSystemListenResponse.Append(listOf(message)),
                            DebugSystemListenResponse.serializer()
                        )
                    }
                }
            }
        }
    }

    companion object : MicroFeatureFactory<DebugSystem, Unit>, Loggable {
        override val log = logger()
        override fun create(config: Unit): DebugSystem = DebugSystem()
        override val key = MicroAttributeKey<DebugSystem>("debug-system")
    }
}

suspend inline fun rpcContext(): RpcCoroutineContext? = coroutineContext[RpcCoroutineContext]
suspend inline fun parentContextId(): String? {
    val ctx = coroutineContext
    return ctx[DebugCoroutineContext]?.context?.id ?: rpcContext()?.call?.jobIdOrNull
}

val logIdGenerator = AtomicInteger(0)
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
        Array::class,
        List::class,
        Set::class,
        Collection::class -> {
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

