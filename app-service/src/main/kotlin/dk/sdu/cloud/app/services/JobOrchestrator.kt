package dk.sdu.cloud.app.services

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.api.*
import dk.sdu.cloud.app.http.JOB_MAX_TIME
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.events.EventProducer
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Job
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
 *   - Accounting backends are notified (See [AccountingEvents])
 *   - Backends are asked to clean up temporary files via [ComputationDescriptions.cleanup]
 *
 * At startup the job orchestrator can restart otherwise unmanaged jobs. This should be performed at startup via
 * [replayLostJobs]. Only one instance of this microservice must run at the same time!
 */
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
        userCloud: AuthenticatedClient
    ): String {
        val backend = computationBackendService.getAndVerifyByName(resolveBackend(req.backend))
        val unverifiedJob = UnverifiedJob(req, principal)
        val jobWithToken = jobVerificationService.verifyOrThrow(unverifiedJob, userCloud)
        val initialState = JobStateChange(jobWithToken.job.id, JobState.VALIDATED)
        backend.jobVerified.call(jobWithToken.job, serviceCloud).orThrow()

        db.withTransaction { session -> jobDao.create(session, jobWithToken) }
        handleStateChange(jobWithToken, initialState)
        return initialState.systemId
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

            val validStates = validStateTransitions[job.currentState] ?: emptySet()
            if (proposedState in validStates) {
                if (proposedState != job.currentState) {
                    handleStateChange(jobWithToken, event, newStatus)
                }
            } else {
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

    /**
     * Handles a state change
     */
    private fun handleStateChange(
        jobWithToken: VerifiedJobWithAccessToken,
        event: JobStateChange,
        newStatus: String? = null,
        isReplay: Boolean = false
    ): Job = OrchestrationScope.launch {
        withJobExceptionHandler(event.systemId, rethrow = false) {
            if (!isReplay) {
                db.withTransaction(autoFlush = true) {
                    jobDao.updateStateAndStatus(it, event.systemId, event.newState, newStatus)
                }
            }

            val (job, _) = jobWithToken
            val backend = computationBackendService.getAndVerifyByName(job.backend)
            val backendConfig = backend.config

            log.info("New state ${job.id} is ${event.newState}")
            when (event.newState) {
                JobState.VALIDATED -> {
                    if (!backendConfig.useWorkspaces) {
                        jobFileService.transferFilesToBackend(jobWithToken, backend)
                    } else {
                        // TODO Prepare workspace with file-service
                    }

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
                    // Do nothing (apart from updating state).
                }

                JobState.TRANSFER_SUCCESS -> {
                    jobFileService.initializeResultFolder(jobWithToken, isReplay)
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
            return@withJobExceptionHandler
        }
    }

    fun replayLostJobs() {
        log.info("Replaying jobs lost from last session...")
        var count = 0
        runBlocking {
            db.withTransaction {
                jobDao.findJobsCreatedBefore(it, System.currentTimeMillis()).forEach { jobWithToken ->
                    count++
                    handleStateChange(
                        jobWithToken,
                        JobStateChange(jobWithToken.job.id, jobWithToken.job.currentState),
                        isReplay = true
                    )
                }
            }
        }
        log.info("No more lost jobs! We recovered $count jobs.")
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
        db.withTransaction { session -> jobDao.find(session, id) }

    companion object : Loggable {
        override val log = logger()

        private val finalStates = JobState.values().asSequence().filter { it.isFinal() }.toSet()
        private val validStateTransitions: Map<JobState, Set<JobState>> = mapOf(
            JobState.VALIDATED to (setOf(JobState.PREPARED) + finalStates),
            JobState.PREPARED to (setOf(JobState.SCHEDULED, JobState.RUNNING, JobState.TRANSFER_SUCCESS) + finalStates),
            // We allow scheduled to skip running in case of quick jobs
            JobState.SCHEDULED to (setOf(JobState.RUNNING, JobState.TRANSFER_SUCCESS) + finalStates),
            JobState.RUNNING to (setOf(JobState.TRANSFER_SUCCESS, JobState.SUCCESS, JobState.FAILURE)),
            JobState.TRANSFER_SUCCESS to (setOf(JobState.SUCCESS, JobState.FAILURE)),
            // In case of really bad failures we allow for a "failure -> failure" transition
            JobState.FAILURE to setOf(JobState.FAILURE),
            JobState.SUCCESS to emptySet()
        )
    }
}

fun <DBSession> JobDao<DBSession>.find(session: DBSession, id: String): VerifiedJobWithAccessToken {
    return findOrNull(session, id) ?: throw JobException.NotFound("Job: $id")
}

fun resolveBackend(backend: String?): String = backend ?: "abacus"
