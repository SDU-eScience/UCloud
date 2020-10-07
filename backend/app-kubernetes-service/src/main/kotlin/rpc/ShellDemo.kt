package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.app.kubernetes.api.DemoRequest
import dk.sdu.cloud.app.kubernetes.api.DemoResponse
import dk.sdu.cloud.app.kubernetes.api.ShellDemo
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.WSCall
import dk.sdu.cloud.calls.server.sendWSMessage
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.k8.ExecContext
import dk.sdu.cloud.service.k8.KubernetesClient
import dk.sdu.cloud.service.k8.KubernetesResources
import dk.sdu.cloud.service.k8.exec
import io.ktor.http.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import kotlin.collections.HashMap

class ShellDemo(private val client: KubernetesClient) : Controller {
    private data class ShellSession(val id: String, val session: ExecContext)
    private val openSessions = HashMap<String, ShellSession>()
    private val mutex = Mutex()

    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(ShellDemo.demo) {
            withContext<WSCall> {
                val cRequest = request // capture the request. this allows for smart casting later
                if (cRequest is DemoRequest.Initialize) {
                    val id = cRequest.jobId + "/" + UUID.randomUUID().toString()
                    client.exec(
                        KubernetesResources.pod.withNameAndNamespace("test-64c67d47dc-h2ptr", "app-kubernetes"),
                        listOf("sh", "-c",
                            "TERM=xterm-256color; " +
                                "export TERM; " +
                                "([ -x /bin/fish ] && exec /bin/fish) || " +
                                "([ -x /usr/bin/fish ] && exec /usr/bin/fish) || " +
                                "([ -x /bin/zsh ] && exec /bin/zsh) || " +
                                "([ -x /usr/bin/zsh ] && exec /usr/bin/zsh) || " +
                                "([ -x /bin/bash ] && exec /bin/bash) || " +
                                "([ -x /usr/bin/bash ] && exec /usr/bin/bash) || " +
                                "exec /bin/sh")
                    ) {
                        val shellSession = ShellSession(id, this)
                        mutex.withLock {
                            openSessions[id] = shellSession
                        }

                        coroutineScope {
                            launch {
                                outputs.consumeEach { f ->
                                    sendWSMessage(DemoResponse.Data(id, f.bytes.toString(Charsets.UTF_8)))
                                }
                            }
                        }

                        ctx.session.addOnCloseHandler {
                            closeSession(shellSession)
                        }

                        sendWSMessage(DemoResponse.Initialized(id))
                    }
                } else if (cRequest is DemoRequest.Input) {
                    val session = openSessions[cRequest.streamId] ?: throw RPCException("Not found", HttpStatusCode.NotFound)
                    session.session.stdin.send(cRequest.data.toByteArray(Charsets.UTF_8))
                    ok(DemoResponse.Acknowledged())
                }
            }
        }
    }

    private suspend fun closeSession(shellSession: ShellSession) {
        mutex.withLock {
            val session = openSessions.remove(shellSession.id)?.session ?: return
            session.outputs.cancel()
        }
    }
}