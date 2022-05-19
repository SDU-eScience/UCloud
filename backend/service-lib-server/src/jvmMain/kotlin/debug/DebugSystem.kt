package dk.sdu.cloud.debug

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.coroutines.coroutineContext

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

class DebugSystemFeature : MicroFeature, DebugSystem {
    private var developmentMode: Boolean = false
    private val mutex = Mutex()
    private val contextGraph = HashMap<String, ArrayList<String>>()
    private val reverseContext = HashMap<String, ArrayList<String>>()
    private val messages = ArrayList<DebugMessage>()

    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        developmentMode = ctx.developmentModeEnabled
        configure(ctx.server)

        installCommon(ctx.client)

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
                        null, // TODO(Dan): Retrieve this
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

    override suspend fun sendMessage(message: DebugMessage) {
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

    companion object : MicroFeatureFactory<DebugSystemFeature, Unit>, Loggable {
        override val log = logger()
        override fun create(config: Unit): DebugSystemFeature = DebugSystemFeature()
        override val key = MicroAttributeKey<DebugSystemFeature>("debug-system")
    }
}
