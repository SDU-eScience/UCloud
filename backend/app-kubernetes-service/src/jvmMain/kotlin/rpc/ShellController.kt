package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.app.kubernetes.api.AppKubernetesShell
import dk.sdu.cloud.app.kubernetes.services.K8Dependencies
import dk.sdu.cloud.app.kubernetes.services.SessionDao
import dk.sdu.cloud.app.orchestrator.api.InteractiveSessionType
import dk.sdu.cloud.app.orchestrator.api.ShellRequest
import dk.sdu.cloud.app.orchestrator.api.ShellResponse
import dk.sdu.cloud.calls.AttributeKey
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.k8.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.slf4j.Logger

private typealias ShellSession = ExecContext

class ShellController(
    private val k8: K8Dependencies,
    private val db: DBContext,
    private val sessions: SessionDao,
) : Controller {
    private val shellSessionKey = AttributeKey<ShellSession>("shell-session")

    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(AppKubernetesShell.open) {
            try {
                withContext<WSCall> {
                    when (val cRequest = request) {
                        is ShellRequest.Initialize -> {
                            val jobAndRank = sessions.findSessionOrNull(
                                db,
                                cRequest.sessionIdentifier,
                                InteractiveSessionType.SHELL
                            ) ?: throw RPCException("Unknown shell session. Reloading the page might " +
                                    "resolve this issue.", HttpStatusCode.NotFound)

                            val podName = k8.nameAllocator.jobIdAndRankToPodName(jobAndRank.jobId, jobAndRank.rank)
                            val namespace = k8.nameAllocator.jobIdToNamespace(jobAndRank.jobId)

                            k8.client.exec(
                                KubernetesResources.pod.withNameAndNamespace(podName, namespace),
                                listOf(
                                    "sh", "-c",
                                    "TERM=xterm-256color; " +
                                            "export TERM; " +
                                            "([ -x /bin/bash ] && exec /bin/bash) || " +
                                            "([ -x /usr/bin/bash ] && exec /usr/bin/bash) || " +
                                            "([ -x /bin/zsh ] && exec /bin/zsh) || " +
                                            "([ -x /usr/bin/zsh ] && exec /usr/bin/zsh) || " +
                                            "([ -x /bin/fish ] && exec /bin/fish) || " +
                                            "([ -x /usr/bin/fish ] && exec /usr/bin/fish) || " +
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
            } catch (ex: Throwable) {
                if (ex.javaClass.canonicalName == "kotlinx.coroutines.JobCancellationException" ||
                    ex.message?.contains("404 Not Found") == true) {
                    ok(ShellResponse.Acknowledged())
                    runCatching {
                        withContext<WSCall> { ctx.session.close() }
                    }
                }
                throw ex
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
