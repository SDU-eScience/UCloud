package dk.sdu.cloud.app.orchestrator.rpc

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.fs.api.AppFileSystems
import dk.sdu.cloud.app.orchestrator.api.JobDescriptions
import dk.sdu.cloud.app.orchestrator.api.JobSortBy
import dk.sdu.cloud.app.orchestrator.api.JobStartedResponse
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.JobStateChange
import dk.sdu.cloud.app.orchestrator.api.JobWithStatus
import dk.sdu.cloud.app.orchestrator.api.SortOrder
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.app.orchestrator.services.JobDao
import dk.sdu.cloud.app.orchestrator.services.JobOrchestrator
import dk.sdu.cloud.app.orchestrator.services.StreamFollowService
import dk.sdu.cloud.app.orchestrator.services.VncService
import dk.sdu.cloud.app.orchestrator.services.WebService
import dk.sdu.cloud.app.orchestrator.services.exportForEndUser
import dk.sdu.cloud.app.orchestrator.services.findOrNull
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.TokenExtensionRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
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
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.mapItems
import io.ktor.http.HttpStatusCode

internal const val JOB_MAX_TIME = 1000 * 60 * 60 * 200L

class JobController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val jobOrchestrator: JobOrchestrator<DBSession>,
    private val jobDao: JobDao<DBSession>,
    private val streamFollowService: StreamFollowService<DBSession>,
    private val userClientFactory: (String?, String?) -> AuthenticatedClient,
    private val serviceClient: AuthenticatedClient,
    private val vncService: VncService<DBSession>,
    private val webService: WebService<DBSession>
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(JobDescriptions.findById) {
            val (job) = db.withTransaction { session ->
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
                    request.maxTimestamp,
                    request.filter,
                    request.application,
                    request.version
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

            // Check name
            if (request.name != null) {
                val invalidChars = Regex("""([./\\\n])""")
                if (invalidChars.containsMatchIn(request.name!!)) {
                    error(CommonErrorMessage("Provided name not allowed"), HttpStatusCode.BadRequest)
                    return@implement
                }
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
            val jobId = jobOrchestrator.startJob(request, ctx.securityToken, refreshToken, userClient)

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

        implement(JobDescriptions.followWS) {
            streamFollowService.followWSStreams(request, ctx.securityPrincipal.username, this)
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
        val expiresAt = job.startedAt?.let {
            job.startedAt + job.maxTime.toMillis()
        }

        return JobWithStatus(
            job.id,
            job.name,
            job.owner,
            job.currentState,
            job.status,
            job.application.metadata.name,
            job.application.metadata.version,
            job.createdAt,
            job.modifiedAt,
            expiresAt,
            job.application.metadata
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
