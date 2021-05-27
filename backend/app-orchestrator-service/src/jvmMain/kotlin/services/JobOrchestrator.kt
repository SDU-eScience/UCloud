package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.*
import dk.sdu.cloud.app.orchestrator.UserClientFactory
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.app.store.api.ToolBackend
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.provider.api.FEATURE_NOT_SUPPORTED_BY_PROVIDER
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.selects.select
import kotlinx.serialization.encodeToString
import java.util.concurrent.atomic.AtomicInteger

interface JobListener {
    suspend fun onVerified(ctx: DBContext, job: Job) {}
    suspend fun onCreate(ctx: DBContext, job: Job) {}
    suspend fun onTermination(ctx: DBContext, job: Job) {}
}

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
    private val projectCache: ProjectCache,
    private val developmentMode: Boolean = false,
) {
    private val listeners = ArrayList<JobListener>()

    fun addListener(listener: JobListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: JobListener) {
        listeners.remove(listener)
    }

    suspend fun startJob(
        req: BulkRequest<JobSpecification>,
        accessToken: String,
        principal: Actor,
        project: String?,
    ): JobsCreateResponse {
        data class JobWithProvider(val jobId: String, val provider: String)

        val tokensToInvalidateInCaseOfFailure = ArrayList<String>()
        val jobsToInvalidateInCaseOfFailure = ArrayList<JobWithProvider>()
        var verifiedJobsInCaseOfFailure: List<VerifiedJobWithAccessToken> = emptyList()

        val parameters = ArrayList<ByteArray>()

        try {
            // NOTE(Dan): Cannot convert due to suspension
            @Suppress("ConvertCallChainIntoSequence")
            val verifiedJobs = req.items
                // Immediately export parameters (before verification code changes it)
                .onEach { request ->
                    parameters.add(
                        defaultMapper
                            .encodeToString(parameterExportService.exportParameters(request))
                            .encodeToByteArray()
                    )
                }
                // Verify parameters
                .map { jobRequest ->
                    jobVerificationService.verifyOrThrow(
                        UnverifiedJob(jobRequest, principal.username, project),
                        listeners
                    )
                }
                // Check for duplicates
                .onEach { job ->
                    if (!job.job.specification.allowDuplicateJob) {
                        if (checkForDuplicateJob(principal, project, job)) {
                            throw JobException.Duplicate()
                        }
                    }
                }
                .toMutableList()

            verifiedJobsInCaseOfFailure = verifiedJobs

            // Reserve resources (and check that resources are available)
            for (job in verifiedJobs) {
                paymentService.reserve(
                    Payment.OfJob(
                        job.job,
                        timeUsedInMillis = job.job.specification.timeAllocation?.toMillis() ?: 1000L * 60 * 60,
                        chargeId = ""
                    )
                )
            }

            // Prepare job folders (needs to happen before database insertion)
            for ((index, jobWithToken) in verifiedJobs.withIndex()) {
                // TODO Temporary
                if (jobWithToken.job.specification.product.provider == "ucloud" && false) {
                    try {
                        val jobFolder = jobFileService.initializeResultFolder(jobWithToken)
                        val newJobWithToken = jobWithToken.copy(
                            job = jobWithToken.job.copy(
                                output = JobOutput(jobFolder.path)
                            )
                        )
                        verifiedJobs[index] = newJobWithToken
                        jobFileService.exportParameterFile(jobFolder.path, newJobWithToken, parameters[index])
                    } catch (ex: RPCException) {
                        if (ex.httpStatusCode == HttpStatusCode.PaymentRequired &&
                            jobWithToken.job.specification.product.provider == "aau"
                        ) {
                            log.warn("Silently ignoring lack of credits in storage due to temporary aau integration")
                        } else {
                            throw ex
                        }
                    }
                }
            }

            // Insert into databases
            db.withSession { session ->
                for (job in verifiedJobs) {
                    jobDao.create(session, job)
                    listeners.forEach { it.onCreate(session, job.job) }
                }
            }

            // Notify compute providers
            verifiedJobs.groupBy { it.job.specification.product.provider }.forEach { (provider, jobs) ->
                providers.proxyCall(
                    provider,
                    principal,
                    { it.api.create },
                    bulkRequestOf(jobs.map { it.job })
                ).orThrow()

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
                providers.proxyCall(
                    provider,
                    principal,
                    { it.api.delete },
                    bulkRequestOf(
                        jobQueryService
                            .retrievePrivileged(
                                db,
                                jobs.map { it.jobId },
                                JobDataIncludeFlags(includeParameters = true)
                            )
                            .map { it.value.job }
                    )
                )
            }

            db.withSession { session ->
                verifiedJobsInCaseOfFailure.forEach { (job) ->
                    listeners.forEach { it.onTermination(session, job) }
                }
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
        val providerId = providers.verifyProviderIsValid(providerActor)
        db.withSession { session ->
            val loadedJobs = loadAndVerifyProviderJobs(session, requests.keys, providerId)

            for ((jobId, updates) in requests) {
                val jobWithToken = loadedJobs[jobId] ?: error("Job not found, this should have failed earlier")
                val (job) = jobWithToken
                var currentState = job.currentState
                for (update in updates) {
                    if (update.expectedState != null && update.expectedState != currentState) continue
                    if (update.expectedDifferentState == true && update.state == currentState) continue

                    val newState = update.state
                    val newStatus = update.status

                    if (newState != null && !currentState.isFinal()) {
                        currentState = newState

                        if (newState.isFinal()) {
                            handleFinalState(jobWithToken)
                            listeners.forEach { it.onTermination(session, job) }
                        }
                    } else if (newState != null && currentState.isFinal()) {
                        log.info("Ignoring job update for $jobId by $providerId (bad state transition)")
                        continue
                    }

                    jobDao.insertUpdate(session, jobId, Time.now(), newState, newStatus)
                }
            }
        }
    }

    private suspend fun handleFinalState(jobWithToken: VerifiedJobWithAccessToken) {
        jobFileService.cleanupAfterMounts(jobWithToken)

        runCatching {
            AuthDescriptions.logout.call(
                Unit,
                serviceClient.withoutAuthentication().bearerAuth(jobWithToken.refreshToken)
            ).throwIfInternal()
        }

        /*
        val resp = MetadataDescriptions.verify.call(
            VerifyRequest(jobWithToken.job.files.map { it.path }),
            serviceClient
        )

        if (resp is IngoingCallResponse.Error) {
            log.warn("Failed to verify metadata for job: ${jobWithToken.job.id} (${resp.statusCode} ${resp.error})")
        }
         */
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
        val providerId = providers.verifyProviderIsValid(providerActor)
        val requests = request.items.groupBy { it.id }

        val duplicates = ArrayList<FindByStringId>()
        val insufficient = ArrayList<FindByStringId>()

        db.withSession { session ->
            val loadedJobs = loadAndVerifyProviderJobs(session, requests.keys, providerId)

            for ((jobId, charges) in requests) {
                val jobWithToken = loadedJobs[jobId] ?: error("Job should have been loaded by now")
                for (charge in charges) {
                    val chargeResult = paymentService.charge(
                        Payment.OfJob(
                            jobWithToken.job,
                            charge.wallDuration.toMillis(),
                            charge.chargeId
                        )
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
        providerId: String,
        flags: JobDataIncludeFlags = JobDataIncludeFlags(includeParameters = true),
    ): Map<String, VerifiedJobWithAccessToken> {
        val loadedJobs = jobQueryService.retrievePrivileged(session, jobIds, flags)
        if (loadedJobs.keys.size != jobIds.size) {
            throw RPCException("Not all jobs are known to UCloud", HttpStatusCode.NotFound)
        }

        val ownsAllJobs = loadedJobs.values.all { (it.job).specification.product.provider == providerId }
        if (!ownsAllJobs && !developmentMode) {
            throw RPCException("Provider is not authorized to perform these updates", HttpStatusCode.Forbidden)
        }
        return loadedJobs
    }

    private enum class Action {
        CANCEL,
        EXTEND,
        OPEN_INTERACTIVE_SESSION,
    }

    private suspend fun loadAndVerifyUserJobs(
        session: AsyncDBConnection,
        jobIds: Set<String>,
        user: Actor,
        @Suppress("UNUSED_PARAMETER") action: Action,
        flags: JobDataIncludeFlags = JobDataIncludeFlags(includeParameters = true),
    ): Map<String, VerifiedJobWithAccessToken> {
        val loadedJobs = jobQueryService.retrievePrivileged(session, jobIds, flags)
        if (loadedJobs.keys.size != jobIds.size) {
            throw RPCException("Not all jobs are known to UCloud", HttpStatusCode.NotFound)
        }

        val ownsAllJobs = loadedJobs.values.all { (job, _) ->
            if (user == Actor.System) return@all true
            val project = job.owner.project
            if (project != null) {
                val projectRole = projectCache.retrieveRole(user.safeUsername(), project)
                val isUserAndLauncher = job.owner.launchedBy == user.safeUsername() && projectRole != null
                val isProjectAdmin = projectRole?.isAdmin() == true
                isUserAndLauncher || isProjectAdmin
            } else {
                job.owner.launchedBy == user.safeUsername()
            }
        }
        if (!ownsAllJobs) {
            throw RPCException("Not all jobs are known to UCloud", HttpStatusCode.NotFound)
        }
        return loadedJobs
    }

    suspend fun submitFile(
        request: JobsControlSubmitFileRequest,
        providerActor: Actor,
        contentLength: Long?,
        content: ByteReadChannel,
    ) {
        val providerId = providers.verifyProviderIsValid(providerActor)
        val jobWithToken = loadAndVerifyProviderJobs(db, setOf(request.jobId), providerId).getValue(request.jobId)

        jobFileService.acceptFile(
            jobWithToken,
            request.filePath,
            contentLength ?: throw RPCException("Unknown length of payload", HttpStatusCode.BadRequest),
            content
        )
    }

    suspend fun retrieveAsProvider(
        request: JobsControlRetrieveRequest,
        providerActor: Actor,
    ): Job {
        val providerId = providers.verifyProviderIsValid(providerActor)
        return loadAndVerifyProviderJobs(db, setOf(request.id), providerId, request).getValue(request.id).job
    }

    suspend fun extendDuration(request: BulkRequest<JobsExtendRequestItem>, userActor: Actor.User) {
        val extensions = request.items.groupBy { it.jobId }
        db.withSession { session ->
            val jobs = loadAndVerifyUserJobs(
                session,
                extensions.keys,
                userActor,
                Action.EXTEND,
                flags = JobDataIncludeFlags(includeApplication = true, includeSupport = true, includeParameters = true)
            )

            for ((job) in jobs.values) {
                val appBackend = job.specification.resolvedApplication!!.invocation.tool.tool!!.description.backend
                val support = job.specification.resolvedSupport!!

                val isSupported =
                    (appBackend == ToolBackend.DOCKER && support.docker.timeExtension == true) ||
                        (appBackend == ToolBackend.VIRTUAL_MACHINE && support.virtualMachine.timeExtension == true)

                if (!isSupported) {
                    throw RPCException(
                        "Extension of deadline is not supported by the provider",
                        HttpStatusCode.BadRequest,
                        FEATURE_NOT_SUPPORTED_BY_PROVIDER
                    )
                }
            }

            val providerIds = jobs.values.map { it.job.specification.product.provider }

            for (providerId in providerIds) {
                providers.proxyCall(
                    providerId,
                    userActor,
                    { it.api.extend },
                    bulkRequestOf(request.items.mapNotNull { extensionRequest ->
                        val (job) = jobs.getValue(extensionRequest.jobId)
                        if (job.specification.product.provider != providerId) null
                        else JobsProviderExtendRequestItem(job, extensionRequest.requestedTime)
                    })
                ).orThrow()
            }

            request.items.groupBy { it.jobId }.forEach { (jobId, requests) ->
                val existingJob = jobs.getValue(jobId)
                val allocatedTime = existingJob.job.specification.timeAllocation?.toMillis() ?: 0L
                val requestedTime = requests.sumOf { it.requestedTime.toMillis() }

                jobDao.updateMaxTime(session, jobId, SimpleDuration.fromMillis(allocatedTime + requestedTime))
            }
        }
    }

    suspend fun cancel(request: BulkRequest<FindByStringId>, userActor: Actor) {
        val extensions = request.items.groupBy { it.id }

        db.withSession { session ->
            val jobs = loadAndVerifyUserJobs(session, extensions.keys, userActor, Action.CANCEL)
            val jobsByProvider = jobs.values.groupBy { it.job.specification.product.provider }
            for ((provider, providerJobs) in jobsByProvider) {
                providers.proxyCall(
                    provider,
                    userActor,
                    { it.api.delete },
                    bulkRequestOf(providerJobs.map { it.job }),
                ).orThrow()
            }
        }
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
                    JobDataIncludeFlags(includeUpdates = true, includeApplication = true, includeSupport = true)
                )

                val appBackend = initialJob.specification.resolvedApplication!!.invocation.tool.tool!!
                    .description.backend
                val support = initialJob.specification.resolvedSupport!!

                val logsSupported =
                    (appBackend == ToolBackend.DOCKER && support.docker.logs == true) ||
                        (appBackend == ToolBackend.VIRTUAL_MACHINE && support.virtualMachine.logs == true)

                val stateUpdate = initialJob.updates.lastOrNull { it.state != null }
                if (stateUpdate != null) {
                    sendWSMessage(
                        JobsFollowResponse(
                            emptyList(),
                            emptyList(),
                            initialJob.status
                        )
                    )
                }
                // NOTE(Dan): We do _not_ send the initial list of updates, instead we assume that clients will
                // retrieve them by themselves.
                var lastUpdate = initialJob.updates.maxByOrNull { it.timestamp }?.timestamp ?: 0L
                var streamId: String? = null
                val states = JobState.values()
                val currentState = AtomicInteger(JobState.IN_QUEUE.ordinal)

                coroutineScope {
                    val logJob = launch {
                        try {
                            while (isActive) {
                                if (currentState.get() == JobState.RUNNING.ordinal) {
                                    if (logsSupported) {
                                        providers.proxySubscription(
                                            initialJob.specification.product.provider,
                                            actor,
                                            { it.api.follow },
                                            JobsProviderFollowRequest.Init(initialJob),
                                            handler = { message ->
                                                if (streamId == null) {
                                                    streamId = message.streamId
                                                }

                                                // Providers are allowed to use negative ranks for control messages
                                                if (message.rank >= 0) {
                                                    sendWSMessage(
                                                        JobsFollowResponse(
                                                            emptyList(),
                                                            listOf(
                                                                JobsLog(
                                                                    message.rank,
                                                                    message.stdout,
                                                                    message.stderr
                                                                )
                                                            ),
                                                            null
                                                        )
                                                    )
                                                }
                                            }
                                        ).orThrow()
                                    }
                                    break
                                } else {
                                    delay(500)
                                }
                            }
                        } catch (ex: Throwable) {
                            if (ex !is CancellationException) {
                                log.warn(ex.stackTraceToString())
                            }
                        }
                    }

                    val updateJob = launch {
                        try {
                            var lastStatus: JobStatus? = null
                            while (isActive && !states[currentState.get()].isFinal()) {
                                val (job) = jobQueryService.retrieve(
                                    actor,
                                    ctx.project,
                                    request.id,
                                    JobDataIncludeFlags(includeUpdates = true)
                                )

                                currentState.set(job.status.state.ordinal)

                                // TODO not ideal for chatty providers
                                val updates = job.updates.filter { it.timestamp > lastUpdate }
                                if (updates.isNotEmpty()) {
                                    sendWSMessage(
                                        JobsFollowResponse(
                                            updates,
                                            emptyList(),
                                            null
                                        )
                                    )
                                    lastUpdate = job.updates.maxByOrNull { it.timestamp }?.timestamp ?: 0L
                                }

                                if (lastStatus != job.status) {
                                    sendWSMessage(JobsFollowResponse(emptyList(), emptyList(), job.status))
                                }

                                lastStatus = job.status
                                delay(1000)
                            }
                        } catch (ex: Throwable) {
                            if (ex !is CancellationException) {
                                log.warn(ex.stackTraceToString())
                            }
                        }
                    }

                    try {
                        select<Unit> {
                            logJob.onJoin {}
                            updateJob.onJoin {}
                        }
                    } finally {
                        val capturedId = streamId
                        if (capturedId != null) {
                            providers.proxyCall(
                                initialJob.specification.product.provider,
                                actor,
                                { it.api.follow },
                                JobsProviderFollowRequest.CancelStream(capturedId),
                                useWebsockets = true
                            )
                        }

                        runCatching { logJob.cancel("No longer following or EOF") }
                        runCatching { updateJob.cancel("No longer following or EOF") }
                    }
                }
            }
        }
    }

    suspend fun openInteractiveSession(
        request: BulkRequest<JobsOpenInteractiveSessionRequestItem>,
        actor: Actor,
    ): JobsOpenInteractiveSessionResponse {
        val errorMessage = "You do not have permissions to open an interactive session for this job"

        return db.withSession { session ->
            val requestsByJobId = request.items.groupBy { it.id }
            val jobIds = request.items.map { it.id }.toSet()
            val jobs = loadAndVerifyUserJobs(
                session,
                jobIds,
                actor,
                Action.OPEN_INTERACTIVE_SESSION,
                flags = JobDataIncludeFlags(includeApplication = true, includeSupport = true, includeParameters = true)
            ).values
            val jobsByProvider = jobs.groupBy { it.job.specification.product.provider }

            if (jobs.size != jobIds.size) {
                log.debug("Not all jobs were found")
                throw RPCException(errorMessage, HttpStatusCode.Forbidden)
            }

            for ((provider, jobs) in jobsByProvider) {
                for ((job) in jobs) {
                    val reqs = requestsByJobId.getValue(job.id)
                    val appBackend = job.specification.resolvedApplication!!.invocation.tool.tool!!.description.backend
                    val support = job.specification.resolvedSupport!!

                    for (req in reqs) {
                        val isSessionSupported = when (req.sessionType) {
                            InteractiveSessionType.WEB ->
                                (appBackend == ToolBackend.DOCKER && support.docker.web == true)
                            InteractiveSessionType.VNC ->
                                (appBackend == ToolBackend.DOCKER && support.docker.vnc == true) ||
                                    (appBackend == ToolBackend.VIRTUAL_MACHINE && support.virtualMachine.vnc == true)
                            InteractiveSessionType.SHELL ->
                                (appBackend == ToolBackend.DOCKER && support.docker.terminal == true) ||
                                    (appBackend == ToolBackend.VIRTUAL_MACHINE && support.virtualMachine.terminal == true)
                        }

                        if (!isSessionSupported) {
                            throw RPCException(
                                "Unsupported by the provider",
                                HttpStatusCode.BadRequest,
                                FEATURE_NOT_SUPPORTED_BY_PROVIDER
                            )
                        }
                    }
                }
            }

            val sessions = ArrayList<OpenSessionWithProvider>()
            for ((provider, jobs) in jobsByProvider) {
                val providerSpec = providers.retrieveProviderSpecification(provider)
                sessions.addAll(
                    providers
                        .proxyCall(
                            provider,
                            actor,
                            { it.api.openInteractiveSession },
                            bulkRequestOf(jobs.flatMap { (job) ->
                                requestsByJobId.getValue(job.id).map { req ->
                                    JobsProviderOpenInteractiveSessionRequestItem(job, req.rank, req.sessionType)
                                }
                            })
                        )
                        .orThrow()
                        .sessions
                        .map {
                            OpenSessionWithProvider(
                                with(providerSpec) {
                                    buildString {
                                        if (https) {
                                            append("https://")
                                        } else {
                                            append("http://")
                                        }

                                        append(domain)

                                        if (port != null) {
                                            append(":$port")
                                        }
                                    }
                                },
                                providerSpec.id,
                                it
                            )
                        }
                )
            }

            JobsOpenInteractiveSessionResponse(sessions)
        }
    }

    suspend fun retrieveUtilization(
        actorAndProject: ActorAndProject,
        jobId: String,
    ): JobsRetrieveUtilizationResponse {
        val (actor, project) = actorAndProject
        val job = jobQueryService
            .retrieve(
                actor,
                project,
                jobId,
                JobDataIncludeFlags(includeApplication = true, includeSupport = true, includeParameters = true)
            )
            .job

        val providerId = job.specification.product.provider
        val support = job.specification.resolvedSupport!!
        val appBackend = job.specification.resolvedApplication!!.invocation.tool.tool!!.description.backend

        val supported =
            (appBackend == ToolBackend.VIRTUAL_MACHINE && support.virtualMachine.utilization == true) ||
                (appBackend == ToolBackend.DOCKER && support.docker.utilization == true)

        if (!supported) {
            throw RPCException(
                "Utilization is not supported by the provider",
                HttpStatusCode.BadRequest,
                FEATURE_NOT_SUPPORTED_BY_PROVIDER
            )
        }

        val response = providers.proxyCall(
            providerId,
            null,
            { it.api.retrieveUtilization },
            Unit
        ).orThrow()

        return JobsRetrieveUtilizationResponse(
            response.capacity,
            response.usedCapacity,
            response.queueStatus
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
