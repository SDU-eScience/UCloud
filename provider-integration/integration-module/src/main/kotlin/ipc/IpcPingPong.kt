package dk.sdu.cloud.ipc

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.ProcessingScope
import dk.sdu.cloud.utils.secureToken
import dk.sdu.cloud.service.Loggable
import kotlin.system.exitProcess
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
data class PingResponse(
    val generation: FindByStringId,
    val shouldClose: Boolean = false
)

class IpcPingPong(
    private val ipcServer: IpcServer?,
    private val ipcClient: IpcClient?,
) {
    // NOTE(Dan): The IPC Ping+Pong protocol is meant to make sure that IM/User instances shutdown when their parent
    // has also shutdown. This is implemented through a simple ping+pong procedure through the IPC interface.
    //
    // IM/Server is responsible for responding to all ping requests with a unique token, which is not reused across
    // restarts. The token contains no other meaning than simply being unique. 
    //
    // IM/User instances will repeatedly query this token. If the token changes, or if it is unable to retrieve a
    // token then the process will eventually terminate.
    fun start() {
        if (ipcServer != null) {
            val generation = FindByStringId(secureToken(32))

            ipcServer.addHandler(PingPongIpc.ping.handler { user: IpcUser, request ->
                val shouldClose = ipcServer.clientShouldRestart(user.uid)
                PingResponse(generation, shouldClose)
            })
        } else if (ipcClient != null) {
            var lastRecordedGeneration: FindByStringId? = null
            var failures = 0
            ProcessingScope.launch {
                while (isActive) {
                    var shouldClose = false
                    try {
                        val resp = ipcClient.sendRequest(PingPongIpc.ping, Unit)
                        shouldClose = resp.shouldClose

                        if (lastRecordedGeneration == null) {
                            lastRecordedGeneration = resp.generation
                        } else if (lastRecordedGeneration != resp.generation) {
                            failures++
                        }
                    } catch (ex: Throwable) {
                        failures++
                    }

                    if (failures >= 5 || shouldClose) {
                        log.info("Shutting down, the IM/Server is no longer active!")
                        exitProcess(0)
                    }

                    delay(1000)
                }
            }
        } else {
            // Nothing to do, we simply return without doing any work.
        }
    }

    private object PingPongIpc : IpcContainer("ping") {
        val ping = updateHandler("ping", Unit.serializer(), PingResponse.serializer())
    }

    companion object : Loggable {
        override val log = logger()
    }
}

