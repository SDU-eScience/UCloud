package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductArea
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.DBContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

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
    db: AsyncDBSessionFactory,
    providers: Providers<ComputeCommunication>,
    support: ProviderSupport<ComputeCommunication, Product.Compute, ComputeSupport>,
    serviceClient: AuthenticatedClient,
) : ResourceService<Job, JobSpecification, JobUpdate, JobIncludeFlags, JobStatus,
    Product.Compute, ComputeSupport, ComputeCommunication>(db, providers, support, serviceClient) {
    private val listeners = ArrayList<JobListener>()

    fun addListener(listener: JobListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: JobListener) {
        listeners.remove(listener)
    }

    override val productArea: ProductArea = ProductArea.COMPUTE
    override val serializer: KSerializer<Job> = serializer()
    override val table: SqlObject.Table = SqlObject.Table("app_orchestrator.jobs")
    override val sortColumn: SqlObject.Column = SqlObject.Column(table, "resource")
    override val updateSerializer: KSerializer<JobUpdate> = serializer()

    override fun controlApi() = JobsControl
    override fun userApi() = Jobs
    override fun providerApi(comms: ProviderComms) = JobsProvider(comms.provider.id)

    override suspend fun createSpecifications(
        idWithSpec: List<Pair<Long, JobSpecification>>,
        session: AsyncDBConnection,
        allowDuplicates: Boolean
    ) {
        /*
        // NOTE(Dan): Cannot convert due to suspension
        @Suppress("ConvertCallChainIntoSequence")
        val verifiedJobs = req.items
            // Verify parameters
            .map { jobRequest ->
                jobVerificationService.verifyOrThrow(
                    UnverifiedJob(jobRequest, principal.username, project),
                    listeners
                )
            }
            .toMutableList()

         */
        // TODO Insert into the database
    }

    override suspend fun onUpdate(
        resources: List<Job>,
        updates: List<ResourceUpdateAndId<JobUpdate>>,
        session: AsyncDBConnection
    ) {
        val loadedJobs = resources.associateBy { it.id }

        for ((jobId, updatesForJob) in updates.groupBy { it.id }) {
            val job = loadedJobs[jobId] ?: error("Job not found, this should have failed earlier")
            var currentState = job.status.state
            for ((_, update) in updatesForJob) {
                if (update.expectedState != null && update.expectedState != currentState) continue
                if (update.expectedDifferentState == true && update.state == currentState) continue

                val newState = update.state
                val newStatus = update.status

                if (newState != null && !currentState.isFinal()) {
                    @Suppress("SqlResolve")
                    currentState = newState

                    if (newState.isFinal()) {
                        handleFinalState(job)
                        listeners.forEach { it.onTermination(session, job) }
                    }

                    // TODO Update status
                } else if (newState != null && currentState.isFinal()) {
                    log.info(
                        "Ignoring job update for $jobId by ${job.specification.product.provider} " +
                            "(bad state transition)"
                    )
                    continue
                }
            }
        }
    }

    override suspend fun browseQuery(flags: JobIncludeFlags?): PartialQuery {
        return PartialQuery(
            {
                setParameter("filter_state", flags?.filterState?.name)
            },
            """
                select
                    job.resource,
                    job,
                    array_remove(array_agg(distinct res), null),
                    array_remove(array_agg(distinct param), null),
                    app,
                    t

                from
                    app_orchestrator.jobs job join
                    app_store.applications app on
                        job.application_name = app.name and job.application_version = app.version join
                    app_store.tools t on app.tool_name = t.name and app.tool_version = t.version left join
                    app_orchestrator.job_resources res on job.resource = res.job_id left join
                    app_orchestrator.job_input_parameters param on job.resource = param.job_id

                where
                    (:filter_state::text is null or current_state = :filter_state::text)

                group by job.resource, job.*, app.*, t.*
            """
        )
    }

    private suspend fun handleFinalState(job: Job) {
        // jobFileService.cleanupAfterMounts(jobWithToken)

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

    suspend fun extendDuration(request: BulkRequest<JobsExtendRequestItem>, userActor: Actor.User) {
        /*
        val extensions = request.items.groupBy { it.jobId }
        db.withSession { session ->
            val jobs = loadAndVerifyUserJobs(
                session,
                extensions.keys,
                userActor,
                Action.EXTEND,
                flags = JobIncludeFlags(includeApplication = true, includeSupport = true, includeParameters = true)
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
         */
    }

    suspend fun cancel(request: BulkRequest<FindByStringId>, userActor: Actor) {
        /*
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
         */
    }

    suspend fun follow(
        callHandler: CallHandler<JobsFollowRequest, JobsFollowResponse, *>,
        actor: Actor,
    ) {
        /*
        callHandler.withContext<WSCall> {
            val (initialJob) = jobQueryService.retrieve(
                actor,
                ctx.project,
                request.id,
                JobIncludeFlags(includeUpdates = true, includeApplication = true, includeSupport = true)
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
                                                        listOf(JobsLog(message.rank, message.stdout, message.stderr)),
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
                                JobIncludeFlags(includeUpdates = true)
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
         */
    }

    suspend fun openInteractiveSession(
        request: BulkRequest<JobsOpenInteractiveSessionRequestItem>,
        actor: Actor,
    ): JobsOpenInteractiveSessionResponse {
        /*
        val errorMessage = "You do not have permissions to open an interactive session for this job"

        return db.withSession { session ->
            val requestsByJobId = request.items.groupBy { it.id }
            val jobIds = request.items.map { it.id }.toSet()
            val jobs = loadAndVerifyUserJobs(
                session,
                jobIds,
                actor,
                Action.OPEN_INTERACTIVE_SESSION,
                flags = JobIncludeFlags(includeApplication = true, includeSupport = true, includeParameters = true)
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
         */
        TODO()
    }

    suspend fun retrieveUtilization(
        actorAndProject: ActorAndProject,
        jobId: String,
    ): JobsRetrieveUtilizationResponse {
        /*
        val (actor, project) = actorAndProject
        val job = jobQueryService
            .retrieve(
                actor,
                project,
                jobId,
                JobIncludeFlags(includeApplication = true, includeSupport = true, includeParameters = true)
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
         */
        TODO()
    }

    companion object : Loggable {
        override val log = logger()
    }
}
