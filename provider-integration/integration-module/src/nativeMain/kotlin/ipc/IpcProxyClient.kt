package dk.sdu.cloud.ipc

import dk.sdu.cloud.calls.AttributeContainer
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.OutgoingCall
import dk.sdu.cloud.calls.client.OutgoingCallCompanion
import dk.sdu.cloud.calls.client.OutgoingRequestInterceptor
import dk.sdu.cloud.defaultMapper
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.reflect.KClass

class IpcProxyCall : OutgoingCall {
    override val attributes: AttributeContainer = AttributeContainer()

    companion object : OutgoingCallCompanion<IpcProxyCall> {
        override val attributes: AttributeContainer = AttributeContainer()
        override val klass: KClass<IpcProxyCall> = IpcProxyCall::class
    }
}

class IpcProxyRequestInterceptor(
    private val ipcClient: IpcClient,
) : OutgoingRequestInterceptor<IpcProxyCall, IpcProxyCall.Companion> {
    override val companion: IpcProxyCall.Companion = IpcProxyCall.Companion

    override suspend fun <R : Any, S : Any, E : Any> prepareCall(
        call: CallDescription<R, S, E>,
        request: R
    ): IpcProxyCall {
        return IpcProxyCall()
    }

    override suspend fun <R : Any, S : Any, E : Any> finalizeCall(
        call: CallDescription<R, S, E>,
        request: R,
        ctx: IpcProxyCall
    ): IngoingCallResponse<S, E> {
        val response = ipcClient.sendRequest(
            JsonRpcRequest(
                IPC_PROXY_METHOD,
                defaultMapper.encodeToJsonElement(
                    IpcProxyRequest(
                        call.fullName,
                        defaultMapper.encodeToJsonElement(call.requestType, request),
                    )
                ) as JsonObject
            )
        )

        return when (response) {
            is JsonRpcResponse.Success -> {
                try {
                    IngoingCallResponse.Ok(
                        defaultMapper.decodeFromJsonElement(
                            call.successType,
                            response.result
                        ),
                        HttpStatusCode.OK,
                        ctx
                    )
                } catch (ex: Throwable) {
                    IngoingCallResponse.Error(null, HttpStatusCode.BadGateway, ctx)
                }
            }

            is JsonRpcResponse.Error -> {
                IngoingCallResponse.Error(
                    runCatching {
                        response.error.data?.let {
                            defaultMapper.decodeFromJsonElement(call.errorType, it)
                        }
                    }.getOrNull(),
                    HttpStatusCode.fromValue(response.error.code),
                    ctx,
                )
            }
        }
    }

    companion object {
        const val IPC_PROXY_METHOD = "proxy"
    }
}

@Serializable
data class IpcProxyRequest(val call: String, val request: JsonElement)
