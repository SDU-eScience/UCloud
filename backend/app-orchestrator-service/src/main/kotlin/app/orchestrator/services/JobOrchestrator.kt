package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.api.SortOrder
import dk.sdu.cloud.app.orchestrator.rpc.JOB_MAX_TIME
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.bearerAuth
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.throwIfInternal
import dk.sdu.cloud.calls.client.withoutAuthentication
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.launch

/**
 * The job orchestrator is responsible for the orchestration of computation backends.
 *
 * The orchestrator receives life-time events via its methods. These life-time events can be
 * related to user action (e.g. starting an application) or it could be related to computation
 * updates (e.g. state change). In reaction to life-time events the orchestrator will update
 * its internal state and potentially send new updates to the relevant computation backends.
 *
 * Below is a description of how a [VerifiedJob] moves between its states ([VerifiedJob.currentState]):
 *
 * - A user starts an application (see [startJob])
 *   - Sets state to [JobState.VALIDATED]. Backends notified via [ComputationDescriptions.jobVerified]
 *
 * - A job becomes [JobState.VALIDATED]
 *   - Files are transferred to the computation backend.
 *   - Sets state to [JobState.PREPARED]. Backends notified via [ComputationDescriptions.jobPrepared]
 *
 * - Computation backend successfully schedules job and requests state change ([handleProposedStateChange]) to
 *   [JobState.SCHEDULED]
 *
 * - Computation backend notifies us of job completion ([JobState.TRANSFER_SUCCESS]). This can happen both in case
 *   of failures and successes.
 *   - This will initialize an output directory in the user's home folder.
 *   - In this state we will accept output files via [ComputationCallbackDescriptions.submitFile]
 *
 * - Computation backend notifies us of final result ([JobState.FAILURE], [JobState.SUCCESS])
 *   - Backends are asked to clean up temporary files via [ComputationDescriptions.cleanup]
 *
 */
class JobOrchestrator(
    private val serviceClient: AuthenticatedClient,
    private val db: DBContext,
    private val jobVerificationService: JobVerificationService,
    private val computationBackendService: ComputationBackendService,
    private val jobFileService: JobFileService,
    private val jobDao: JobDao,
    private val jobQueryService: JobQueryService,
    private val defaultBackend: String,
    private val scope: BackgroundScope,
    private val paymentService: PaymentService
) {
    /**
     * Shared error handling for methods that work with a live job.
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
                val existingJob = db.withSession { session ->
                    jobDao.updateStatus(session, jobId, message ?: "Internal error")
                    jobQueryService.find(session, listOf(jobId), null).singleOrNull()
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
        decodedToken: SecurityPrincipalToken,
        refreshToken: String,
        userCloud: AuthenticatedClient,
        project: String?
    ): String {
        log.debug("Starting job ${req.application.name}@${req.application.version}")
        val backend = computationBackendService.getAndVerifyByName(resolveBackend(req.backend, defaultBackend))

        log.debug("Verifying job")
        val unverifiedJob = UnverifiedJob(req, decodedToken, refreshToken, project)
        val jobWithToken = jobVerificationService.verifyOrThrow(unverifiedJob, userCloud)

        log.debug("Checking if duplicate")
        if (!req.acceptSameDataRetry) {
            if (checkForDuplicateJob(decodedToken, jobWithToken)) {
                throw RPCException.fromStatusCode(HttpStatusCode.Conflict, "Job with same parameters already running")
            }
        }

        log.debug("Switching state and preparing job...")
        val (outputFolder) = jobFileService.initializeResultFolder(jobWithToken)
        jobFileService.exportParameterFile(outputFolder, jobWithToken, req.parameters)
        val jobWithOutputFolder = jobWithToken.job.copy(outputFolder = outputFolder)
        val jobWithTokenAndFolder = jobWithToken.copy(job = jobWithOutputFolder)
        paymentService.reserve(jobWithOutputFolder)

        log.debug("Notifying compute")
        val initialState = JobStateChange(jobWithToken.job.id, JobState.PREPARED)
        backend.jobVerified.call(jobWithOutputFolder, serviceClient).orThrow()

        log.debug("Saving job state")
        jobDao.create(
            db,
            jobWithTokenAndFolder
        )
        handleStateChange(jobWithTokenAndFolder, initialState)

        return initialState.systemId
    }

    private suspend fun checkForDuplicateJob(
        securityPrincipalToken: SecurityPrincipalToken,
        jobWithToken: VerifiedJobWithAccessToken
    ): Boolean {
        val jobs = findLast10JobsForUser(
            securityPrincipalToken,
            jobWithToken.job.application.metadata.name,
            jobWithToken.job.application.metadata.version
        )

        return jobs.any { it.job == jobWithToken.job }
    }

    private suspend fun findLast10JobsForUser(
        securityPrincipalToken: SecurityPrincipalToken,
        application: String,
        version: String
    ): List<VerifiedJobWithAccessToken> {
        return jobQueryService.list(
            db,
            securityPrincipalToken.principal.username,
            PaginationRequest(10).normalize(),
            ListRecentRequest(
                sortBy = JobSortBy.CREATED_AT,
                order = SortOrder.DESCENDING,
                filter = JobState.RUNNING,
                application = application,
                version = version
            )
        ).items
    }

    suspend fun handleProposedStateChange(
        event: JobStateChange,
        newStatus: String?,
        computeBackend: SecurityPrincipal? = null,
        jobOwner: SecurityPrincipalToken? = null
    ) {
        if (computeBackend == null && jobOwner == null) {
            log.warn("computeBackend == null && jobOwner == null in handleProposedStateChange")
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
        }

        val jobWithToken = findJobForId(event.systemId, jobOwner)

        withJobExceptionHandler(event.systemId) {
            val proposedState = event.newState
            val (job) = jobWithToken
            computationBackendService.getAndVerifyByName(job.backend, computeBackend)

            val validStates = validStateTransitions[job.currentState] ?: emptySet()
            if (proposedState in validStates) {
                if (proposedState != job.currentState) {
                    val asyncJob = handleStateChange(jobWithToken, event, newStatus)

                    // Some states we want to wait for
                    if (proposedState == JobState.TRANSFER_SUCCESS) {
                        asyncJob.join()
                    }
                }
            } else {
                // if we have bad transition on canceling, the job is finished and should not change status
                if (proposedState != JobState.CANCELING || job.currentState.isFinal()) {
                    if (job.currentState.isFinal()) {
                        log.info("Bad state transition from ${job.currentState} to $proposedState")
                        return
                    }

                    throw JobException.BadStateTransition(job.currentState, event.newState)
                }
            }
        }
    }

    suspend fun handleAddStatus(jobId: String, newStatus: String, securityPrincipal: SecurityPrincipal) {
        // We don't cancel the job if this fails
        val (job) = findJobForId(jobId)
        computationBackendService.getAndVerifyByName(job.backend, securityPrincipal)

        jobDao.updateStatus(db, jobId, newStatus)
    }

    suspend fun handleJobComplete(
        jobId: String,
        wallDuration: SimpleDuration?,
        success: Boolean,
        securityPrincipal: SecurityPrincipal
    ) {
        withJobExceptionHandler(jobId) {
            val jobWithToken = findJobForId(jobId)
            val job = jobWithToken.job
            computationBackendService.getAndVerifyByName(job.backend, securityPrincipal)

            val actualDuration = if (wallDuration != null) {
                wallDuration
            } else {
                val startedAt = job.startedAt
                if (startedAt == null) {
                    SimpleDuration.fromMillis(5000L)
                } else {
                    SimpleDuration.fromMillis(Time.now() - startedAt)
                }
            }

            log.debug("Job completed $jobId took $actualDuration")

            jobFileService.cleanupAfterMounts(jobWithToken)

            handleProposedStateChange(
                JobStateChange(jobId, if (success) JobState.SUCCESS else JobState.FAILURE),
                null,
                securityPrincipal
            )

            paymentService.charge(job, actualDuration.toMillis())
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

    /**
     * Handles a state change
     */
    private fun handleStateChange(
        jobWithToken: VerifiedJobWithAccessToken,
        event: JobStateChange,
        newStatus: String? = null,
        isReplay: Boolean = false
    ): Job = scope.launch {
        withJobExceptionHandler(event.systemId, rethrow = false) {
            if (!isReplay) {
                val failedStateOrNull =
                    if (event.newState == JobState.FAILURE) jobWithToken.job.currentState else null

                jobDao.updateStatus(
                    db,
                    event.systemId,
                    state = event.newState,
                    status = newStatus,
                    failedState = failedStateOrNull
                )
            }

            val (job) = jobWithToken
            val backend = computationBackendService.getAndVerifyByName(job.backend)

            when (event.newState) {
                JobState.VALIDATED -> {
                    jobDao.updateStatus(
                        db,
                        event.systemId,
                        state = JobState.PREPARED,
                        status = "Your job is currently in the process of being scheduled."
                    )
                }

                JobState.PREPARED -> {
                    backend.jobPrepared.call(jobWithToken.job, serviceClient).orThrow()
                }

                JobState.SCHEDULED, JobState.RUNNING -> {
                    // Do nothing (apart from updating state).
                }

                JobState.CANCELING -> {
                    backend.cancel.call(CancelInternalRequest(job), serviceClient).orThrow()
                }

                JobState.SUCCESS, JobState.FAILURE -> {
                    if (job.currentState == JobState.CANCELING) {
                        jobDao.updateStatus(
                            db,
                            event.systemId,
                            state = event.newState,
                            status = "Job cancelled successfully."
                        )
                    }

                    if (jobWithToken.refreshToken != null) {
                        AuthDescriptions.logout.call(
                            Unit,
                            serviceClient.withoutAuthentication().bearerAuth(jobWithToken.refreshToken)
                        ).throwIfInternal()
                    }

                    MetadataDescriptions.verify.call(
                        VerifyRequest(
                            job.files.map { it.sourcePath } + job.mounts.map { it.sourcePath }
                        ),
                        serviceClient
                    ).orThrow()

                    // This one should _NEVER_ throw an exception
                    val resp = backend.cleanup.call(job, serviceClient)
                    if (resp is IngoingCallResponse.Error) {
                        log.info("unable to clean up for job $job.")
                        log.info(resp.toString())
                    }
                }
            }
            return@withJobExceptionHandler
        }
    }

    suspend fun replayLostJobs() {
        log.info("Replaying jobs lost from last session...")
        var count = 0
        jobQueryService.findJobsCreatedBefore(db, Time.now()).collect { jobWithToken ->
            count++
            handleStateChange(
                jobWithToken,
                JobStateChange(jobWithToken.job.id, jobWithToken.job.currentState),
                isReplay = true
            )
        }
        log.info("No more lost jobs! We recovered $count jobs.")
    }

    suspend fun lookupOwnJob(jobId: String, securityPrincipal: SecurityPrincipal): VerifiedJob {
        val jobWithToken = findJobForId(jobId)
        computationBackendService.getAndVerifyByName(jobWithToken.job.backend, securityPrincipal)
        return jobWithToken.job
    }

    suspend fun lookupOwnJobByUrl(urlId: String, securityPrincipal: SecurityPrincipal): VerifiedJob {
        val jobWithToken = findJobForUrl(urlId)
        computationBackendService.getAndVerifyByName(jobWithToken.job.backend, securityPrincipal)
        return jobWithToken.job
    }

    suspend fun removeExpiredJobs() {
        val expired = Time.now() - JOB_MAX_TIME
        jobQueryService.findJobsCreatedBefore(db, expired).collect { job ->
            failJob(job)
        }
    }

    private suspend fun findJobForId(id: String, jobOwner: SecurityPrincipalToken? = null): VerifiedJobWithAccessToken {
        return if (jobOwner == null) {
            jobQueryService.find(db, listOf(id), null).singleOrNull()
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        } else {
            jobQueryService.findById(jobOwner, id)
        }
    }

    private suspend fun findJobForUrl(
        urlId: String,
        jobOwner: SecurityPrincipalToken? = null
    ): VerifiedJobWithAccessToken {
        return jobQueryService.findFromUrlId(db, urlId, jobOwner?.principal?.username)
            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
    }

    companion object : Loggable {
        override val log = logger()

        private val finalStates = JobState.values().asSequence().filter { it.isFinal() }.toSet()
        private val validStateTransitions: Map<JobState, Set<JobState>> = mapOf(
            JobState.VALIDATED to (setOf(JobState.PREPARED, JobState.CANCELING) + finalStates),

            JobState.PREPARED to (setOf(
                JobState.SCHEDULED,
                JobState.RUNNING,
                JobState.TRANSFER_SUCCESS,
                JobState.CANCELING
            ) + finalStates),

            // We allow scheduled to skip running in case of quick jobs
            JobState.SCHEDULED to (setOf(
                JobState.RUNNING,
                JobState.TRANSFER_SUCCESS,
                JobState.CANCELING
            ) + finalStates),

            JobState.RUNNING to (setOf(
                JobState.TRANSFER_SUCCESS,
                JobState.SUCCESS,
                JobState.FAILURE,
                JobState.CANCELING
            )),

            JobState.TRANSFER_SUCCESS to (setOf(JobState.SUCCESS, JobState.FAILURE, JobState.CANCELING)),

            JobState.CANCELING to (setOf(JobState.SUCCESS, JobState.FAILURE)),

            // In case of really bad failures we allow for a "failure -> failure" transition
            JobState.FAILURE to setOf(JobState.FAILURE),
            JobState.SUCCESS to emptySet()
        )
    }

    suspend fun deleteJobInformation(appName: String, appVersion: String) {
        jobDao.deleteJobInformation(
            db,
            appName,
            appVersion
        )
    }
}

fun resolveBackend(backend: String?, defaultBackend: String): String = backend ?: defaultBackend
