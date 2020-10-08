package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.app.kubernetes.api.AppKubernetesShell
import dk.sdu.cloud.app.kubernetes.services.K8Dependencies
import dk.sdu.cloud.app.orchestrator.api.ComputationCallbackDescriptions
import dk.sdu.cloud.app.orchestrator.api.ShellRequest
import dk.sdu.cloud.app.orchestrator.api.ShellResponse
import dk.sdu.cloud.calls.AttributeKey
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.k8.*
import io.ktor.http.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.slf4j.Logger

private typealias ShellSession = ExecContext

class ShellController(private val k8: K8Dependencies) : Controller {
    private val shellSessionKey = AttributeKey<ShellSession>("shell-session")

    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(AppKubernetesShell.open) {
            withContext<WSCall> {
                val cRequest = request // capture the request. this allows for smart casting later
                when (cRequest) {
                    is ShellRequest.Initialize -> {
                        // NOTE(Dan): This follows the same model we used for VNC. This means that we do _not_ perform any
                        // additional security apart from being in possession of the job ID. The job ID itself is generated
                        // in a secure fashion, however, I think we should change this when reworking the API.

                        val podName = k8.nameAllocator.jobIdAndRankToPodName(cRequest.jobId, cRequest.rank)
                        val namespace = k8.nameAllocator.jobIdToNamespace(cRequest.jobId)

                        k8.client.exec(
                            KubernetesResources.pod.withNameAndNamespace(podName, namespace),
                            listOf("sh", "-c",
                                "TERM=xterm-256color; " +
                                    "export TERM; " +
                                    "([ -x /bin/fish ] && exec /bin/fish) || " +
                                    "([ -x /usr/bin/fish ] && exec /usr/bin/fish) || " +
                                    "([ -x /bin/zsh ] && exec /bin/zsh) || " +
                                    "([ -x /usr/bin/zsh ] && exec /usr/bin/zsh) || " +
                                    "([ -x /bin/bash ] && exec /bin/bash) || " +
                                    "([ -x /usr/bin/bash ] && exec /usr/bin/bash) || " +
                                    "exec /bin/sh"
                            )
                        ) {
                            val shellSession = this
                            ctx.session.attributes[shellSessionKey] = shellSession

                            ctx.session.addOnCloseHandler {
                                closeSession(shellSession)
                            }

                            sendWSMessage(ShellResponse.Initialized())

                            resizes.send(ExecResizeMessage(cRequest.cols, cRequest.rows))

                            coroutineScope {
                                launch {
                                    outputs.consumeEach { f ->
                                        sendWSMessage(ShellResponse.Data(f.bytes.toString(Charsets.UTF_8)))
                                    }
                                }
                            }
                        }
                    }

                    is ShellRequest.Input -> {
                        // Don't audit input. It will happen very often and produce extreme amounts of noise.
                        ctx.audit.skipAuditing = true

                        val session = ctx.session.attributes.getOrNull(shellSessionKey)
                            ?: throw RPCException("Not found", HttpStatusCode.NotFound)
                        session.stdin.send(cRequest.data.toByteArray(Charsets.UTF_8))
                        ok(ShellResponse.Acknowledged())
                    }

                    is ShellRequest.Resize -> {
                        val session = ctx.session.attributes.getOrNull(shellSessionKey)
                            ?: throw RPCException("Not found", HttpStatusCode.NotFound)
                        session.resizes.send(ExecResizeMessage(cRequest.cols, cRequest.rows))
                        ok(ShellResponse.Acknowledged())
                    }
                }
            }
        }
    }

    private fun closeSession(shellSession: ShellSession) {
        shellSession.outputs.cancel()
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}