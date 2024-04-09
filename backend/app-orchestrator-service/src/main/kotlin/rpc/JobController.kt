package dk.sdu.cloud.app.orchestrator.rpc

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ProviderRegisteredResource
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.statistics
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.services.JobResourceService
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.actorAndProject
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

class JobController(
    private val jobs: JobResourceService,
    private val micro: Micro,
) : Controller {

    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Jobs.browse) {
            ok(jobs.browseBy(actorAndProject, request))
        }

        implement(Jobs.retrieve) {
            ok(jobs.retrieve(actorAndProject, request) ?: throw RPCException("Unknown job", HttpStatusCode.NotFound))
        }

        implement(Jobs.create) {
            ok(jobs.create(actorAndProject, request))
        }

        implement(JobsControl.browse) {
            ok(jobs.browseBy(actorAndProject, request))
        }

        implement(JobsControl.retrieve) {
            ok(jobs.retrieve(actorAndProject, request) ?: throw RPCException("Unknown job", HttpStatusCode.NotFound))
        }

        implement(JobsControl.update) {
            ok(jobs.addUpdate(actorAndProject, request))
        }

        implement(Jobs.updateAcl) {
            jobs.updateAcl(actorAndProject, request)
            ok(BulkResponse(request.items.map { }))
        }

        implement(Jobs.terminate) {
            jobs.terminate(actorAndProject, request)
            ok(BulkResponse(request.items.map { }))
        }

        implement(Jobs.follow) {
            jobs.follow(this)
            ok(JobsFollowResponse(emptyList(), emptyList()))
        }

        implement(Jobs.extend) {
            jobs.extend(actorAndProject, request)
            ok(BulkResponse(request.items.map { }))
        }

        implement(Jobs.suspend) {
            jobs.suspendOrUnsuspendJob(actorAndProject, request, shouldSuspend = true)
            ok(BulkResponse(request.items.map { }))
        }

        implement(Jobs.unsuspend) {
            jobs.suspendOrUnsuspendJob(actorAndProject, request, shouldSuspend = false)
            ok(BulkResponse(request.items.map { }))
        }

        implement(Jobs.openInteractiveSession) {
            ok(jobs.openInteractiveSession(actorAndProject, request))
        }

        implement(Jobs.retrieveProducts) {
            ok(jobs.retrieveProducts(actorAndProject))
        }

        implement(Jobs.retrieveUtilization) {
            ok(jobs.retrieveUtilization(actorAndProject, request))
        }

        implement(Jobs.search) {
            ok(jobs.browseBy(actorAndProject, request, request.query))
        }

        implement(Jobs.init) {
            ok(jobs.initializeProviders(actorAndProject))
        }

        implement(JobsControl.register) {
            ok(jobs.register(actorAndProject, request))
        }

        implement(JobsControl.chargeCredits) {
            ok(jobs.chargeOrCheckCredits(actorAndProject, request, checkOnly = false))
        }

        implement(JobsControl.checkCredits) {
            ok(jobs.chargeOrCheckCredits(actorAndProject, request, checkOnly = true))
        }

        implement(Statistics.retrieveStatistics) {
            ok(statistics.retrieveStatistics(ctx.responseAllocator, actorAndProject, request.start, request.end))
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
