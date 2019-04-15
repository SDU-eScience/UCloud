package dk.sdu.cloud.app.services

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.api.FollowStdStreamsRequest
import dk.sdu.cloud.app.api.FollowStdStreamsResponse
import dk.sdu.cloud.app.api.InternalFollowStdStreamsRequest
import dk.sdu.cloud.app.api.JobCompletedEvent
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.JobStateChange
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.StartJobRequest
import dk.sdu.cloud.app.api.SubmitFileToComputation
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.app.http.JOB_MAX_TIME
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.bearerAuth
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withoutAuthentication
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.events.EventProducer
import dk.sdu.cloud.file.api.DownloadByURI
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.*
import kotlinx.coroutines.io.ByteReadChannel

class JobOrchestrator<DBSession>(
    private val serviceCloud: AuthenticatedClient,

    private val accountingEventProducer: EventProducer<JobCompletedEvent>,

    private val db: DBSessionFactory<DBSession>,
    private val jobVerificationService: JobVerificationService<DBSession>,
    private val computationBackendService: ComputationBackendService,
    private val jobFileService: JobFileService,
    private val jobDao: JobDao<DBSession>
) {
    /**
     * Shared error handling for methods that work with a live job.
     *
     * Will return the result if [rethrow] is false, otherwise the original exception is thrown without wrapping.
     */
    @Suppress("TooGenericExceptionCaught")
    private inline fun <R> withJobExceptionHandler(jobId: String, rethrow: Boolean = true, body: () -> R): R? {
        return try {
            body()
        } catch (ex: Exception) {
            val message =
                if (ex is RPCException && ex.httpStatusCode != HttpStatusCode.InternalServerError) ex.why else null

            if (ex !is RPCException) {
                log.warn("Unexpected exception caught while handling a job callback! ($jobId)")
                log.warn(ex.stackTraceToString())
            } else {
                log.debug("Expected exception caught while handling a job callback! ($jobId)")
                log.debug(ex.stackTraceToString())
            }

            try {
                val existingJob = db.withTransaction { session ->
                    jobDao.updateStatus(session, jobId, message ?: "Internal error")
                    jobDao.findOrNull(session, jobId)
                }

                failJob(existingJob)
            } catch (cleanupException: Exception) {
                log.info("Exception while cleaning up (most likely to job not existing)")
                log.info(cleanupException.stackTraceToString())
            }

            if (rethrow) {
                log.debug("Rethrowing exception")
                throw ex
            } else {
                log.debug("Not rethrowing exception")
                null
            }
        }
    }

    private fun failJob(existingJob: VerifiedJobWithAccessToken?) {
        // If we don't check for an existing failure state we can loop forever in a crash
        if (existingJob != null && existingJob.job.currentState != JobState.FAILURE) {
            val stateChange = JobStateChange(existingJob.job.id, JobState.FAILURE)
            handleStateChange(existingJob, stateChange)
        }
    }

    suspend fun startJob(
        req: StartJobRequest,
        principal: DecodedJWT,
        cloud: AuthenticatedClient
    ): String {
        val (initialState, jobWithToken) = validateJob(req, principal, cloud)
        db.withTransaction { session -> jobDao.create(session, jobWithToken) }
        handleStateChange(jobWithToken, initialState)
        return initialState.systemId
    }

    private suspend fun validateJob(
        req: StartJobRequest,
        principal: DecodedJWT,
        userCloud: AuthenticatedClient
    ): Pair<JobStateChange, VerifiedJobWithAccessToken> {
        val backend = computationBackendService.getAndVerifyByName(resolveBackend(req.backend))
        val unverifiedJob = UnverifiedJob(req, principal)
        val jobWithToken = jobVerificationService.verifyOrThrow(unverifiedJob, userCloud)
        backend.jobVerified.call(jobWithToken.job, serviceCloud).orThrow()

        return Pair(JobStateChange(jobWithToken.job.id, JobState.VALIDATED), jobWithToken)
    }

    fun handleProposedStateChange(
        event: JobStateChange,
        newStatus: String?,
        securityPrincipal: SecurityPrincipal
    ) {
        withJobExceptionHandler(event.systemId) {
            val proposedState = event.newState
            val jobWithToken = findJobForId(event.systemId)
            val (job, _) = jobWithToken
            computationBackendService.getAndVerifyByName(job.backend, securityPrincipal)

            log.info("Moving from ${job.currentState} to $proposedState")
            val validStates = validStateTransitions[job.currentState] ?: emptySet()
            if (proposedState in validStates) {
                if (proposedState != job.currentState) {
                    handleStateChange(jobWithToken, event, newStatus)
                }
            } else {
                log.info("NOPE!")
                throw JobException.BadStateTransition(job.currentState, event.newState)
            }
        }
    }

    fun handleAddStatus(jobId: String, newStatus: String, securityPrincipal: SecurityPrincipal) {
        // We don't cancel the job if this fails
        val (job, _) = findJobForId(jobId)
        computationBackendService.getAndVerifyByName(job.backend, securityPrincipal)

        db.withTransaction {
            jobDao.updateStatus(it, jobId, newStatus)
        }
    }

    suspend fun handleJobComplete(
        jobId: String,
        wallDuration: SimpleDuration,
        success: Boolean,
        securityPrincipal: SecurityPrincipal
    ) {
        withJobExceptionHandler(jobId) {
            val (job, _) = findJobForId(jobId)
            computationBackendService.getAndVerifyByName(job.backend, securityPrincipal)

            handleProposedStateChange(
                JobStateChange(jobId, if (success) JobState.SUCCESS else JobState.FAILURE),
                null,
                securityPrincipal
            )

            accountingEventProducer.produce(
                JobCompletedEvent(
                    jobId,
                    job.owner,
                    wallDuration,
                    job.nodes,
                    System.currentTimeMillis(),
                    NameAndVersion(job.application.metadata.name, job.application.metadata.version),
                    success
                )
            )
        }
    }

    suspend fun handleIncomingFile(
        jobId: String,
        securityPrincipal: SecurityPrincipal,
        filePath: String,
        length: Long,
        data: ByteReadChannel,
        needsExtraction: Boolean
    ) {
        withJobExceptionHandler(jobId) {
            val jobWithToken = findJobForId(jobId)
            computationBackendService.getAndVerifyByName(jobWithToken.job.backend, securityPrincipal)

            jobFileService.acceptFile(jobWithToken, filePath, length, data, needsExtraction)
        }
    }

    suspend fun followStreams(
        request: FollowStdStreamsRequest
    ): FollowStdStreamsResponse {
        val (job, _) = findJobForId(request.jobId)
        val backend = computationBackendService.getAndVerifyByName(job.backend, null)
        val internalResult = backend.follow.call(
            InternalFollowStdStreamsRequest(
                job,
                request.stdoutLineStart,
                request.stdoutMaxLines,
                request.stderrLineStart,
                request.stderrMaxLines
            ),
            serviceCloud
        ).orThrow()

        return FollowStdStreamsResponse(
            internalResult.stdout,
            internalResult.stdoutNextLine,
            internalResult.stderr,
            internalResult.stderrNextLine,
            NameAndVersion(job.application.metadata.name, job.application.metadata.version),
            job.currentState,
            job.status,
            job.currentState.isFinal(),
            job.id,
            jobFileService.jobFolder(job),
            job.application.metadata
        )
    }

    /**
     * Handles a state change
     */
    private fun handleStateChange(
        jobWithToken: VerifiedJobWithAccessToken,
        event: JobStateChange,
        newStatus: String? = null
    ) {
        OrchestrationScope.launch {
            withJobExceptionHandler(event.systemId, rethrow = false) {
                db.withTransaction(autoFlush = true) {
                    jobDao.updateStateAndStatus(it, event.systemId, event.newState, newStatus)
                }

                val (job, _) = jobWithToken
                val backend = computationBackendService.getAndVerifyByName(job.backend)

                when (event.newState) {
                    JobState.VALIDATED -> {
                        // Ask backend to prepare the job
                        transferFilesToCompute(jobWithToken)

                        val jobWithNewState = job.copy(currentState = JobState.PREPARED)
                        val jobWithTokenAndNewState = jobWithToken.copy(job = jobWithNewState)

                        handleStateChange(
                            jobWithTokenAndNewState,
                            JobStateChange(jobWithNewState.id, JobState.PREPARED)
                        )
                    }

                    JobState.PREPARED -> {
                        backend.jobPrepared.call(jobWithToken.job, serviceCloud).orThrow()
                    }

                    JobState.SCHEDULED, JobState.RUNNING -> {
                        // Do nothing (apart from updating state). It is mostly working.
                    }

                    JobState.TRANSFER_SUCCESS -> {
                        jobFileService.initializeResultFolder(jobWithToken)
                    }

                    JobState.SUCCESS, JobState.FAILURE -> {
                        // This one should _NEVER_ throw an exception
                        val resp = backend.cleanup.call(job, serviceCloud)
                        if (resp is IngoingCallResponse.Error) {
                            log.info("unable to clean up for job $job.")
                            log.info(resp.toString())
                        }
                    }
                }
            }
        }
    }

    // TODO Move this
    private suspend fun transferFilesToCompute(jobWithToken: VerifiedJobWithAccessToken) {
        val (job, accessToken) = jobWithToken
        val backend = computationBackendService.getAndVerifyByName(job.backend)
        coroutineScope {
            job.files.map { file ->
                async {
                    runCatching {
                        val userCloud = serviceCloud.withoutAuthentication().bearerAuth(accessToken)
                        val fileStream = FileDescriptions.download.call(
                            DownloadByURI(file.sourcePath, null),
                            userCloud
                        ).orRethrowAs { throw JobException.TransferError() }.asIngoing()

                        backend.submitFile.call(
                            SubmitFileToComputation(
                                job.id,
                                file.id,
                                BinaryStream.outgoingFromChannel(
                                    fileStream.channel,
                                    contentType = fileStream.contentType
                                        ?: ContentType.Application.OctetStream,
                                    contentLength = fileStream.length
                                )
                            ),
                            serviceCloud
                        ).orThrow()
                    }
                }
            }.awaitAll().firstOrNull { it.isFailure }.let {
                if (it != null) {
                    throw it.exceptionOrNull()!!
                }
            }
        }
    }

    fun lookupOwnJob(jobId: String, securityPrincipal: SecurityPrincipal): VerifiedJob {
        val jobWithToken = findJobForId(jobId)
        computationBackendService.getAndVerifyByName(jobWithToken.job.backend, securityPrincipal)
        return jobWithToken.job
    }

    suspend fun removeExpiredJobs() {
        val expired = System.currentTimeMillis() - JOB_MAX_TIME
        db.withTransaction { session ->
            jobDao.findJobsCreatedBefore(session, expired).forEach { job ->
                failJob(job)
            }
        }
    }

    private fun findJobForId(id: String): VerifiedJobWithAccessToken =
        db.withTransaction { session ->
            jobDao.findOrNull(session, id) ?: throw JobException.NotFound("Job: $id")
        }

    companion object : Loggable {
        override val log = logger()

        private val finalStates = JobState.values().asSequence().filter { it.isFinal() }.toSet()
        private val validStateTransitions: Map<JobState, Set<JobState>> = mapOf(
            JobState.VALIDATED to (setOf(JobState.PREPARED) + finalStates),
            JobState.PREPARED to (setOf(JobState.SCHEDULED, JobState.RUNNING, JobState.TRANSFER_SUCCESS) + finalStates),
            // We allow scheduled to skip running in case of quick jobs
            JobState.SCHEDULED to (setOf(JobState.RUNNING, JobState.TRANSFER_SUCCESS) + finalStates),
            JobState.RUNNING to (setOf(JobState.TRANSFER_SUCCESS, JobState.FAILURE)),
            JobState.TRANSFER_SUCCESS to (setOf(JobState.SUCCESS, JobState.FAILURE)),
            // In case of really bad failures we allow for a "failure -> failure" transition
            JobState.FAILURE to setOf(JobState.FAILURE),
            JobState.SUCCESS to emptySet()
        )
    }
}

inline fun <T : Any, E : Any> IngoingCallResponse<T, E>.orThrowOnError(
    onError: (IngoingCallResponse.Error<T, E>) -> Nothing
): IngoingCallResponse.Ok<T, E> {
    return when (this) {
        is IngoingCallResponse.Ok -> this
        is IngoingCallResponse.Error -> onError(this)
        else -> throw IllegalStateException()
    }
}

fun resolveBackend(backend: String?): String = backend ?: "abacus"
