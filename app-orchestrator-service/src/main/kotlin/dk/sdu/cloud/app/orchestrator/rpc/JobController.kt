package dk.sdu.cloud.app.orchestrator.rpc

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.fs.api.AppFileSystems
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.services.*
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.TokenExtensionRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.ClientAndBackend
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.bearerAuth
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.bearer
import dk.sdu.cloud.calls.server.requiredAuthScope
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.calls.server.securityToken
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.MultiPartUploadDescriptions
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.mapItems
import io.ktor.http.HttpStatusCode

internal const val JOB_MAX_TIME = 1000 * 60 * 60 * 24L

class JobController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val jobOrchestrator: JobOrchestrator<DBSession>,
    private val jobDao: JobDao<DBSession>,
    private val streamFollowService: StreamFollowService<DBSession>,
    private val tokenValidation: TokenValidation<DecodedJWT>,
    private val serviceClient: AuthenticatedClient,
    private val vncService: VncService<DBSession>,
    private val webService: WebService<DBSession>
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(JobDescriptions.findById) {
            val (job, _) = db.withTransaction { session ->
                jobDao.findOrNull(session, request.id, ctx.securityToken)
            } ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            ok(job.toJobWithStatus())
        }

        implement(JobDescriptions.listRecent) {
            val result = db.withTransaction {
                jobDao.list(
                    it,
                    ctx.securityToken,
                    request.normalize(),
                    request.order ?: SortOrder.DESCENDING,
                    request.sortBy ?: JobSortBy.CREATED_AT,
                    request.minTimestamp,
                    request.maxTimestamp
                )
            }.mapItems { it.job.toJobWithStatus() }

            ok(result)
        }

        implement(JobDescriptions.start) {
            log.debug("Extending token")
            val maxTime = request.maxTime
            if (maxTime != null && maxTime.toMillis() > JOB_MAX_TIME) {
                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Maximum job time exceeded")
            }
            val extensionResponse = AuthDescriptions.tokenExtension.call(
                TokenExtensionRequest(
                    ctx.bearer!!,
                    listOf(
                        MultiPartUploadDescriptions.simpleUpload.requiredAuthScope.toString(),
                        FileDescriptions.download.requiredAuthScope.toString(),
                        FileDescriptions.createDirectory.requiredAuthScope.toString(),
                        FileDescriptions.stat.requiredAuthScope.toString(),
                        FileDescriptions.extract.requiredAuthScope.toString(),
                        AppFileSystems.view.requiredAuthScope.toString()
                    ),
                    JOB_MAX_TIME
                ),
                serviceClient
            )

            if (extensionResponse !is IngoingCallResponse.Ok) {
                error(CommonErrorMessage("Unauthorized"), HttpStatusCode.Unauthorized)
                return@implement
            }

            log.debug("Validating response")
            val extendedToken = tokenValidation.validate(extensionResponse.result.accessToken)

            log.debug("Creating client")
            val userClient =
                ClientAndBackend(serviceClient.client, serviceClient.companion).bearerAuth(extendedToken.token)

            log.debug("Starting job")
            val jobId = jobOrchestrator.startJob(request, extendedToken, userClient)

            log.debug("Complete")
            ok(JobStartedResponse(jobId))
        }

        implement(JobDescriptions.cancel) {
            jobOrchestrator.handleProposedStateChange(
                JobStateChange(request.jobId, JobState.CANCELING),
                newStatus = "Job is cancelling...",
                jobOwner = ctx.securityToken
            )

            ok(Unit)
        }

        implement(JobDescriptions.follow) {
            ok(streamFollowService.followStreams(request, ctx.securityPrincipal.username))
        }

        implement(JobDescriptions.queryVncParameters) {
            ok(vncService.queryVncParameters(request.jobId, ctx.securityPrincipal.username).exportForEndUser())
        }

        implement(JobDescriptions.queryWebParameters) {
            ok(webService.queryWebParameters(request.jobId, ctx.securityPrincipal.username).exportForEndUser())
        }
    }

    private fun VerifiedJob.toJobWithStatus(): JobWithStatus {
        val job = this
        return JobWithStatus(
            job.id,
            job.owner,
            job.currentState,
            job.status,
            job.application.metadata.name,
            job.application.metadata.version,
            job.createdAt,
            job.modifiedAt,
            job.application.metadata
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
