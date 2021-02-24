package dk.sdu.cloud.app.orchestrator.rpc

import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.app.orchestrator.services.JobOrchestrator
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.toActor

class CallbackController(
    private val jobOrchestrator: JobOrchestrator
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(JobsControl.update) {
            jobOrchestrator.updateState(request, ctx.securityPrincipal.toActor())
            ok(Unit)
        }

        implement(JobsControl.chargeCredits) {
            ok(jobOrchestrator.charge(request, ctx.securityPrincipal.toActor()))
        }

        implement(JobsControl.retrieve) {
            ok(jobOrchestrator.retrieveAsProvider(request, ctx.securityPrincipal.toActor()))
        }

        implement(JobsControl.submitFile) {
            jobOrchestrator.submitFile(request, ctx.securityPrincipal.toActor())
            ok(Unit)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
