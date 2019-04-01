package dk.sdu.cloud.app.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.api.ComputationCallbackDescriptions
import dk.sdu.cloud.app.api.JobStateChange
import dk.sdu.cloud.app.services.JobOrchestrator
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import io.ktor.application.call
import io.ktor.http.HttpHeaders
import io.ktor.request.header

class CallbackController<DBSession>(
    private val jobOrchestrator: JobOrchestrator<DBSession>
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(ComputationCallbackDescriptions.submitFile) {
            withContext<HttpCall> {
                val length = ctx.call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()

                if (length != null) {
                    jobOrchestrator.handleIncomingFile(
                        request.jobId,
                        ctx.securityPrincipal,
                        request.filePath,
                        length,
                        request.fileData.asIngoing().channel,
                        request.needsExtraction == true
                    )
                    ok(Unit)
                } else {
                    error(CommonErrorMessage("Missing file length"))
                }
            }
        }

        implement(ComputationCallbackDescriptions.requestStateChange) {
            jobOrchestrator.handleProposedStateChange(
                JobStateChange(request.id, request.newState),
                request.newStatus,
                ctx.securityPrincipal
            )
            ok(Unit)
        }

        implement(ComputationCallbackDescriptions.addStatus) {
            jobOrchestrator.handleAddStatus(request.id, request.status, ctx.securityPrincipal)
            ok(Unit)
        }

        implement(ComputationCallbackDescriptions.completed) {
            jobOrchestrator.handleJobComplete(request.id, request.wallDuration, request.success, ctx.securityPrincipal)
            ok(Unit)
        }

        implement(ComputationCallbackDescriptions.lookup) {
            ok(jobOrchestrator.lookupOwnJob(request.id, ctx.securityPrincipal))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
