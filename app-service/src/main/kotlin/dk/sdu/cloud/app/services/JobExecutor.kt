package dk.sdu.cloud.app.services

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.api.AppRequest
import dk.sdu.cloud.app.api.JobCompletedEvent
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.JobStateChange
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.copyFrom
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.client.jwtAuth
import dk.sdu.cloud.file.api.DownloadByURI
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.MappedEventProducer
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.orThrow
import io.ktor.http.isSuccess
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.awaitAll
import kotlinx.coroutines.experimental.io.jvm.javaio.toInputStream
import kotlinx.coroutines.experimental.runBlocking

// This does the management (but doesn't run jobs)
class JobExecutionService<DBSession>(
    private val serviceCloud: RefreshingJWTAuthenticatedCloud,

    private val stateChangeProducer: MappedEventProducer<String, JobStateChange>,
    private val accountingEventProducer: MappedEventProducer<String, JobCompletedEvent>,

    private val db: DBSessionFactory<DBSession>,
    private val jobVerificationService: JobVerificationService<DBSession>,
    private val computationBackendService: ComputationBackendService,
    private val jobDao: JobDao<DBSession>
) {
    suspend fun startJob(
        req: AppRequest.Start,
        principal: DecodedJWT,
        cloud: AuthenticatedCloud
    ): String {
        val (initialState, jobWithToken) = validateJob(req, principal, cloud)
        stateChangeProducer.emit(initialState)
        db.withTransaction { session -> jobDao.create(session, jobWithToken) }
        return initialState.systemId
    }

    fun handleProposedStateChange(event: JobStateChange, securityPrincipal: SecurityPrincipal) {
        // Validate that we can make this change
        // TODO We need to verify securityPrincipal corresponds to the computational backend that is handling the job
    }

    fun handleJobComplete(jobId: String, wallDuration: SimpleDuration, securityPrincipal: SecurityPrincipal) {
        // TODO We need to verify securityPrincipal corresponds to the computational backend that is handling the job
    }

    fun handleStateChange(event: JobStateChange) {
        // Retrieve job details
        // Call appropriate handler based on state
        runBlocking {
            val jobWithToken = findJobForId(event.systemId)
            val (job, _) = jobWithToken
            val backend = computationBackendService.getByName(job.backend)

            when (event.newState) {
                JobState.VALIDATED -> {
                    // Ask backend to prepare the job
                    transferFilesFromJob(jobWithToken)
                    backend.jobPrepared.call(job, serviceCloud).orThrow()
                }

                JobState.PREPARED, JobState.SCHEDULED -> {
                    // Do nothing (apart from updating state). It is mostly working.
                }

                JobState.SUCCESS, JobState.FAILURE -> {
                    // Do cleanup
                    val success = event.newState == JobState.SUCCESS
                }
            }
        }
    }

    private suspend fun validateJob(
        req: AppRequest.Start,
        principal: DecodedJWT,
        cloud: AuthenticatedCloud
    ): Pair<JobStateChange, VerifiedJobWithAccessToken> {
        val descriptions = computationBackendService.getByName(resolveBackend(req.backend))
        val unverifiedJob = UnverifiedJob(req, principal)
        val jobWithToken = jobVerificationService.verifyOrThrow(unverifiedJob, cloud)
        descriptions.jobVerified.call(jobWithToken.job, cloud).orThrow()

        return Pair(JobStateChange(jobWithToken.job.id, JobState.VALIDATED), jobWithToken)
    }

    private suspend fun transferFilesFromJob(jobWithToken: VerifiedJobWithAccessToken) {
        val (job, accessToken) = jobWithToken
        val descriptions = computationBackendService.getByName(job.backend)
        job.files.map { file ->
            async {
                val userCloud = serviceCloud.parent.jwtAuth(accessToken)
                val fileStream = (FileDescriptions.download.call(
                    DownloadByURI(file.sourcePath, null),
                    userCloud
                ) as? RESTResponse.Ok)?.response?.content?.toInputStream()

                // TODO We need to be a bit careful throwing exceptions in a co-routine
                if (fileStream == null) {
                    TODO()
                }

                val (statusCode, _) = descriptions.submitFile(
                    cloud = serviceCloud,
                    job = job,
                    parameterName = file.id,
                    causedBy = null,
                    dataWriter = { it.copyFrom(fileStream) }
                )

                if (!statusCode.isSuccess()) TODO()
            }
        }.awaitAll()
    }

    private fun findJobForId(id: String): VerifiedJobWithAccessToken = TODO()

    companion object : Loggable {
        override val log = logger()
    }
}

inline fun <T, E> RESTResponse<T, E>.orThrowOnError(
    onError: (RESTResponse.Err<T, E>) -> Nothing
): RESTResponse.Ok<T, E> {
    return when (this) {
        is RESTResponse.Ok -> this
        is RESTResponse.Err -> onError(this)
        else -> throw IllegalStateException()
    }
}

fun resolveBackend(backend: String?): String = backend ?: "abacus"
