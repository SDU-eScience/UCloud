package dk.sdu.cloud.debug

import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.RpcClient
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.defaultMapper
import kotlinx.serialization.json.JsonNull

fun DebugSystem.registerMiddleware(client: RpcClient, server: RpcServer?) {
    client.debugSystem = this
    installCommon(client)
    if (server != null) {
        server.attachFilter(object : IngoingCallFilter.AfterParsing() {
            override fun canUseContext(ctx: IngoingCall): Boolean = true
            override suspend fun run(context: IngoingCall, call: CallDescription<*, *, *>, request: Any) {
                @Suppress("UNCHECKED_CAST")
                call as CallDescription<Any, Any, Any>

                serverRequest(
                    MessageImportance.THIS_IS_NORMAL,
                    call.fullName,
                    defaultMapper.encodeToJsonElement(call.requestType, request),
                )
            }
        })

        server.attachFilter(object : IngoingCallFilter.AfterResponse() {
            override fun canUseContext(ctx: IngoingCall): Boolean = true
            override suspend fun run(
                context: IngoingCall,
                call: CallDescription<*, *, *>,
                request: Any?,
                result: OutgoingCallResponse<*, *>,
                responseTimeMs: Long
            ) {
                @Suppress("UNCHECKED_CAST")
                call as CallDescription<Any, Any, Any>

                serverResponse(
                    when {
                        responseTimeMs >= 300 || result.statusCode.value in 500..599 -> MessageImportance.THIS_IS_WRONG
                        responseTimeMs >= 150 || result.statusCode.value in 400..499 -> MessageImportance.THIS_IS_ODD
                        else -> MessageImportance.THIS_IS_NORMAL
                    },
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
                    result.statusCode,
                    responseTimeMs
                )
            }
        })
    }
}
