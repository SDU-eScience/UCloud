package dk.sdu.cloud.app.http

import dk.sdu.cloud.app.api.ComputationCallbackDescriptions
import dk.sdu.cloud.app.api.JobStateChange
import dk.sdu.cloud.app.services.JobExecutionService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.service.securityPrincipal
import io.ktor.routing.Route

class CallbackController<DBSession>(
    private val jobExecutionService: JobExecutionService<DBSession>
) : Controller {
    override val baseContext = ComputationCallbackDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(ComputationCallbackDescriptions.submitFile) { req ->
            logEntry(log, req)
        }

        implement(ComputationCallbackDescriptions.requestStateChange) { req ->
            logEntry(log, req)

            jobExecutionService.handleProposedStateChange(JobStateChange(req.id, req.newState), call.securityPrincipal)
            ok(Unit)
        }

        implement(ComputationCallbackDescriptions.addStatus) { req ->
            logEntry(log, req)
        }

        implement(ComputationCallbackDescriptions.completed) { req ->
            logEntry(log, req)

            jobExecutionService.handleJobComplete(req.id, req.wallDuration, call.securityPrincipal)
            ok(Unit)
        }

        implement(ComputationCallbackDescriptions.lookup) { req ->
            logEntry(log, req)

        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
