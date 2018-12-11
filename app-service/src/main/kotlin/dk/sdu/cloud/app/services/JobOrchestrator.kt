package dk.sdu.cloud.app.services

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.api.AppRequest
import dk.sdu.cloud.app.api.FollowStdStreamsRequest
import dk.sdu.cloud.app.api.FollowStdStreamsResponse
import dk.sdu.cloud.app.api.InternalFollowStdStreamsRequest
import dk.sdu.cloud.app.api.JobCompletedEvent
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.JobStateChange
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.SubmitFileToComputation
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.MultipartRequest
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.client.StreamingFile
import dk.sdu.cloud.client.jwtAuth
import dk.sdu.cloud.file.api.DownloadByURI
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.MappedEventProducer
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.awaitAllOrThrow
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.okChannelOrNull
import dk.sdu.cloud.service.orThrow
import dk.sdu.cloud.service.safeAsync
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.runBlocking

class JobOrchestrator<DBSession>(
    private val serviceCloud: RefreshingJWTAuthenticatedCloud,

    private val stateChangeProducer: MappedEventProducer<String, JobStateChange>,
    private val accountingEventProducer: MappedEventProducer<String, JobCompletedEvent>,

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
    private suspend inline fun <R> withJobExceptionHandler(jobId: String, rethrow: Boolean = true, body: () -> R): R? {
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

                // If we don't check for an existing failure state we can loop forever in a crash
                if (existingJob != null && existingJob.job.currentState != JobState.FAILURE) {
                    stateChangeProducer.emit(JobStateChange(jobId, JobState.FAILURE))
                }

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

    suspend fun startJob(
        req: AppRequest.Start,
        principal: DecodedJWT,
        cloud: AuthenticatedCloud
    ): String {
        val (initialState, jobWithToken) = validateJob(req, principal, cloud)
        db.withTransaction { session -> jobDao.create(session, jobWithToken) }
        stateChangeProducer.emit(initialState)
        return initialState.systemId
    }

    private suspend fun validateJob(
        req: AppRequest.Start,
        principal: DecodedJWT,
        userCloud: AuthenticatedCloud
    ): Pair<JobStateChange, VerifiedJobWithAccessToken> {
        val backend = computationBackendService.getAndVerifyByName(resolveBackend(req.backend))
        val unverifiedJob = UnverifiedJob(req, principal)
        val jobWithToken = jobVerificationService.verifyOrThrow(unverifiedJob, userCloud)
        backend.jobVerified.call(jobWithToken.job, serviceCloud).orThrow()

        return Pair(JobStateChange(jobWithToken.job.id, JobState.VALIDATED), jobWithToken)
    }

    suspend fun handleProposedStateChange(
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
                    stateChangeProducer.emit(event)
                    handleStateChangeImmediately(jobWithToken, event, newStatus)
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

            accountingEventProducer.emit(
                JobCompletedEvent(
                    jobId,
                    job.owner,
                    wallDuration,
                    job.nodes,
                    System.currentTimeMillis(),
                    job.application.description.info,
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
        data: ByteReadChannel
    ) {
        withJobExceptionHandler(jobId) {
            val jobWithToken = findJobForId(jobId)
            computationBackendService.getAndVerifyByName(jobWithToken.job.backend, securityPrincipal)

            jobFileService.acceptFile(jobWithToken, filePath, length, data)
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
            job.application.description.info,
            job.currentState,
            job.status,
            job.currentState.isFinal(),
            job.id
        )
    }

    /**
     * Handles a state change immediately (as we emit it to Kafka)
     */
    private suspend fun handleStateChangeImmediately(
        jobWithToken: VerifiedJobWithAccessToken,
        event: JobStateChange,
        newStatus: String?
    ) {
        log.info("Received $event")
        db.withTransaction(autoFlush = true) {
            jobDao.updateStateAndStatus(it, event.systemId, event.newState, newStatus)
        }

        when (event.newState) {
            JobState.TRANSFER_SUCCESS -> {
                // We need to create the directory immediately
                jobFileService.initializeResultFolder(jobWithToken)
            }

            else -> {
                // Do nothing
            }
        }
    }

    /**
     * Handles a state change as we are told through Kafka
     */
    fun handleStateChange(event: JobStateChange) {
        runBlocking {
            withJobExceptionHandler(event.systemId, rethrow = false) {
                val jobWithToken = findJobForId(event.systemId)
                val (job, _) = jobWithToken
                val backend = computationBackendService.getAndVerifyByName(job.backend)

                when (event.newState) {
                    JobState.VALIDATED -> {
                        // Ask backend to prepare the job
                        transferFilesToCompute(jobWithToken)
                        db.withTransaction(autoFlush = true) {
                            jobDao.updateStateAndStatus(it, job.id, JobState.PREPARED)
                        }

                        val jobWithNewState = job.copy(currentState = JobState.PREPARED)
                        backend.jobPrepared.call(jobWithNewState, serviceCloud).orThrow()
                    }

                    JobState.PREPARED, JobState.SCHEDULED, JobState.RUNNING, JobState.TRANSFER_SUCCESS -> {
                        // Do nothing (apart from updating state). It is mostly working.
                    }

                    JobState.SUCCESS, JobState.FAILURE -> {
                        // This one should _NEVER_ throw an exception
                        val resp = backend.cleanup.call(job, serviceCloud)
                        if (resp is RESTResponse.Err) {
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
                safeAsync {
                    val userCloud = serviceCloud.parent.jwtAuth(accessToken)
                    val fileStream = FileDescriptions.download.call(
                        DownloadByURI(file.sourcePath, null),
                        userCloud
                    ).okChannelOrNull ?: throw JobException.TransferError()

                    backend.submitFile.call(
                        MultipartRequest.create(
                            SubmitFileToComputation(
                                job,
                                file.id,
                                StreamingFile(
                                    fileStream.contentType ?: ContentType.Application.OctetStream,
                                    fileStream.contentLength,
                                    file.destinationPath,
                                    fileStream.stream
                                )
                            )
                        ),
                        serviceCloud
                    ).orThrow()
                }
            }.awaitAllOrThrow()
        }
    }

    fun lookupOwnJob(jobId: String, securityPrincipal: SecurityPrincipal): VerifiedJob {
        val jobWithToken = findJobForId(jobId)
        computationBackendService.getAndVerifyByName(jobWithToken.job.backend, securityPrincipal)
        return jobWithToken.job
    }

    private fun findJobForId(id: String): VerifiedJobWithAccessToken =
        db.withTransaction { session -> jobDao.findOrNull(session, id) ?: throw JobException.NotFound("Job: $id") }

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
