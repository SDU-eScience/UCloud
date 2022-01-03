package dk.sdu.cloud.app.orchestrator.rpc

import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.util.asController
import dk.sdu.cloud.app.orchestrator.api.Jobs
import dk.sdu.cloud.app.orchestrator.api.JobsFollowResponse
import dk.sdu.cloud.app.orchestrator.services.JobOrchestrator
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.actorAndProject
import io.ktor.http.*

class JobController(
    private val orchestrator: JobOrchestrator,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        orchestrator.asController().configure(rpcServer)

        implement(Jobs.terminate) {
            ok(orchestrator.terminate(actorAndProject, request))
        }

        implement(Jobs.follow) {
            orchestrator.follow(this)
            ok(JobsFollowResponse(emptyList(), emptyList()))
        }

        implement(Jobs.extend) {
            ok(orchestrator.extendDuration(actorAndProject, request))
        }

        implement(Jobs.openInteractiveSession) {
            ok(orchestrator.openInteractiveSession(actorAndProject, request))
        }

        implement(Jobs.retrieveUtilization) {
            ok(orchestrator.retrieveUtilization(actorAndProject, request))
        }
    }

    private fun CallHandler<*, *, *>.verifySlaFromPrincipal() {
        val principal = ctx.securityPrincipal
        if (principal.role == Role.USER && !principal.twoFactorAuthentication &&
            principal.principalType == "password"
        ) {
            throw RPCException(
                "2FA must be activated before application services are available",
                HttpStatusCode.Forbidden
            )
        }

        if (principal.role in Roles.END_USER && !principal.serviceAgreementAccepted) {
            throw RPCException("Service license agreement not yet accepted", HttpStatusCode.Forbidden)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
