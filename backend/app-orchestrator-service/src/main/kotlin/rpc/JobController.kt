package dk.sdu.cloud.app.orchestrator.rpc

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.app.orchestrator.UserClientFactory
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.services.*
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.TokenExtensionRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.MultiPartUploadDescriptions
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.project.api.ViewMemberInProjectRequest
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay

class JobController(
    private val jobQueryService: JobQueryService,
    private val jobOrchestrator: JobOrchestrator,
    private val streamFollowService: StreamFollowService,
    private val userClientFactory: UserClientFactory,
    private val serviceClient: AuthenticatedClient,
    private val machineCache: MachineTypeCache
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Jobs.create) {
            verifySlaFromPrincipal()
            jobOrchestrator.startJob(request, ctx.bearer!!, ctx.securityPrincipal, ctx.project)
            TODO()
        }

        implement(Jobs.delete) {
            verifySlaFromPrincipal()
            TODO()
        }

        implement(Jobs.retrieve) {
            verifySlaFromPrincipal()
            TODO()
        }

        implement(Jobs.browse) {
            verifySlaFromPrincipal()
            TODO()
        }

        implement(Jobs.follow) {
            verifySlaFromPrincipal()
            TODO()
        }

        implement(Jobs.extend) {
            verifySlaFromPrincipal()
            TODO()
        }

        /*
        implement(JobDescriptions.findById) {
            verifySlaFromPrincipal()
            ok(jobQueryService.asJobWithStatus(jobQueryService.findById(ctx.securityToken, request.id)))
        }

        implement(JobDescriptions.listRecent) {
            verifySlaFromPrincipal()
            val project = run {
                val projectId = ctx.project
                if (projectId != null) {
                    val memberInProject = Projects.viewMemberInProject.call(
                        ViewMemberInProjectRequest(projectId, ctx.securityPrincipal.username),
                        serviceClient
                    ).orThrow()

                    ProjectContext(projectId, memberInProject.member.role)
                } else {
                    null
                }
            }

            ok(
                jobQueryService.listRecent(
                    ctx.securityToken,
                    request.normalize(),
                    request,
                    project
                )
            )
        }

        implement(JobDescriptions.start) {
            verifySlaFromPrincipal()
            log.debug("Extending token")

            val extensionResponse = AuthDescriptions.tokenExtension.call(
                TokenExtensionRequest(
                    ctx.bearer!!,
                    listOf(
                        MultiPartUploadDescriptions.simpleUpload.requiredAuthScope.toString(),
                        FileDescriptions.download.requiredAuthScope.toString(),
                        FileDescriptions.createDirectory.requiredAuthScope.toString(),
                        FileDescriptions.stat.requiredAuthScope.toString(),
                        FileDescriptions.deleteFile.requiredAuthScope.toString()
                    ),
                    1000L * 60 * 60 * 5,
                    allowRefreshes = true
                ),
                serviceClient
            )

            if (extensionResponse !is IngoingCallResponse.Ok) {
                error(CommonErrorMessage("Unauthorized"), HttpStatusCode.Unauthorized)
                return@implement
            }

            val refreshToken = extensionResponse.result.refreshToken!!
            val userClient = userClientFactory(null, refreshToken)

            log.debug("Starting job")
            val jobId = jobOrchestrator.startJob(request, ctx.securityToken, refreshToken, userClient, ctx.project)

            log.debug("Complete")
            ok(JobStartedResponse(jobId))
        }

        implement(JobDescriptions.cancel) {
            verifySlaFromPrincipal()
            jobOrchestrator.handleProposedStateChange(
                JobStateChange(request.jobId, JobState.CANCELING),
                newStatus = "User initiated cancel",
                jobOwner = ctx.securityToken
            )

            ok(Unit)
        }

        implement(JobDescriptions.extendDuration) {
            verifySlaFromPrincipal()
            jobOrchestrator.extendDuration(
                request.jobId,
                request.extendWith,
                ctx.securityToken
            )

            ok(Unit)
        }

        implement(JobDescriptions.follow) {
            verifySlaFromPrincipal()
            ok(streamFollowService.followStreams(request, ctx.securityToken))
        }

        implement(JobDescriptions.followWS) {
            verifySlaFromPrincipal()
            streamFollowService.followWSStreams(request, ctx.securityToken, this).join()
        }

        implement(JobDescriptions.queryVncParameters) {
            verifySlaFromPrincipal()
            ok(vncService.queryVncParameters(request.jobId, ctx.securityPrincipal.username).exportForEndUser())
        }

        implement(JobDescriptions.queryWebParameters) {
            verifySlaFromPrincipal()
            ok(webService.queryWebParameters(request.jobId, ctx.securityPrincipal.username).exportForEndUser())
        }

        implement(JobDescriptions.queryShellParameters) {
            verifySlaFromPrincipal()
            // TODO We will rework the API before adding more backends
            ok(QueryShellParametersResponse("/api/app/compute/kubernetes/shell"))
        }

        implement(JobDescriptions.machineTypes) {
            verifySlaFromPrincipal()

            val machines = machineCache.machines.get(Unit)
                ?: throw RPCException("Internal server error", HttpStatusCode.InternalServerError)
            ok(machines)
        }
         */
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
