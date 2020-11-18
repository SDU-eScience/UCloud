package dk.sdu.cloud.app.orchestrator.rpc

import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.app.orchestrator.api.Jobs
import dk.sdu.cloud.app.orchestrator.services.JobOrchestrator
import dk.sdu.cloud.app.orchestrator.services.JobQueryService
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.mapItems
import dk.sdu.cloud.service.toActor
import io.ktor.http.*

class JobController(
    private val jobQueryService: JobQueryService,
    private val jobOrchestrator: JobOrchestrator,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Jobs.create) {
            verifySlaFromPrincipal()
            ok(jobOrchestrator.startJob(request, ctx.bearer!!, ctx.securityPrincipal.toActor(), ctx.project))
        }

        implement(Jobs.delete) {
            verifySlaFromPrincipal()
            jobOrchestrator.cancel(request, ctx.securityPrincipal.toActor())
            ok(Unit)
        }

        implement(Jobs.retrieve) {
            verifySlaFromPrincipal()
            ok(jobQueryService.retrieve(ctx.securityPrincipal.toActor(), ctx.project, request.id, request).job)
        }

        implement(Jobs.browse) {
            verifySlaFromPrincipal()
            ok(
                jobQueryService
                    .browse(ctx.securityPrincipal.toActor(), ctx.project, request.normalize(), request)
                    .mapItems { it.job }
            )
        }

        implement(Jobs.follow) {
            verifySlaFromPrincipal()
            TODO()
        }

        implement(Jobs.extend) {
            verifySlaFromPrincipal()
            jobOrchestrator.extendDuration(request, ctx.securityPrincipal.toActor())
            ok(Unit)
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
