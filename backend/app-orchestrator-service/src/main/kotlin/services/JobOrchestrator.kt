package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.*
import dk.sdu.cloud.app.orchestrator.UserClientFactory
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.TokenExtensionRequest
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.PaginationRequestV2
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.safeUsername
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.*

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
    private val db: AsyncDBSessionFactory,
    private val jobVerificationService: JobVerificationService,
    private val jobFileService: JobFileService,
    private val jobDao: JobDao,
    private val jobQueryService: JobQueryService,
    private val paymentService: PaymentService,
    private val providers: Providers,
    private val userClientFactory: UserClientFactory,
    private val parameterExportService: ParameterExportService,
) {
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
        principal: Actor,
        project: String?,
    ): JobsCreateResponse {
        data class JobWithProvider(val jobId: String, val provider: String)

        val tokensToInvalidateInCaseOfFailure = ArrayList<String>()
        val jobsToInvalidateInCaseOfFailure = ArrayList<JobWithProvider>()

        val parameters = ArrayList<ByteArray>()

        try {
            val (_, tmpUserClient) = extendToken(accessToken, allowRefreshes = false)
            val verifiedJobs = req.items
                // Immediately export parameters (before verification code changes it)
                .onEach { request ->
                    parameters.add(
                        defaultMapper.writeValueAsBytes(
                            parameterExportService.exportParameters(request)
                        )
                    )
                }
                // Verify tokens
                .map { jobRequest ->
                    jobVerificationService.verifyOrThrow(
                        UnverifiedJob(jobRequest, principal.username, project),
                        tmpUserClient
                    )
                }
                // Check for duplicates
                .onEach { job ->
                    if (!job.job.parameters.allowDuplicateJob) {
                        if (checkForDuplicateJob(principal, project, job)) {
                            throw JobException.Duplicate()
                        }
                    }
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
            for ((index, job) in verifiedJobs.withIndex()) {
                val jobFolder = jobFileService.initializeResultFolder(job)
                jobFileService.exportParameterFile(jobFolder.path, job, parameters[index])
            }

            // Insert into databases
            db.withSession { session ->
                for (job in verifiedJobs) {
                    jobDao.create(session, job)
                }
            }

            // Notify compute providers
            verifiedJobs.groupBy { it.job.parameters.product.provider }.forEach { (provider, jobs) ->
                val (api, client) = providers.prepareCommunication(provider)

                api.create.call(bulkRequestOf(jobs.map { it.job }), client).orThrow()
                jobsToInvalidateInCaseOfFailure.addAll(jobs.map { JobWithProvider(it.job.id, provider) })
            }

            return JobsCreateResponse(verifiedJobs.map { it.job.id })
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
                    bulkRequestOf(
                        jobQueryService
                            .retrievePrivileged(
                                db,
                                jobs.map { it.jobId },
                                JobDataIncludeFlags(includeParameters = true)
                            )
                            .map { it.value.job }
                    ),
                    client
                )
            }

            throw ex
        }
    }

    private suspend fun checkForDuplicateJob(
        securityPrincipal: Actor,
        project: String?,
        jobWithToken: VerifiedJobWithAccessToken,
    ): Boolean {
        return findLast10JobsForUser(securityPrincipal, project).any { it.job == jobWithToken.job }
    }

    private suspend fun findLast10JobsForUser(
        principal: Actor,
        project: String?,
    ): List<VerifiedJobWithAccessToken> {
        return jobQueryService.browse(
            principal,
            project,
            PaginationRequestV2(10).normalize(),
            JobDataIncludeFlags(includeParameters = true)
        ).items
    }

    suspend fun updateState(request: JobsControlUpdateRequest, providerActor: Actor) {
        val requests = request.items.groupBy { it.jobId }
        val comm = providers.prepareCommunication(providerActor)
        db.withSession { session ->
            val loadedJobs = loadAndVerifyProviderJobs(session, requests.keys, comm)

            for ((jobId, updates) in requests) {
                val jobWithToken = loadedJobs[jobId] ?: error("Job not found, this should have failed earlier")
                val (job) = jobWithToken
                var currentState = job.currentState
                for (update in updates) {
                    val newState = update.state
                    val newStatus = update.status

                    if (newState != null && !currentState.isFinal()) {
                        currentState = newState

                        if (newState.isFinal()) {
                            handleFinalState(jobWithToken)
                        }
                    } else if (newState != null && currentState.isFinal()) {
                        log.info("Ignoring job update for $jobId by ${comm.provider} (bad state transition)")
                        continue
                    }

                    jobDao.insertUpdate(session, jobId, System.currentTimeMillis(), newState, newStatus)
                }
            }
        }
    }

    private suspend fun handleFinalState(jobWithToken: VerifiedJobWithAccessToken) {
        jobFileService.cleanupAfterMounts(jobWithToken)

        AuthDescriptions.logout.call(
            Unit,
            serviceClient.withoutAuthentication().bearerAuth(jobWithToken.refreshToken)
        ).throwIfInternal()

        MetadataDescriptions.verify.call(
            VerifyRequest(jobWithToken.job.files.map { it.path }),
            serviceClient
        ).orThrow()
    }

    suspend fun deleteJobInformation(appName: String, appVersion: String) {
        jobDao.deleteJobInformation(
            db,
            appName,
            appVersion
        )
    }

    suspend fun charge(
        request: JobsControlChargeCreditsRequest,
        providerActor: Actor,
    ): JobsControlChargeCreditsResponse {
        val comm = providers.prepareCommunication(providerActor)
        val requests = request.items.groupBy { it.id }

        val duplicates = ArrayList<FindByStringId>()
        val insufficient = ArrayList<FindByStringId>()

        db.withSession { session ->
            val loadedJobs = loadAndVerifyProviderJobs(session, requests.keys, comm)

            for ((jobId, charges) in requests) {
                val jobWithToken = loadedJobs[jobId] ?: error("Job should have been loaded by now")
                for (charge in charges) {
                    val chargeResult = paymentService.charge(
                        jobWithToken.job,
                        charge.wallDuration.toMillis(),
                        charge.chargeId
                    )

                    when (chargeResult) {
                        is PaymentService.ChargeResult.Charged -> {
                            jobDao.updateCreditsCharged(session, jobId, chargeResult.amountCharged)
                        }

                        PaymentService.ChargeResult.InsufficientFunds -> {
                            insufficient.add(FindByStringId(jobId))
                        }

                        PaymentService.ChargeResult.Duplicate -> {
                            duplicates.add(FindByStringId(jobId))
                        }
                    }
                }
            }
        }

        return JobsControlChargeCreditsResponse(insufficient, duplicates)
    }

    private suspend fun loadAndVerifyProviderJobs(
        session: DBContext,
        jobIds: Set<String>,
        comm: ProviderCommunication,
        flags: JobDataIncludeFlags = JobDataIncludeFlags(includeParameters = true),
    ): Map<String, VerifiedJobWithAccessToken> {
        val loadedJobs = jobQueryService.retrievePrivileged(session, jobIds, flags)
        if (loadedJobs.keys.size != jobIds.size) {
            throw RPCException("Not all jobs are known to UCloud", HttpStatusCode.NotFound)
        }

        val ownsAllJobs = loadedJobs.values.all { (it.job).parameters.product.provider == comm.provider }
        if (!ownsAllJobs) {
            throw RPCException("Provider is not authorized to perform these updates", HttpStatusCode.Forbidden)
        }
        return loadedJobs
    }

    private suspend fun loadAndVerifyUserJobs(
        session: AsyncDBConnection,
        jobIds: Set<String>,
        user: Actor,
        flags: JobDataIncludeFlags = JobDataIncludeFlags(includeParameters = true),
    ): Map<String, VerifiedJobWithAccessToken> {
        val loadedJobs = jobQueryService.retrievePrivileged(session, jobIds, flags)
        if (loadedJobs.keys.size != jobIds.size) {
            throw RPCException("Not all jobs are known to UCloud", HttpStatusCode.NotFound)
        }

        val ownsAllJobs = loadedJobs.values.all {
            it.job.owner.launchedBy == user.safeUsername() || user == Actor.System
        }
        if (!ownsAllJobs) {
            throw RPCException("Not all jobs are known to UCloud", HttpStatusCode.NotFound)
        }
        return loadedJobs
    }

    suspend fun submitFile(request: JobsControlSubmitFileRequest, providerActor: Actor) {
        val comm = providers.prepareCommunication(providerActor)
        val jobWithToken = loadAndVerifyProviderJobs(db, setOf(request.jobId), comm).getValue(request.jobId)

        val ingoing = request.fileData.asIngoing()
        jobFileService.acceptFile(
            jobWithToken,
            request.filePath,
            ingoing.length ?: throw RPCException("Unknown length of payload", HttpStatusCode.BadRequest),
            ingoing.channel
        )
    }

    suspend fun retrieveAsProvider(
        request: JobsControlRetrieveRequest,
        providerActor: Actor,
    ): Job {
        val comm = providers.prepareCommunication(providerActor)
        return loadAndVerifyProviderJobs(db, setOf(request.id), comm, request).getValue(request.id).job
    }

    suspend fun extendDuration(request: BulkRequest<JobsExtendRequestItem>, userActor: Actor.User) {
        val extensions = request.items.groupBy { it.jobId }
        db.withSession { session ->
            val jobs = loadAndVerifyUserJobs(session, extensions.keys, userActor)
            val providers = jobs.values.map { it.job.parameters!!.product.provider }
                .map { providers.prepareCommunication(it) }

            for (comm in providers) {
                comm.api.extend.call(
                    bulkRequestOf(request.items.mapNotNull { extensionRequest ->
                        val (job) = jobs.getValue(extensionRequest.jobId)
                        if (job.parameters!!.product.provider != comm.provider) null
                        else ComputeExtendRequestItem(job, extensionRequest.requestedTime)
                    }),
                    comm.client
                ).orThrow()
            }
        }
    }

    suspend fun cancel(request: BulkRequest<FindByStringId>, userActor: Actor) {
        val extensions = request.items.groupBy { it.id }

        db.withSession { session ->
            val jobs = loadAndVerifyUserJobs(session, extensions.keys, userActor)
            val jobsByProvider = jobs.values.groupBy { it.job.parameters!!.product.provider }
            for ((provider, providerJobs) in jobsByProvider) {
                val comm = providers.prepareCommunication(provider)
                comm.api.delete.call(
                    bulkRequestOf(providerJobs.map { it.job }),
                    comm.client
                ).orThrow()
            }
        }
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

    suspend fun follow(
        callHandler: CallHandler<JobsFollowRequest, JobsFollowResponse, *>,
        actor: Actor,
    ) {
        with(callHandler) {
            withContext<WSCall> {
                val (initialJob) = jobQueryService.retrieve(
                    actor,
                    ctx.project,
                    request.id,
                    JobDataIncludeFlags(includeUpdates = true)
                )

                sendWSMessage(JobsFollowResponse(initialJob.updates, emptyList()))
                var lastUpdate = initialJob.updates.maxByOrNull { it.timestamp }?.timestamp ?: 0L
                val (api, _, wsClient) = providers.prepareCommunication(initialJob.parameters.product.provider)
                var streamId: String? = null

                coroutineScope {
                    val logJob = launch {
                        log.info("Logs are go!")
                        api.follow.subscribe(ComputeFollowRequest.Init(initialJob), wsClient, { message ->
                            if (streamId == null) {
                                streamId = message.streamId
                            }

                            // Providers are allowed to use negative ranks for control messages
                            if (message.rank >= 0) {
                                sendWSMessage(JobsFollowResponse(
                                    emptyList(),
                                    listOf(JobsLog(message.rank, message.stdout, message.stderr))
                                ))
                            }
                        }).orThrow()
                    }

                    val updateJob = launch {
                        log.info("Updates are go!")
                        while (currentCoroutineContext().isActive) {
                            val (job) = jobQueryService.retrieve(
                                actor,
                                ctx.project,
                                request.id,
                                JobDataIncludeFlags(includeUpdates = true)
                            )

                            // TODO not ideal for chatty providers
                            val updates = job.updates.filter { it.timestamp > lastUpdate }
                            if (updates.isNotEmpty()) {
                                sendWSMessage(JobsFollowResponse(updates, emptyList()))
                                lastUpdate = job.updates.maxByOrNull { it.timestamp }?.timestamp ?: 0L
                            }
                            delay(1000)
                        }
                    }

                    try {
                        logJob.join()
                        updateJob.join()
                    } finally {
                        val capturedId = streamId
                        if (capturedId != null) {
                            api.follow.call(
                                ComputeFollowRequest.CancelStream(capturedId),
                                wsClient
                            )
                        }
                    }
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
