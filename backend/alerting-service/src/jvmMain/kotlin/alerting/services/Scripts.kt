package alerting.services

import dk.sdu.cloud.PageV2
import dk.sdu.cloud.alerting.api.ScriptInfo
import dk.sdu.cloud.alerting.api.Scripts
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.server.OutgoingCallResponse
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.feature
import dk.sdu.cloud.service.*
import dk.sdu.cloud.slack.api.SendAlertRequest
import dk.sdu.cloud.slack.api.SlackDescriptions
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ScriptController(
    private val ctx: Micro,
    private val serviceClient: AuthenticatedClient,
) : Controller {
    private val mutex = Mutex()
    private val registeredScripts = HashMap<String, Pair<ScriptMetadata, Long>>()
    private val lastRequested = 0L

    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        val manager = ctx.feature(ScriptManager)
        manager.onRegistration = { metadata ->
            mutex.withLock {
                registeredScripts[metadata.id] = Pair(metadata, Time.now())
            }
        }

        manager.onError = { report ->
            val message = buildString {
                appendLine("*The '${report.metadata.title}' script has failed (${report.metadata.id})*")
                appendLine()
                appendLine("```")
                appendLine(report.errorMessage)
                appendLine("```")
            }

            val response = SlackDescriptions.sendAlert.call(
                SendAlertRequest(message),
                serviceClient
            )

            if (response is IngoingCallResponse.Error<*, *> && !ctx.developmentModeEnabled) {
                log.warn(message)
            }
        }

        implement(Scripts.browse) {
            val now = Time.now()
            if (now - lastRequested > 60_000) {
                manager.requestRegistration()
            }

            ok(mutex.withLock {
                PageV2(
                    Int.MAX_VALUE,
                    registeredScripts.mapNotNull {
                        if (lastRequested - it.value.second < 120_000) {
                            ScriptInfo(it.value.first, manager.lastRun(it.value.first))
                        } else {
                            null
                        }
                     },
                    null
                )
            })
        }

        implement(Scripts.start) {
            mutex.withLock {
                for (req in request.items) {
                    registeredScripts[req.scriptId]
                        ?: throw RPCException("Unknown script: ${req.scriptId}", HttpStatusCode.BadRequest)
                }

                for (req in request.items) {
                    manager.requestRun(req.scriptId)
                }
            }

            ok(Unit)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
