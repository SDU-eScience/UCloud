package dk.sdu.cloud.ipc

import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Logger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class IpcProxyServer {
    fun init(server: IpcServer, client: AuthenticatedClient) {
        server.addHandler(IpcHandler(IpcProxyRequestInterceptor.IPC_PROXY_METHOD) { _, req ->
            val proxyRequest = defaultMapper.decodeFromJsonElement<IpcProxyRequest>(req.params)

            val call: CallDescription<*, *, *>? = when (proxyRequest.call) {
                JobsControl.update.fullName -> {
                    // TODO Verify the request
                    JobsControl.update
                }

                else -> null
            }

            runBlocking {
                @Suppress("UNCHECKED_CAST")
                val response = client.client.call(
                    call as CallDescription<Any, Any, Any>,
                    defaultMapper.decodeFromJsonElement(call.requestType, proxyRequest.request),
                    client.backend,
                    client.authenticator,
                    afterHook = client.afterHook
                ).orThrow()

                defaultMapper.encodeToJsonElement(call.successType, response) as JsonObject
            }
        })
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
