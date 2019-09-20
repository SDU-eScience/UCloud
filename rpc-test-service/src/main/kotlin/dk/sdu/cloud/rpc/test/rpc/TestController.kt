package dk.sdu.cloud.rpc.test.rpc

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.rpc.test.api.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.rpc.test.b.api.PingRequest
import dk.sdu.cloud.rpc.test.b.api.TestB
import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.*

class TestController(
    private val client: AuthenticatedClient,
    private val wsClient: AuthenticatedClient
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(TestA.ping) {
            val client = if (request.ws == true) wsClient else client
            coroutineScope {
                repeat(request.parallelism ?: 16) {
                    launch {
                        repeat(100) {
                            TestB.ping.call(PingRequest(UUID.randomUUID().toString()), client)
                        }
                    }
                }
            }
            ok(Unit)
        }

        implement(TestA.processing) {
            val client = if (request.ws == true) wsClient else client
            coroutineScope {
                repeat(request.parallelism ?: 16) {
                    launch {
                        repeat(100) {
                            TestB.processing.call(Unit, client)
                        }
                    }
                }
            }

            ok(Unit)
        }

        implement(TestA.processingSuspend) {
            val client = if (request.ws == true) wsClient else client
            coroutineScope {
                repeat(request.parallelism ?: 16) {
                    launch {
                        repeat(100) {
                            TestB.suspendingProcessing.call(Unit, client)
                        }
                    }
                }
            }

            ok(Unit)
        }

        implement(TestA.processingSuspend2) {
            val client = if (request.ws == true) wsClient else client
            coroutineScope {
                repeat(request.parallelism ?: 16) {
                    launch {
                        repeat(100) {
                            TestB.suspendingProcessing2.call(Unit, client)
                        }
                    }
                }
            }

            ok(Unit)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
