package dk.sdu.cloud.debug

import calls.server.RpcCoroutineContext
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.micro.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

@Serializable
sealed class DebugContext {
    abstract val id: String
    abstract val parent: String?

    @SerialName("server")
    @Serializable
    data class Server(override val id: String, override val parent: String? = null) : DebugContext()

    @SerialName("client")
    @Serializable
    data class Client(override val id: String, override val parent: String? = null) : DebugContext()

    @SerialName("job")
    @Serializable
    data class Job(override val id: String, override val parent: String? = null) : DebugContext()
}

@Serializable
sealed class DebugMessage {
    abstract val context: DebugContext
    abstract val timestamp: Long
    abstract val principal: SecurityPrincipal?
    abstract val importance: MessageImportance

    @SerialName("client_request")
    @Serializable
    data class ClientRequest(
        override val context: DebugContext,
        override val timestamp: Long,
        override val principal: SecurityPrincipal?,
        override val importance: MessageImportance,
        val call: String?,
        val payload: JsonElement?,
        val resolvedUrl: String,
        val method: String
    ) : DebugMessage()

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
    ) : DebugMessage()

    @SerialName("server_request")
    @Serializable
    data class ServerRequest(
        override val context: DebugContext,
        override val timestamp: Long,
        override val principal: SecurityPrincipal?,
        override val importance: MessageImportance,
        val call: String?,
        val payload: JsonElement?,
        val method: String
    ) : DebugMessage()

    @SerialName("client_response")
    @Serializable
    data class ServerResponse(
        override val context: DebugContext,
        override val timestamp: Long,
        override val principal: SecurityPrincipal?,
        override val importance: MessageImportance,
        val call: String?,
        val response: JsonElement?,
        val responseCode: Int
    ) : DebugMessage()

    @SerialName("database_connection")
    @Serializable
    data class DatabaseConnection(
        override val context: DebugContext,
        override val timestamp: Long,
        override val principal: SecurityPrincipal?,
        override val importance: MessageImportance,
    ) : DebugMessage()

    @SerialName("database_transaction")
    @Serializable
    data class DatabaseTransaction(
        override val context: DebugContext,
        override val timestamp: Long,
        override val principal: SecurityPrincipal?,
        override val importance: MessageImportance,
    ) : DebugMessage()

    @SerialName("database_query")
    @Serializable
    data class DatabaseQuery(
        override val context: DebugContext,
        override val timestamp: Long,
        override val principal: SecurityPrincipal?,
        override val importance: MessageImportance,
        val query: String,
        val parameters: JsonObject,
    ) : DebugMessage()
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
     * Indicates that something might be wrong, but not for certain. An operator/developer might want to see this, but
     * it is not critical.
     */
    THIS_IS_ODD,

    /**
     * A clear message that something is wrong. An operator/developer want to see this as soon as possible, but it can
     * probably wait until the next morning.
     */
    THIS_IS_WRONG,

    /**
     * This should never happen. This event needs to be investigated immediately.
     *
     * If you don't think it is appropriate to wake somebody up to tell them about this event then you should pick a
     * different category.
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
    class SetContextFilter(val ids: List<String>?) : DebugListenRequest()
}

@Serializable
sealed class DebugSystemListenResponse {
    @SerialName("append")
    @Serializable
    data class Append(val messages: List<DebugMessage>) : DebugSystemListenResponse()

    @SerialName("clear")
    @Serializable
    object Clear : DebugSystemListenResponse()
}

object DebugApi : CallDescriptionContainer("debug") {
    val listen = call<DebugListenRequest, DebugSystemListenResponse, CommonErrorMessage>("listen") {
        websocket("/api/debug")
    }
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
    }

    data class DebugSession(
        val streamId: String,
        val session: WSSession,
        var interestedIn: List<String>?,
    )

    private val sessions = ArrayList<DebugSession>()

    private fun configure(server: RpcServer) {
        if (!developmentMode) return

        val key = AttributeKey<DebugSession>("debug-session")
        server.implement(DebugApi.listen) {
            withContext<WSCall> {
                when (request) {
                    DebugListenRequest.Init -> {
                        val debugSession = DebugSession(ctx.streamId, ctx.session, null)
                        ctx.session.attributes[key] = debugSession

                        ctx.session.addOnCloseHandler {
                            mutex.withLock {
                                val idx = sessions.indexOfFirst { it.session == ctx.session }
                                if (idx != -1) {
                                    sessions.removeAt(idx)
                                }

                                sendWSMessage(DebugSystemListenResponse.Append(messages.takeLast(100)))
                            }
                        }
                    }

                    is DebugListenRequest.SetContextFilter -> {
                        ctx.session.attributes[key].interestedIn = request.ids

                        mutex.withLock {
                            sendWSMessage(DebugSystemListenResponse.Clear)

                            if (request.ids != null) {
                                val allIds = request.ids.flatMap { reverseContext[it] ?: emptyList() }.toSet()
                                sendWSMessage(DebugSystemListenResponse.Append(
                                    messages.filter { it.context.id in allIds })
                                )
                            }
                        }
                    }
                }
            }
        }
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
                        val elements = contextGraph.getValue(parent)
                        elements.forEach {
                            reverseContext.getValue(it).add(message.context.id)
                        }
                        chain.addAll(elements)
                    }
                    chain.add(message.context.id)

                    contextGraph[message.context.id] = chain
                    reverseContext[message.context.id] = ArrayList()
                }

                messages.add(message)

                sessions.forEach { session ->
                    val allIds = session.interestedIn?.flatMap { reverseContext[it] ?: emptyList() }?.toSet()

                    if (allIds == null || message.context.id in allIds) {
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

    companion object : MicroFeatureFactory<DebugSystem, Unit> {
        override fun create(config: Unit): DebugSystem = DebugSystem()
        override val key = MicroAttributeKey<DebugSystem>("debug-system")
    }
}

suspend inline fun rpcContext(): RpcCoroutineContext? = coroutineContext[RpcCoroutineContext]