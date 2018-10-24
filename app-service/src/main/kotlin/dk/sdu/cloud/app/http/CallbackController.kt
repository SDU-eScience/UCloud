package dk.sdu.cloud.app.http

import dk.sdu.cloud.app.api.ComputationCallbackDescriptions
import dk.sdu.cloud.app.api.JobStateChange
import dk.sdu.cloud.app.services.JobOrchestrator
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.service.securityPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route

class CallbackController<DBSession>(
    private val jobOrchestrator: JobOrchestrator<DBSession>
) : Controller {
    override val baseContext = ComputationCallbackDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(ComputationCallbackDescriptions.submitFile) { multipart ->
            logEntry(log, multipart)

            multipart.receiveBlocks { req ->
                jobOrchestrator.handleIncomingFile(
                    req.jobId,
                    call.securityPrincipal,
                    req.filePath,
                    req.fileData.payload
                )
            }

            ok(Unit)
        }

        implement(ComputationCallbackDescriptions.requestStateChange) { req ->
            logEntry(log, req)

            jobOrchestrator.handleProposedStateChange(JobStateChange(req.id, req.newState), call.securityPrincipal)
            ok(Unit)
        }

        implement(ComputationCallbackDescriptions.addStatus) { req ->
            logEntry(log, req)
            throw RPCException.fromStatusCode(HttpStatusCode.NotImplemented)
        }

        implement(ComputationCallbackDescriptions.completed) { req ->
            logEntry(log, req)

            jobOrchestrator.handleJobComplete(req.id, req.wallDuration, req.success, call.securityPrincipal)
            ok(Unit)
        }

        implement(ComputationCallbackDescriptions.lookup) { req ->
            logEntry(log, req)

            ok(jobOrchestrator.lookupOwnJob(req.id, call.securityPrincipal))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
