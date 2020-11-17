package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.app.orchestrator.UserClientFactory
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.TokenExtensionRequest
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.bearerAuth
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withoutAuthentication
import dk.sdu.cloud.calls.server.requiredAuthScope
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class JobStateChange(val systemId: String, val newState: JobState)

/**
 * The job orchestrator is responsible for the orchestration of computation providers.
 *
 * The orchestrator receives life-time events via its methods. These life-time events can be
 * related to user action (e.g. starting an application) or it could be related to computation
 * updates (e.g. state change). In reaction to life-time events the orchestrator will update
 * its internal state and potentially send new updates to the relevant computation providers.
 */
class JobOrchestrator(
    private val serviceClient: AuthenticatedClient,
    private val db: DBContext,
    private val jobVerificationService: JobVerificationService,
    private val jobFileService: JobFileService,
    private val jobDao: JobDao,
    private val jobQueryService: JobQueryService,
    private val scope: BackgroundScope,
    private val paymentService: PaymentService,
    private val providers: Providers,
    private val userClientFactory: UserClientFactory,
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

            log.info("Exception: $ex")

            if (ex !is RPCException) {
                log.warn("Unexpected exception caught while handling a job callback! ($jobId)")
                log.warn(ex.stackTraceToString())
            } else {
                log.debug("Expected exception caught while handling a job callback! ($jobId)")
                log.debug(ex.stackTraceToString())
            }

            try {
                val existingJob = db.withSession { session ->
                    jobDao.insertUpdate(session, jobId, System.currentTimeMillis(), null, message ?: "Internal error")
                    //jobQueryService.find(session, listOf(jobId), null).singleOrNull()
                    TODO()
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

    private suspend fun extendToken(accessToken: String, allowRefreshes: Boolean): Pair<String?, AuthenticatedClient> {
        return AuthDescriptions.tokenExtension.call(
            TokenExtensionRequest(
                accessToken,
                listOf(
                    MultiPartUploadDescriptions.simpleUpload.requiredAuthScope.toString(),
                    FileDescriptions.download.requiredAuthScope.toString(),
                    FileDescriptions.createDirectory.requiredAuthScope.toString(),
                    FileDescriptions.stat.requiredAuthScope.toString(),
                    FileDescriptions.deleteFile.requiredAuthScope.toString()
                ),
                1000L * 60 * 60 * 5,
                allowRefreshes = allowRefreshes
            ),
            serviceClient
        ).orThrow().let {
            if (!allowRefreshes) {
                null to serviceClient.withoutAuthentication().bearerAuth(it.accessToken)
            } else {
                it.refreshToken!! to userClientFactory(it.refreshToken!!)
            }
        }
    }

    suspend fun startJob(
        req: BulkRequest<JobParameters>,
        accessToken: String,
        principal: SecurityPrincipal,
        project: String?,
    ): JobsCreateResponse {
        data class JobWithProvider(val jobId: String, val provider: String)

        return db.withSession { session ->
            val tokensToInvalidateInCaseOfFailure = ArrayList<String>()
            val jobsToInvalidateInCaseOfFailure = ArrayList<JobWithProvider>()

            try {
                val (_, tmpUserClient) = extendToken(accessToken, allowRefreshes = false)
                val verifiedJobs = req.items
                    // Verify tokens
                    .map { jobRequest ->
                        jobVerificationService.verifyOrThrow(
                            UnverifiedJob(jobRequest, principal.username, project),
                            tmpUserClient
                        )
                    }
                    // Check for duplicates
                    .onEach { job ->
                        TODO("Check them")
                    }
                    // Extend tokens needed for jobs
                    .map { job ->
                        val (extendedToken) = extendToken(accessToken, allowRefreshes = true)
                        tokensToInvalidateInCaseOfFailure.add(extendedToken!!)
                        job.copy(refreshToken = extendedToken)
                    }

                // Reserve resources (and check that resources are available)
                for (job in verifiedJobs) {
                    paymentService.reserve(job.job)
                }

                // Prepare job folders (needs to happen before database insertion)
                for (job in verifiedJobs) {
                    val jobFolder = jobFileService.initializeResultFolder(job)
                    jobFileService.exportParameterFile(jobFolder.path, job)
                }

                // Insert into databases
                for (job in verifiedJobs) {
                    jobDao.create(session, job)
                }

                // Notify compute providers
                verifiedJobs.groupBy { it.job.parameters!!.product.provider }.forEach { (provider, jobs) ->
                    val (api, client) = providers.prepareCommunication(provider)

                    api.create.call(bulkRequestOf(jobs.map { it.job }), client).orThrow()
                    jobsToInvalidateInCaseOfFailure.addAll(jobs.map { JobWithProvider(it.job.id, provider) })
                }

                JobsCreateResponse(verifiedJobs.map { it.job.id })
            } catch (ex: Throwable) {
                for (tok in tokensToInvalidateInCaseOfFailure) {
                    AuthDescriptions.logout.call(
                        Unit,
                        serviceClient.withoutAuthentication().bearerAuth(tok)
                    )
                }

                jobsToInvalidateInCaseOfFailure.groupBy { it.provider }.forEach { (provider, jobs) ->
                    val (api, client) = providers.prepareCommunication(provider)
                    api.delete.call(
                        bulkRequestOf(jobs.map { FindByStringId(it.jobId) }),
                        client
                    )
                }

                throw ex
            }
        }
    }

    private suspend fun checkForDuplicateJob(
        securityPrincipalToken: SecurityPrincipalToken,
        jobWithToken: VerifiedJobWithAccessToken,
    ): Boolean {
        TODO("""
        val jobs = findLast10JobsForUser(
            securityPrincipalToken,
            jobWithToken.job.application.metadata.name,
            jobWithToken.job.application.metadata.version
        )

        return jobs.any { it.job == jobWithToken.job }
        """)
    }

    private suspend fun findLast10JobsForUser(
        securityPrincipalToken: SecurityPrincipalToken,
        application: String,
        version: String,
    ): List<VerifiedJobWithAccessToken> {
        TODO("""
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
        """)
    }

    suspend fun handleProposedStateChange(
        event: JobStateChange,
        newStatus: String?,
        computeBackend: SecurityPrincipal? = null,
        jobOwner: SecurityPrincipalToken? = null,
    ) {
        TODO("""
        val jobWithToken = findJobForId(event.systemId, jobOwner)

        withJobExceptionHandler(event.systemId) {
            val proposedState = event.newState
            val (job) = jobWithToken
            computationBackendService.getAndVerifyByName(job.backend, computeBackend)

            if (job.currentState.isFinal()) {
                if (proposedState != JobState.CANCELING) {
                    log.info("Ignoring bad state transition from $\{job.currentState} to $\proposedState")
                }
                return
            }

            if (proposedState != job.currentState) {
                handleStateChange(jobWithToken, event, newStatus)
            }
        }
        """)
    }

    suspend fun handleJobComplete(
        jobId: String,
        wallDuration: SimpleDuration?,
        success: Boolean,
        securityPrincipal: SecurityPrincipal,
    ) {
        TODO("""
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

            log.debug("Job completed $jobId took $\actualDuration")

            jobFileService.cleanupAfterMounts(jobWithToken)

            handleProposedStateChange(
                JobStateChange(jobId, if (success) JobState.SUCCESS else JobState.FAILURE),
                null,
                securityPrincipal
            )

            chargeCredits(jobWithToken, actualDuration, "", false)
        }
        """)
    }

    suspend fun handleIncomingFile(
        jobId: String,
        securityPrincipal: SecurityPrincipal,
        filePath: String,
        length: Long,
        data: ByteReadChannel,
    ) {
        TODO("""
        withJobExceptionHandler(jobId) {
            val jobWithToken = findJobForId(jobId)
            computationBackendService.getAndVerifyByName(jobWithToken.job.backend, securityPrincipal)

            jobFileService.acceptFile(jobWithToken, filePath, length, data)
        }
        """)
    }

    /**
     * Handles a state change
     */
    private fun handleStateChange(
        jobWithToken: VerifiedJobWithAccessToken,
        event: JobStateChange,
        newStatus: String? = null,
        isReplay: Boolean = false,
    ): Job = scope.launch {
        TODO("""
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
                JobState.IN_QUEUE -> {
                    jobDao.updateStatus(
                        db,
                        event.systemId,
                        state = JobState.IN_QUEUE,
                        status = "Your job is currently in the process of being scheduled."
                    )

                    backend.jobPrepared.call(jobWithToken.job, serviceClient).orThrow()
                }

                JobState.RUNNING -> {
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
                            status = "Job cancelled ($\{jobWithToken.job.status})."
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
                        log.info("unable to clean up for job $\job.")
                        log.info(resp.toString())
                    }
                }
            }
            return@withJobExceptionHandler
        }
        """)
    }


    suspend fun extendDuration(
        jobId: String,
        extendWith: SimpleDuration,
        jobOwner: SecurityPrincipalToken,
    ) {
        TODO("""
        val jobWithToken = findJobForId(jobId, jobOwner)
        val backend = computationBackendService.getAndVerifyByName(jobWithToken.job.backend)

        db.withSession { session ->
            val newMaxTime = SimpleDuration.fromMillis(jobWithToken.job.maxTime.toMillis() + extendWith.toMillis())
            jobDao.updateMaxTime(
                session,
                jobId,
                newMaxTime
            )

            backend.updateJobDeadline.call(
                UpdateJobDeadlineRequest(jobWithToken.job.copy(maxTime = newMaxTime), newMaxTime),
                serviceClient
            ).orThrow()
        }
        """)
    }

    suspend fun lookupOwnJob(jobId: String, securityPrincipal: SecurityPrincipal): Job {
        TODO("""
        val jobWithToken = findJobForId(jobId)
        computationBackendService.getAndVerifyByName(jobWithToken.job.backend, securityPrincipal)
        return jobWithToken.job
        """)
    }

    suspend fun lookupOwnJobByUrl(urlId: String, securityPrincipal: SecurityPrincipal): Job {
        TODO("""
        val jobWithToken = findJobForUrl(urlId)
        computationBackendService.getAndVerifyByName(jobWithToken.job.backend, securityPrincipal)
        return jobWithToken.job
        """)
    }

    private suspend fun findJobForId(id: String, jobOwner: SecurityPrincipalToken? = null): VerifiedJobWithAccessToken {
        /*
        return if (jobOwner == null) {
            jobQueryService.find(db, listOf(id), null).singleOrNull()
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        } else {
            jobQueryService.findById(jobOwner, id)
        }
         */
        TODO()
    }

    private suspend fun findJobForUrl(
        urlId: String,
        jobOwner: SecurityPrincipalToken? = null,
    ): VerifiedJobWithAccessToken {
        /*
        return jobQueryService.findFromUrlId(db, urlId, jobOwner?.principal?.username)
            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
         */
        TODO()
    }

    suspend fun deleteJobInformation(appName: String, appVersion: String) {
        jobDao.deleteJobInformation(
            db,
            appName,
            appVersion
        )
    }

    suspend fun charge(
        jobId: String,
        chargeId: String,
        wallDuration: SimpleDuration,
        securityPrincipal: SecurityPrincipal,
    ) {
        TODO("""
        val jobWithToken = findJobForUrl(jobId)
        computationBackendService.getAndVerifyByName(jobWithToken.job.backend, securityPrincipal)

        chargeCredits(jobWithToken, wallDuration, "-$chargeId", cancelIfInsufficient = true)
        """)
    }

    private suspend fun chargeCredits(
        jobWithToken: VerifiedJobWithAccessToken,
        wallDuration: SimpleDuration,
        chargeId: String,
        cancelIfInsufficient: Boolean,
    ) {
        val charge = paymentService.charge(jobWithToken.job, wallDuration.toMillis(), chargeId)
        when (charge) {
            is PaymentService.ChargeResult.Charged -> {
                TODO() // jobDao.updateStatus(db, jobWithToken.job.id, creditsCharged = charge.amountCharged)
            }

            PaymentService.ChargeResult.InsufficientFunds -> {
                if (cancelIfInsufficient) {
                    handleStateChange(
                        jobWithToken,
                        JobStateChange(jobWithToken.job.id, JobState.CANCELING),
                        "Insufficient funds"
                    )
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

fun resolveBackend(backend: String?, defaultBackend: String): String = backend ?: defaultBackend
