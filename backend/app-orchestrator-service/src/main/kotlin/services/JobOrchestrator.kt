package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.*
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductType
import dk.sdu.cloud.accounting.api.providers.SortDirection
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.store.api.ToolBackend
import dk.sdu.cloud.app.store.api.AppParameterValue
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsProvider
import dk.sdu.cloud.file.orchestrator.api.FilesProvider
import dk.sdu.cloud.file.orchestrator.service.FileCollectionService
import dk.sdu.cloud.file.orchestrator.service.StorageCommunication
import dk.sdu.cloud.provider.api.FEATURE_NOT_SUPPORTED_BY_PROVIDER
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.coroutines.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString

interface JobListener {
    suspend fun onVerified(ctx: DBContext, job: Job) {
        // Empty
    }

    suspend fun onCreate(ctx: DBContext, job: Job) {
        // Empty
    }

    suspend fun onTermination(ctx: DBContext, job: Job) {
        // Empty
    }
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
    projectCache: ProjectCache,
    db: AsyncDBSessionFactory,
    providers: Providers<ComputeCommunication>,
    support: ProviderSupport<ComputeCommunication, Product.Compute, ComputeSupport>,
    serviceClient: AuthenticatedClient,
    private val appService: AppStoreCache,
) : ResourceService<Job, JobSpecification, JobUpdate, JobIncludeFlags, JobStatus,
        Product.Compute, ComputeSupport, ComputeCommunication>(projectCache, db, providers, support, serviceClient) {

    lateinit var exporter: ParameterExportService // TODO(Dan): Cyclic-dependency hack

    override val browseStrategy: ResourceBrowseStrategy = ResourceBrowseStrategy.NEW
    private val storageProviders = Providers(serviceClient) {
        StorageCommunication(
            it.client,
            it.client,
            it.provider,
            FilesProvider(it.provider.id),
            FileCollectionsProvider(it.provider.id)
        )
    }

    private val verificationService = JobVerificationService(
        appService,
        this,
        FileCollectionService(
            projectCache,
            db,
            storageProviders,
            ProviderSupport(storageProviders, serviceClient) {
                it.filesApi.retrieveProducts.call(Unit, it.client).orThrow().responses
            },
            serviceClient
        )
    )

    private val listeners = ArrayList<JobListener>()

    fun addListener(listener: JobListener) {
        listeners.add(listener)
    }

    override val productArea: ProductType = ProductType.COMPUTE
    override val serializer: KSerializer<Job> = Job.serializer()
    override val table: SqlObject.Table = SqlObject.Table("app_orchestrator.jobs")
    override val sortColumns = mapOf(
        "resource" to SqlObject.Column(table, "resource"),
    )

    override val defaultSortColumn: SqlObject.Column = SqlObject.Column(table, "resource")
    override val defaultSortDirection: SortDirection = SortDirection.descending
    override val updateSerializer: KSerializer<JobUpdate> = JobUpdate.serializer()

    override fun controlApi() = JobsControl
    override fun userApi() = Jobs
    override fun providerApi(comms: ProviderComms) = JobsProvider(comms.provider.id)

    override suspend fun verifyProviderSupportsCreate(
        spec: JobSpecification,
        res: ProductRefOrResource<Job>,
        support: ComputeSupport
    ) {
        val application = appService.resolveApplication(spec.application)
            ?: throw JobException.VerificationError("Application does not exist")

        when (application.invocation.tool.tool!!.description.backend) {
            ToolBackend.DOCKER -> {
                if (support.docker.enabled != true) {
                    throw JobException.VerificationError("Application is not supported by provider")
                }
            }

            ToolBackend.VIRTUAL_MACHINE -> {
                if (support.virtualMachine.enabled != true) {
                    throw JobException.VerificationError("Application is not supported by provider")
                }
            }

            ToolBackend.NATIVE -> {
                if (support.native.enabled != true) {
                    throw JobException.VerificationError("Application is not supported by provider")
                }
            }

            else -> {
                throw JobException.VerificationError("Unsupported application")
            }
        }
    }

    override suspend fun createSpecifications(
        actorAndProject: ActorAndProject,
        idWithSpec: List<Pair<Long, JobSpecification>>,
        session: AsyncDBConnection,
        allowDuplicates: Boolean
    ) {
        idWithSpec.forEach { (id, spec) ->
            verificationService.verifyOrThrow(actorAndProject, spec)

            val preliminaryJob = Job.fromSpecification(id.toString(), actorAndProject, spec)
            listeners.forEach {
                it.onVerified(db, preliminaryJob)
            }
        }

        val exports = idWithSpec.map { (_, spec) ->
            exporter.exportParameters(spec)
        }

        session.sendPreparedStatement(
            {
                val applicationNames = ArrayList<String>().also { setParameter("application_names", it) }
                val applicationVersions = ArrayList<String>().also { setParameter("application_versions", it) }
                val timeAllocationMillis = ArrayList<Long?>().also { setParameter("time_allocation", it) }
                val names = ArrayList<String?>().also { setParameter("names", it) }
                val resources = ArrayList<Long>().also { setParameter("resources", it) }
                val openedFiles = ArrayList<String?>().also { setParameter("opened_file", it) }
                val replicas = ArrayList<Int>().also { setParameter("replicas", it) }
                val restartOnExit = ArrayList<Boolean>().also { setParameter("restart_on_exit", it) }
                val sshEnabled = ArrayList<Boolean>().also { setParameter("ssh_enabled", it) }
                setParameter("exports", exports.map { defaultMapper.encodeToString(it) })

                for ((id, spec) in idWithSpec) {
                    applicationNames.add(spec.application.name)
                    applicationVersions.add(spec.application.version)
                    timeAllocationMillis.add(spec.timeAllocation?.toMillis())
                    names.add(spec.name)
                    resources.add(id)
                    openedFiles.add(spec.openedFile)
                    replicas.add(spec.replicas)
                    restartOnExit.add(spec.restartOnExit ?: false)
                    sshEnabled.add(spec.sshEnabled ?: false)
                }
            },
            """
                with bulk_data as (
                    select unnest(:application_names::text[]) app_name, unnest(:application_versions::text[]) app_ver,
                           unnest(:time_allocation::bigint[]) time_alloc, unnest(:names::text[]) n, 
                           unnest(:resources::bigint[]) resource, unnest(:exports::jsonb[]) export, 
                           unnest(:opened_file::text[]) opened_file, unnest(:replicas::int[]) replicas,
                           unnest(:restart_on_exit::bool[]) restart_on_exit,
                           unnest(:ssh_enabled::bool[]) ssh_enabled
                )
                insert into app_orchestrator.jobs
                    (application_name, application_version, time_allocation_millis, name, 
                     output_folder, current_state, started_at, resource, job_parameters, opened_file, replicas,
                     restart_on_exit, ssh_enabled) 
                select app_name, app_ver, time_alloc, n, null, 'IN_QUEUE', null, resource, export, opened_file,
                       replicas, restart_on_exit, ssh_enabled
                from bulk_data
            """,
            "job spec create"
        )

        insertResources(idWithSpec, ctx = session)

        session.sendPreparedStatement(
            {
                val jobIds = ArrayList<Long>().also { setParameter("job_ids", it) }
                val values = ArrayList<String>().also { setParameter("values", it) }
                val names = ArrayList<String>().also { setParameter("names", it) }

                for ((id, spec) in idWithSpec) {
                    for ((name, value) in (spec.parameters ?: emptyMap())) {
                        jobIds.add(id)
                        values.add(defaultMapper.encodeToString(value))
                        names.add(name)
                    }
                }
            },
            """
                insert into app_orchestrator.job_input_parameters (name, value, job_id)  
                select unnest(:names::text[]), unnest(:values::jsonb[]), unnest(:job_ids::bigint[])
            """,
            "job input parameters create"
        )

        idWithSpec.forEach { (id, spec) ->
            val preliminaryJob = Job.fromSpecification(id.toString(), actorAndProject, spec)
            listeners.forEach { it.onCreate(db, preliminaryJob) }
        }
    }

    override suspend fun deleteSpecification(
        resourceIds: List<Long>,
        resources: List<Job>,
        session: AsyncDBConnection
    ) {
        session.sendPreparedStatement(
            {
                setParameter("job_ids", resourceIds)
            },
            """
                delete from app_orchestrator.job_resources
                where job_id = some(:job_ids::bigint[])
            """
        )

        // Update ingresses?

        // Delete from job_input_parameters



        super.deleteSpecification(resourceIds, resources, session)
    }

    suspend fun insertResources(
        idWithSpec: List<Pair<Long, JobSpecification>>,
        resetExistingResources: Boolean = false,
        ctx: DBContext = db,
    ) {
        ctx.withSession { session ->
            if (resetExistingResources) {
                session.sendPreparedStatement(
                    {
                        setParameter("job_ids", idWithSpec.map { it.first })
                    },
                    """
                        delete from app_orchestrator.job_resources
                        where job_id = some(:job_ids::bigint[])
                    """,
                    "job resource reset"
                )
            }

            session.sendPreparedStatement(
                {
                    val jobIds = ArrayList<Long>().also { setParameter("job_ids", it) }
                    val resources = ArrayList<String>().also { setParameter("resources", it) }

                    for ((id, spec) in idWithSpec) {
                        for (resource in (spec.resources ?: emptyList())) {
                            jobIds.add(id)
                            resources.add(defaultMapper.encodeToString(resource))
                        }
                    }
                },
                """
                    insert into app_orchestrator.job_resources (resource, job_id) 
                    select unnest(:resources::jsonb[]), unnest(:job_ids::bigint[])
                """,
                "job resources create"
            )
        }
    }

    override suspend fun onUpdate(
        resources: List<Job>,
        updates: List<ResourceUpdateAndId<JobUpdate>>,
        session: AsyncDBConnection
    ) {
        val loadedJobs = resources.associateBy { it.id }
        val jobsToRestart = ArrayList<Job>()

        for ((jobId, updatesForJob) in updates.groupBy { it.id }) {
            val job = loadedJobs[jobId] ?: error("Job not found, this should have failed earlier")
            var currentState = job.status.state
            for ((_, update) in updatesForJob) {
                if (update.expectedState != null && update.expectedState != currentState) continue
                if (update.expectedDifferentState == true && update.state == currentState) continue

                val newState = update.state
                val didChange = newState != null || update.outputFolder != null || update.newTimeAllocation != null ||
                    update.newMounts != null

                var updateToApply = update

                if (didChange && !currentState.isFinal()) {
                    if (newState != null && newState.isFinal()) {
                        if (job.specification.restartOnExit == true && update.allowRestart == true) {
                            updateToApply = JobUpdate(
                                JobState.SUSPENDED,
                                timestamp = Time.now(),
                                allowRestart = true,
                            )

                            jobsToRestart.add(job)
                        }

                        // NOTE(Dan): We are running onTermination handlers regardless even if we are about to restart.
                        listeners.forEach {
                            try {
                                it.onTermination(session, job)
                            } catch (ex: Throwable) {
                                log.warn("Failure while terminating job: $job\n${ex.stackTraceToString()}")
                            }
                        }
                    }

                    session.sendPreparedStatement(
                        {
                            setParameter("new_state", updateToApply.state?.name)
                            setParameter("output_folder", updateToApply.outputFolder)
                            setParameter("new_time_allocation", updateToApply.newTimeAllocation)
                            setParameter("allow_restart", updateToApply.allowRestart)
                            setParameter("job_id", jobId)
                        },
                        """
                            update app_orchestrator.jobs
                            set
                                current_state = coalesce(:new_state::text, current_state),
                                started_at = case
                                    when :new_state = 'RUNNING' then coalesce(started_at, now())
                                    else started_at
                                end,
                                last_update = now(),
                                output_folder = coalesce(:output_folder::text, output_folder),
                                time_allocation_millis = coalesce(:new_time_allocation, time_allocation_millis),
                                allow_restart = coalesce(:allow_restart, allow_restart)
                            where resource = :job_id
                        """,
                        "job update"
                    )

                    val newMounts = updateToApply.newMounts
                    if (newMounts != null) {
                        val allResources = job.specification.resources ?: emptyList()
                        val nonMountResources = allResources.filter { it !is AppParameterValue.File }
                        val validMountResources = verificationService.checkAndReturnValidFiles(
                            job.owner.toActorAndProject(),
                            newMounts.map { AppParameterValue.File(it) }
                        )

                        val newSpecification = job.specification.copy(
                            resources = nonMountResources + validMountResources
                        )

                        insertResources(
                            listOf(job.id.toLong() to newSpecification), 
                            resetExistingResources = true,
                            ctx = session,
                        )
                    }
                } else if (newState != null && currentState.isFinal()) {
                    log.info(
                        "Ignoring job update for $jobId by ${job.specification.product.provider} " +
                                "(bad state transition)"
                    )
                    continue
                }
            }
        }

        if (jobsToRestart.isNotEmpty()) {
            GlobalScope.launch {
                for (job in jobsToRestart) {
                    try {
                        val jobWithNewState = job.copy(
                            status = job.status.copy(
                                state = JobState.SUSPENDED
                            )
                        )
                        performUnsuspension(listOf(jobWithNewState))
                    } catch (ex: Throwable) {
                        log.info("Failed to restart job: $job\n${ex.stackTraceToString()}")
                    }
                }
            }
        }
    }

    override suspend fun applyFilters(actor: Actor, query: String?, flags: JobIncludeFlags?): PartialQuery {
        return PartialQuery(
            {
                setParameter("query", query)
                setParameter("filter_state", flags?.filterState?.name)
                setParameter("filter_application", flags?.filterApplication)
            },
            buildString {
                appendLine("(")
                appendLine("  true")
                if (flags?.filterState != null) {
                    appendLine("  and spec.current_state = :filter_state")
                }

                if (flags?.filterApplication != null) {
                    appendLine("  and spec.application_name = :filter_application")
                }

                if (query != null) {
                    appendLine("  and (")
                    appendLine("    spec.name ilike '%' || :query || '%'")
                    appendLine("    or spec.resource::text ilike '%' || :query || '%'")
                    if (flags?.includeApplication != false) {
                        appendLine("    or spec.application_name ilike '%' || :query || '%'")
                    }
                    appendLine("  )")
                }
                appendLine(")")
            }
        )
    }

    override suspend fun browseQuery(
        actorAndProject: ActorAndProject,
        flags: JobIncludeFlags?,
        query: String?
    ): PartialQuery {
        @Suppress("SqlResolve")
        return PartialQuery(
            {
                setParameter("query", query)
                setParameter("filter_state", flags?.filterState?.name)
                setParameter("filter_application", flags?.filterApplication)
            },
            buildString {
                run {
                    appendLine("select")
                    appendLine("  (resc.spec).resource, resc.spec")

                    if (flags?.includeParameters != false) {
                        appendLine("  , array_remove(array_agg(distinct res), null)")
                        appendLine("  , array_remove(array_agg(distinct param), null)")
                    } else {
                        appendLine("  , array[]::app_orchestrator.job_resources[] as res")
                        appendLine("  , array[]::app_orchestrator.job_input_parameters[] as res")
                    }

                    if (flags?.includeApplication != false) {
                        appendLine("  , app, t")
                    } else {
                        appendLine("  , null::app_store.applications as app, null::app_store.tools as t")
                    }
                }
                run {
                    appendLine("from")
                    appendLine("  relevant_resources resc")

                    if (flags?.includeApplication != false) {
                        appendLine("  join app_store.applications app on (resc.spec).application_name = app.name " +
                                "and (resc.spec).application_version = app.version")
                        appendLine("  join app_store.tools t on app.tool_name = t.name " +
                                "and app.tool_version = t.version")
                    }

                    if (flags?.includeParameters != false) {
                        appendLine("  left join app_orchestrator.job_resources res on " +
                                "(resc.spec).resource = res.job_id")
                        appendLine("  left join app_orchestrator.job_input_parameters param on " +
                                "(resc.spec).resource = param.job_id")
                    }
                }

                run {
                    if (flags?.includeParameters != false) {
                        append("group by (resc.spec).resource, resc.spec")
                        if (flags?.includeApplication != false) {
                            appendLine(", app.*, t.*")
                        }
                    }
                }
            }
        )
    }

    protected override suspend fun attachExtraInformation(resource: Job, actor: Actor, flags: JobIncludeFlags?): Job {
        val needsProviderInfo = actor == Actor.System || actor.safeUsername().startsWith(AuthProviders.PROVIDER_PREFIX)
        if (!needsProviderInfo) return resource

        val actorAndProject = resource.owner.toActorAndProject()

        // NOTE(Dan): This will throw if the parameters are no longer OK. This will modify the specification if the
        // mounted resources are no longer OK. We need this, since it will add information about the read/write status
        // of mounts.

        // TODO(Dan): Centralize some of these procedures, it seems insane that we have it in three different places. 

        // TODO(Dan): Catching this, since it does not appear to be a good idea that we just throw in case of errors.

        // TODO(Dan): Should we be re-inserting resources in case of modifications? Technically this should be done by
        // the loop also, so it should be safe not to do it. But we run the risk of these being slightly out-of-date
        // with each other.

        runCatching {
            verificationService.verifyOrThrow(actorAndProject, resource.specification)
        }

        return resource
    }

    suspend fun extendDuration(
        actorAndProject: ActorAndProject,
        request: BulkRequest<JobsExtendRequestItem>
    ): BulkResponse<Unit?> {
        return proxy.bulkProxy(
            actorAndProject,
            request,
            BulkProxyInstructions.pureProcedure(
                service = this,
                retrieveCall = { providerApi(it).extend },
                requestToId = { it.jobId },
                resourceToRequest = { req, res -> JobsProviderExtendRequestItem(res, req.requestedTime) },
                verifyRequest = { req, res, support ->
                    val job = (res as ProductRefOrResource.SomeResource).resource
                    val application = appService.resolveApplication(job.specification.application)
                        ?: throw RPCException("Unknown application", HttpStatusCode.BadRequest)
                    val appBackend = application.invocation.tool.tool!!.description.backend

                    val isSupported =
                        (appBackend == ToolBackend.DOCKER && support.docker.timeExtension == true) ||
                                (appBackend == ToolBackend.VIRTUAL_MACHINE && support.virtualMachine.timeExtension == true) ||
                                (appBackend == ToolBackend.NATIVE && support.native.timeExtension == true)

                    if (!isSupported) {
                        throw RPCException(
                            "Extension of deadline is not supported by the provider",
                            HttpStatusCode.BadRequest,
                            FEATURE_NOT_SUPPORTED_BY_PROVIDER
                        )
                    }
                }
            )
        )
    }

    suspend fun terminate(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>,
    ): BulkResponse<Unit?> {
        return proxy.bulkProxy(
            actorAndProject,
            request,
            BulkProxyInstructions.pureProcedure(
                service = this,
                retrieveCall = { providerApi(it).terminate },
                requestToId = { it.id },
                resourceToRequest = { _, res -> res }
            )
        )
    }

    suspend fun suspendJob(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>
    ): BulkResponse<Unit?> {
        return proxy.bulkProxy(
            actorAndProject,
            request,
            BulkProxyInstructions.pureProcedure(
                service = this,
                retrieveCall = { providerApi(it).suspend },
                requestToId = { it.id },
                resourceToRequest = { req, res -> JobsProviderSuspendRequestItem(res) },
                verifyRequest = { req, res, support ->
                    val job = (res as ProductRefOrResource.SomeResource).resource
                    val application = appService.resolveApplication(job.specification.application)
                        ?: throw RPCException("Unknown application", HttpStatusCode.BadRequest)
                    val appBackend = application.invocation.tool.tool!!.description.backend

                    val isSupported =
                            (appBackend == ToolBackend.VIRTUAL_MACHINE && support.virtualMachine.suspension == true)

                    if (!isSupported) {
                        throw RPCException(
                            "Suspension of jobs are not supported. Please reload and try again.",
                            HttpStatusCode.BadRequest,
                            FEATURE_NOT_SUPPORTED_BY_PROVIDER
                        )
                    }
                }
            )
        )
    }

    suspend fun unsuspendJob(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>
    ): BulkResponse<Unit?> {
        val jobs = retrieveBulk(
            actorAndProject,
            request.items.map { it.id },
            permissionOneOf = listOf(Permission.EDIT)
        )
        performUnsuspension(jobs)
        return BulkResponse(request.items.map { Unit })
    }

    private fun ResourceOwner.toActorAndProject(): ActorAndProject {
        return ActorAndProject(
            Actor.SystemOnBehalfOfUser(createdBy),
            project
        )
    }

    suspend fun performUnsuspension(jobs: List<Job>) {
        for (job in jobs) {
            val actorAndProject = job.owner.toActorAndProject()

            // NOTE(Dan): This will throw if the parameters are no longer OK. This will modify the specification if the
            // mounted resources are no longer OK.
            verificationService.verifyOrThrow(actorAndProject, job.specification)

            // NOTE(Dan): We reset the resources associated with the job. We also need to create a new session per job
            // to ensure that we don't roll back changes for a job which has already been unsuspended.
            insertResources(listOf(job.id.toLong() to job.specification), resetExistingResources = true)

            providers.invokeCall(
                job.specification.product.provider,
                actorAndProject,
                { it.api.unsuspend },
                BulkRequest(listOf(JobsProviderUnsuspendRequestItem(job))),
                signedIntentFromEndUser = null, // TODO This is going to cause issues with #3367
            )
        }
    }

    suspend fun follow(
        callHandler: CallHandler<JobsFollowRequest, JobsFollowResponse, *>,
    ): Unit = with(callHandler) {
        withContext<WSCall> {
            val initialJob = retrieveBulk(
                callHandler.actorAndProject, listOf(request.id), listOf(Permission.READ),
                flags = JobIncludeFlags(includeUpdates = true, includeSupport = true)
            ).single()

            val support = initialJob.status.resolvedSupport!!.support
            val app = appService.resolveApplication(initialJob.specification.application)!!
            val backend = app.invocation.tool.tool!!.description.backend
            val logsSupported =
                (backend == ToolBackend.DOCKER && support.docker.logs == true) ||
                        (backend == ToolBackend.VIRTUAL_MACHINE && support.virtualMachine.logs == true) ||
                        (backend == ToolBackend.NATIVE && support.native.logs == true)

                    // NOTE(Dan): We do _not_ send the initial list of updates, instead we assume that clients will
            // retrieve them by themselves.
            sendWSMessage(JobsFollowResponse(emptyList(), emptyList(), initialJob.status))

            var lastUpdate = initialJob.updates.maxByOrNull { it.timestamp }?.timestamp ?: 0L
            var streamId: String? = null
            val states = JobState.values()
            val currentState = AtomicInteger(JobState.IN_QUEUE.ordinal)

            coroutineScope {
                val logJob = if (!logsSupported) null else launch {
                    while (isActive) {
                        try {
                            if (currentState.get() == JobState.RUNNING.ordinal) {
                                proxy.proxySubscription(
                                    actorAndProject,
                                    Unit,
                                    object : SubscriptionProxyInstructions<ComputeCommunication, ComputeSupport, Job,
                                            Unit, JobsProviderFollowRequest, JobsProviderFollowResponse>() {
                                        override val isUserRequest: Boolean = true
                                        override fun retrieveCall(comms: ComputeCommunication) =
                                            providerApi(comms).follow

                                        override suspend fun verifyAndFetchResources(
                                            actorAndProject: ActorAndProject,
                                            request: Unit
                                        ): RequestWithRefOrResource<Unit, Job> {
                                            return Unit to ProductRefOrResource.SomeResource(initialJob)
                                        }

                                        override suspend fun beforeCall(
                                            provider: String,
                                            resource: RequestWithRefOrResource<Unit, Job>
                                        ): JobsProviderFollowRequest {
                                            return JobsProviderFollowRequest.Init(initialJob)
                                        }

                                        override suspend fun onMessage(
                                            provider: String,
                                            resource: RequestWithRefOrResource<Unit, Job>,
                                            message: JobsProviderFollowResponse
                                        ) {
                                            if (streamId == null) streamId = message.streamId
                                            sendWSMessage(
                                                JobsFollowResponse(
                                                    emptyList(),
                                                    listOf(JobsLog(message.rank, message.stdout, message.stderr))
                                                )
                                            )
                                        }
                                    }
                                )
                            }
                        } catch (ignore: java.util.concurrent.CancellationException) {
                            break
                        } catch (ex: Throwable) {
                            log.debug("Caught exception while following logs:\n${ex.stackTraceToString()}")
                            break
                        }

                        delay(100)
                    }
                }

                val updateJob = launch {
                    try {
                        var lastStatus: JobStatus? = null
                        while (isActive && !states[currentState.get()].isFinal()) {
                            val job = retrieveBulk(
                                actorAndProject, listOf(request.id), listOf(Permission.READ),
                                JobIncludeFlags(includeUpdates = true)
                            ).single()

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
                    updateJob.join()
                } finally {
                    val capturedId = streamId
                    if (capturedId != null) {
                        proxy.proxy(
                            actorAndProject,
                            Unit,
                            object : ProxyInstructions<ComputeCommunication, ComputeSupport, Job, Unit,
                                    JobsProviderFollowRequest, JobsProviderFollowResponse>() {
                                override val isUserRequest: Boolean = true
                                override fun retrieveCall(comms: ComputeCommunication) = providerApi(comms).follow

                                override suspend fun verifyAndFetchResources(
                                    actorAndProject: ActorAndProject,
                                    request: Unit
                                ): RequestWithRefOrResource<Unit, Job> =
                                    Unit to ProductRefOrResource.SomeResource(initialJob)

                                override suspend fun beforeCall(
                                    provider: String,
                                    resource: RequestWithRefOrResource<Unit, Job>
                                ): JobsProviderFollowRequest = JobsProviderFollowRequest.CancelStream(capturedId)

                            },
                            useHttpClient = false
                        )
                    }

                    runCatching { logJob?.cancel("No longer following or EOF") }
                    runCatching { updateJob.cancel("No longer following or EOF") }
                }
            }
        }
    }

    suspend fun openInteractiveSession(
        actorAndProject: ActorAndProject,
        request: BulkRequest<JobsOpenInteractiveSessionRequestItem>,
    ): JobsOpenInteractiveSessionResponse {
        val jobIdToProvider = HashMap<String, String>()
        val responses = proxy.bulkProxy(
            actorAndProject,
            request,
            BulkProxyInstructions.pureProcedure(
                service = this,
                retrieveCall = { providerApi(it).openInteractiveSession },
                requestToId = { it.id },
                resourceToRequest = { req, res ->
                    jobIdToProvider[req.id] = res.specification.product.provider
                    JobsProviderOpenInteractiveSessionRequestItem(res, req.rank, req.sessionType)
                }
            )
        )

        return BulkResponse(
            request.items.zip(responses.responses).map { (request, response) ->
                val providerSpec = providers.prepareCommunication(jobIdToProvider.getValue(request.id)).provider
                if (response == null) null
                else OpenSessionWithProvider(
                    with(providerSpec) {
                        buildString {
                            val domainOverride = response.domainOverride
                            if (domainOverride != null) {
                                append(domainOverride)
                            } else {
                                if (https) append("https://") else append("http://")
                                append(domain)
                                if (port != null) append(":$port")
                            }
                        }
                    },
                    providerSpec.id,
                    response
                )
            }
        )
    }

    suspend fun retrieveUtilization(
        actorAndProject: ActorAndProject,
        request: JobsRetrieveUtilizationRequest
    ): JobsRetrieveUtilizationResponse {
        return proxy.proxy(
            actorAndProject,
            request,
            object : ProxyInstructions<ComputeCommunication, ComputeSupport, Job,
                    JobsRetrieveUtilizationRequest, JobsProviderUtilizationRequest, JobsProviderUtilizationResponse>() {
                override val isUserRequest: Boolean = false
                override fun retrieveCall(comms: ComputeCommunication) = providerApi(comms).retrieveUtilization

                override suspend fun verifyAndFetchResources(
                    actorAndProject: ActorAndProject,
                    request: JobsRetrieveUtilizationRequest
                ): RequestWithRefOrResource<JobsRetrieveUtilizationRequest, Job> {
                    val job = retrieveBulk(actorAndProject, listOf(request.jobId), listOf(Permission.READ)).single()
                    return RequestWithRefOrResource(request, ProductRefOrResource.SomeResource(job))
                }

                override suspend fun verifyRequest(
                    request: JobsRetrieveUtilizationRequest,
                    res: ProductRefOrResource<Job>,
                    support: ComputeSupport
                ) {
                    val job = (res as ProductRefOrResource.SomeResource).resource
                    val app = appService.resolveApplication(job.specification.application)!!
                    val backend = app.invocation.tool.tool!!.description.backend
                    if (backend == ToolBackend.DOCKER) {
                        if (support.docker.utilization != true) {
                            throw RPCException(
                                "Not supported",
                                HttpStatusCode.BadRequest,
                                FEATURE_NOT_SUPPORTED_BY_PROVIDER
                            )
                        }
                    } else if (backend == ToolBackend.VIRTUAL_MACHINE) {
                        if (support.virtualMachine.utilization != true) {
                            throw RPCException(
                                "Not supported",
                                HttpStatusCode.BadRequest,
                                FEATURE_NOT_SUPPORTED_BY_PROVIDER
                            )
                        }
                    } else if (backend == ToolBackend.NATIVE) {
                        if (support.native.utilization != true) {
                            throw RPCException(
                                "Not supported",
                                HttpStatusCode.BadRequest,
                                FEATURE_NOT_SUPPORTED_BY_PROVIDER
                            )
                        }
                    }
                }

                override suspend fun beforeCall(
                    provider: String,
                    resource: RequestWithRefOrResource<JobsRetrieveUtilizationRequest, Job>
                ): JobsProviderUtilizationRequest {
                    return JobsProviderUtilizationRequest(resource.second.reference.category)
                }
            }
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
