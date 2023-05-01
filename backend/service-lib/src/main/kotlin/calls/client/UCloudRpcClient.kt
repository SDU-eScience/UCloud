@file:OptIn(ExperimentalUnsignedTypes::class)

package dk.sdu.cloud.calls.client

import com.google.flatbuffers.FlatBufferBuilder
import com.google.flatbuffers.FlatBufferBuilder.ByteBufferFactory
import com.google.flatbuffers.Table
import dk.sdu.cloud.calls.AttributeContainer
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.UCloudRpcRequest
import dk.sdu.cloud.calls.UCloudRpcSubsystem
import dk.sdu.cloud.calls.ucloudRpc
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import ucloud.RegisterCall
import ucloud.RequestHeader
import java.nio.ByteBuffer
import java.util.*
import kotlin.coroutines.coroutineContext
import kotlin.random.Random
import kotlin.random.nextInt

class OutgoingURPCCall : OutgoingCall {
    override val attributes = AttributeContainer()

    companion object : OutgoingCallCompanion<OutgoingURPCCall> {
        override val klass = OutgoingURPCCall::class
        override val attributes = AttributeContainer()
    }
}

class OutgoingURPCRequestInterceptor(
    private val defaultHost: HostInfo,
    private val scope: CoroutineScope,
) : OutgoingRequestInterceptor<OutgoingURPCCall, OutgoingURPCCall.Companion> {
    override val companion = OutgoingURPCCall.Companion

    private val wsClient = createWebsocketClient()

    private val rpcSubSystemCount = UCloudRpcSubsystem.values().size
    private val maxSystems = rpcSubSystemCount + 128

    private val streamIds = IntArray(maxSystems) { 0 }
    private val connectionMutexes = Array(maxSystems) { Mutex() }
    private val connections = arrayOfNulls<ClientWebSocketSession>(maxSystems)
    private val callTable = Array<Array<String?>>(maxSystems) { arrayOfNulls(256) }
    private val pathTable = Array(rpcSubSystemCount) {
        when (UCloudRpcSubsystem.values()[it]) {
            UCloudRpcSubsystem.USER -> "/api/user"
            UCloudRpcSubsystem.PAM -> "/api/pam"
            UCloudRpcSubsystem.ORCHESTRATOR -> "/api/orchestrator"
            UCloudRpcSubsystem.META -> "/api/meta"
            UCloudRpcSubsystem.EXTERNAL -> "/ucloud/rpc"
        }
    }

    private val newHostMutex = Mutex()
    private val hosts = arrayOfNulls<String>(maxSystems).also { array ->
        for (i in 0 until rpcSubSystemCount) {
            array[i] = buildPathToHost(defaultHost, pathTable[i])
        }
    }

    private val statusCodeTable = Array(256) {
        when (it) {
            0 -> HttpStatusCode.OK
            1 -> HttpStatusCode.BadRequest
            2 -> HttpStatusCode.Conflict
            3 -> HttpStatusCode.NotFound
            4 -> HttpStatusCode.PaymentRequired

            5 -> HttpStatusCode.Unauthorized
            6 -> HttpStatusCode.Forbidden

            7 -> HttpStatusCode.BadGateway
            8 -> HttpStatusCode.GatewayTimeout
            9 -> HttpStatusCode.InternalServerError
            else -> HttpStatusCode.InternalServerError
        }
    }

    private val invertedStatusCodeTable = Array(600) { status ->
        (statusCodeTable
            .indexOfFirst { it == HttpStatusCode.fromValue(status) }
            .takeIf { it != -1 }
            ?: 9).toUByte()
    }

    override suspend fun <R : Any, S : Any, E : Any> prepareCall(
        call: CallDescription<R, S, E>,
        request: R
    ): OutgoingURPCCall {
        return OutgoingURPCCall()
    }

    override suspend fun <R : Any, S : Any, E : Any> finalizeCall(
        call: CallDescription<R, S, E>,
        request: R,
        ctx: OutgoingURPCCall
    ): IngoingCallResponse<S, E> {
        val ucloudRpc = call.ucloudRpc
        val subsystemEnum = ucloudRpc.subsystem
        val subsystem = subsystemEnum.ordinal

        var systemIdx: Int = -1
        var callId: UShort
        var streamId: UShort

        if (subsystemEnum != UCloudRpcSubsystem.EXTERNAL) {
            systemIdx = subsystem
        } else {
            val targetHost = ctx.attributes.outgoingTargetHost
            val hostPath = buildPathToHost(targetHost, pathTable[UCloudRpcSubsystem.EXTERNAL.ordinal])
            systemIdx = hosts.indexOfFirst { it == hostPath }

            newHostMutex.withLock {
                systemIdx = hosts.indexOfFirst { it == hostPath }
                if (systemIdx != -1) return@withLock

                systemIdx = hosts.indexOfFirst { it == null }
                require(systemIdx != -1) { "No more slots available. Tried to connect to $hostPath." }

                hosts[systemIdx] = hostPath
            }
        }

        require(systemIdx in 0 until maxSystems) { "systemIdx should no longer be invalid" }

        connectionMutexes[systemIdx].withLock {
            run {
                // Make sure we have an active TCP connection to the target
                var connection = connections[systemIdx]
                if (connection != null && connection.outgoing.isClosedForSend) {
                    connection = null
                }

                if (connection == null) {
                    startProcessor(systemIdx)
                }

                require(connections[systemIdx] != null) { "Connection must be initialized" }
            }

            val requestChannel = sessionRequestChannels[systemIdx] ?: error("requestChannel was not initialized")

            run {
                // Find the call ID and potentially register it with the server
                val myCallTable = callTable[systemIdx]
                var potentialId = myCallTable.indexOf(call.fullName)
                if (potentialId == -1) {
                    potentialId = myCallTable.indexOf(null)

                    if (potentialId == -1) {
                        potentialId = Random.nextInt(myCallTable.indices)
                    }

                    val recyclableBuffer = RecyclableBufferPool.rpc.retrieve()
                    val buffer = recyclableBuffer.buffer

                    buffer.put(UCloudRpcRequest.OP_REG_CALL)
                    FlatBufferBuilder(buffer, NoGrowByteBufferFactory).let { builder ->
                        RegisterCall.createRegisterCall(
                            builder,
                            potentialId.toUShort(),
                            builder.createString(call.fullName)
                        ).also { builder.finish(it) }
                    }

                    requestChannel.send(recyclableBuffer)
                }

                require(potentialId != -1) { "potentialId must be valid now" }
                callId = potentialId.toUShort()
            }

            run {
                // TODO Register the bearer, if it has changed
            }

            run {
                // TODO Register the intent, if it has changed
            }

            run {
                // TODO Register the project
            }

            run {
                // Find a stream
                // TODO(Dan): Come up with something better, this won't work for subscriptions
                streamId = (streamIds[systemIdx]++).toUShort()
            }

            run {
                // Make the call
                val recyclableBuffer = RecyclableBufferPool.rpc.retrieve()
                val buffer = recyclableBuffer.buffer

                buffer.put(UCloudRpcRequest.OP_REQ)
                FlatBufferBuilder(buffer, NoGrowByteBufferFactory).let { builder ->
                    RequestHeader.createRequestHeader(
                        builder,
                        callId,
                        streamId,
                        0u
                    ).also { builder.finish(it) }
                }

                val pos = buffer.position()
                buffer.putInt(42)

                FlatBufferBuilder(buffer, NoGrowByteBufferFactory).let { builder ->

                }
            }
        }

        while (coroutineContext.isActive) delay(100)

//        val responseChannel = sessionResponseChannels[systemIdx] ?: error("responseChannel was not initialized")
//
//        var result: ByteBuffer? = null
//        responseChannel.takeWhile {
//            if (it.id == id) result = it
//            it.id != id
//        }.collect()
        TODO("Not yet implemented")
    }

    private val sessionRequestChannels = arrayOfNulls<SendChannel<RecyclableBuffer>>(maxSystems)
    private val sessionResponseChannels = arrayOfNulls<SharedFlow<ByteBuffer>>(maxSystems)

    private suspend fun startProcessor(systemIdx: Int) {
        val host = hosts[systemIdx]
        println("Attempting to connect to $host ($systemIdx)")
        val session = wsClient.webSocketSession {
            url(URLBuilder(host!!).build())
        }

        Arrays.fill(callTable[systemIdx], null)

        val requestChannel = Channel<RecyclableBuffer>(Channel.BUFFERED)
        val responseFlow = MutableSharedFlow<ByteBuffer>(replay = 15)

        connections[systemIdx] = session
        sessionRequestChannels[systemIdx] = requestChannel
        sessionResponseChannels[systemIdx] = responseFlow

        scope.launch {
            var shouldContinue = true
            while (isActive && shouldContinue) {
                select<Unit> {
                    requestChannel.onReceiveCatching { messageResult ->
                        val message = messageResult.getOrNull() ?: run {
                            shouldContinue = false
                            return@onReceiveCatching
                        }

                        session.outgoing.send(Frame.Binary(true, message.buffer))
                        message.recycle()
                    }

                    session.incoming.onReceiveCatching { messageResult ->
                        val message = messageResult.getOrNull() ?: run {
                            shouldContinue = false
                            return@onReceiveCatching
                        }

                        responseFlow.emit(message.buffer)
                    }
                }
            }
        }
    }

    private fun buildPathToHost(targetHost: HostInfo, path: String): String {
        val host = targetHost.host.removeSuffix("/")
        val port = targetHost.port ?: if (targetHost.scheme == "https") 443 else 80
        val scheme = when {
            targetHost.scheme == "http" -> "ws"
            targetHost.scheme == "https" -> "wss"
            port == 80 -> "ws"
            port == 443 -> "wss"
            else -> "ws"
        }

        return "$scheme://$host:$port/${path.removePrefix("/")}"
    }
}

class RecyclableBufferPool(
    private val bufferSize: Int,
    private val poolSize: Int,
) {
    private val semaphore = Semaphore(poolSize)
    private val mutex = Mutex()
    private val pool = Array(poolSize) {
        RecyclableBuffer(this, ByteBuffer.allocateDirect(bufferSize))
    }

    suspend fun retrieve(): RecyclableBuffer {
        semaphore.acquire()
        val buffer = mutex.withLock {
            pool.find { !it.__inUse } ?: error("corrupt semaphore")
        }

        buffer.__inUse = true
        return buffer
    }

    fun recycle(buffer: RecyclableBuffer) {
        buffer.__inUse = false
        buffer.buffer.clear()
        semaphore.release()
    }

    suspend inline fun useInstance(block: (buffer: ByteBuffer) -> Unit) {
        val buffer = retrieve()
        try {
            block(buffer.buffer)
        } finally {
            buffer.recycle()
        }
    }

    companion object {
        val rpc = RecyclableBufferPool(1024 * 64, 128)
    }
}

class RecyclableBuffer(
    private val pool: RecyclableBufferPool,
    val buffer: ByteBuffer,
) {
    var __inUse = false

    fun recycle() {
        pool.recycle(this)
    }
}

object NoGrowByteBufferFactory : ByteBufferFactory() {
    override fun newByteBuffer(capacity: Int): ByteBuffer = throw IllegalStateException("Buffer is not allowed to grow")
}

class TableSerializer<T : Table> : KSerializer<T> {
    override val descriptor: SerialDescriptor = Unit.serializer().descriptor
    lateinit var capturedValue: T

    override fun deserialize(decoder: Decoder): T {
        error("TableSerializer can only serialize")
    }

    override fun serialize(encoder: Encoder, value: T) {
        capturedValue = value
    }
}
