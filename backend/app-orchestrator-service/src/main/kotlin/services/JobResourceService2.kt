package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.WSCall
import dk.sdu.cloud.calls.server.sendWSMessage
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.actorAndProject
import dk.sdu.cloud.service.db.async.*
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.*
import java.util.concurrent.CancellationException
import kotlin.math.min

data class InternalJobState(
    val specification: JobSpecification,
    var state: JobState = JobState.IN_QUEUE,
    var outputFolder: String? = null,
    var startedAt: Long? = null,
    var allowRestart: Boolean = false,
    var jobParameters: JsonElement? = null,
)

class JobResourceService2(
    private val db: AsyncDBSessionFactory,
    private val providers: ProviderCommunications,
    private val backgroundScope: BackgroundScope,
    private val productCache: ProductCache,
) {
    private val idCards = IdCardService(db)
    private val applicationCache = ApplicationCache(db)
    private val documents = ResourceStore(
        "job",
        db,
        productCache,
        idCards,
        object : ResourceStore.Callbacks<InternalJobState> {
            override suspend fun loadState(
                transaction: Any,
                count: Int,
                resources: LongArray
            ): Array<InternalJobState> {
                val state = arrayOfNulls<InternalJobState>(count)
                val session = transaction as AsyncDBConnection

                session.sendPreparedStatement(
                    { setParameter("ids", resources.slice(0 until count)) },
                    """
                        with
                            params as (
                                select param.job_id, jsonb_object_agg(param.name, param.value) as params
                                from app_orchestrator.job_input_parameters param
                                where param.job_id = some(:ids)
                                group by job_id
                            ),
                            resources as (
                                select r.job_id, jsonb_agg(r.resource) as resources
                                from app_orchestrator.job_resources r
                                where r.job_id = some(:ids)
                                group by job_id
                            )
                        select
                            j.resource,
                            j.application_name,
                            j.application_version,
                            j.name,
                            j.replicas,
                            j.time_allocation_millis,
                            j.opened_file,
                            j.restart_on_exit,
                            j.ssh_enabled,
                            j.output_folder,
                            j.current_state,
                            p.params,
                            r.resources,
                            floor(extract(epoch from j.started_at) * 1000)::int8,
                            j.allow_restart,
                            j.job_parameters
                        from
                            app_orchestrator.jobs j
                            left join params p on j.resource = p.job_id
                            left join resources r on j.resource = r.job_id
                        where
                            j.resource = some(:ids);
                    """
                ).rows.forEach { row ->
                    val id = row.getLong(0)!!
                    val slot = resources.indexOf(id)

                    state[slot] = InternalJobState(
                        JobSpecification(
                            product = ProductReference("", "", ""), // Filled out by doc store
                            application = NameAndVersion(
                                name = row.getString(1)!!,
                                version = row.getString(2)!!,
                            ),
                            name = row.getString(3),
                            replicas = row.getInt(4)!!,
                            allowDuplicateJob = false,
                            parameters = row.getString(11)?.let { text ->
                                defaultMapper.decodeFromString(
                                    MapSerializer(String.serializer(), AppParameterValue.serializer()),
                                    text
                                )
                            } ?: emptyMap(),
                            resources = row.getString(12)?.let { text ->
                                defaultMapper.decodeFromString(
                                    ListSerializer(AppParameterValue.serializer()),
                                    text
                                )
                            } ?: emptyList(),
                            timeAllocation = row.getLong(5)?.let { SimpleDuration.fromMillis(it) },
                            openedFile = row.getString(6),
                            restartOnExit = row.getBoolean(7),
                            sshEnabled = row.getBoolean(8),
                        ),
                        state = JobState.valueOf(row.getString(10)!!),
                        outputFolder = row.getString(9),
                        startedAt = row.getLong(13),
                        allowRestart = row.getBoolean(14) ?: false,
                        jobParameters = row.getString(15)?.let { text ->
                            defaultMapper.decodeFromString(JsonElement.serializer(), text)
                        },
                    )
                }


                @Suppress("UNCHECKED_CAST")
                return state as Array<InternalJobState>
            }

            override suspend fun saveState(
                transaction: Any,
                store: ResourceStoreBucket<InternalJobState>,
                indices: IntArray,
                length: Int
            ) {
                val session = transaction as AsyncDBConnection
                session.sendPreparedStatement(
                    {
                        val applicationNames = ArrayList<String>().also { setParameter("application_name", it) }
                        val applicationVersions =
                            ArrayList<String>().also { setParameter("application_version", it) }
                        val timeAllocations = ArrayList<Long?>().also { setParameter("time_allocation", it) }
                        val names = ArrayList<String?>().also { setParameter("name", it) }
                        val outputFolders = ArrayList<String?>().also { setParameter("output_folder", it) }
                        val currentStates = ArrayList<String>().also { setParameter("current_state", it) }
                        val startedAtTimestamps = ArrayList<Long?>().also { setParameter("started_at", it) }
                        val jobIds = ArrayList<Long>().also { setParameter("job_id", it) }
                        val jobParameterFiles = ArrayList<String?>().also { setParameter("job_parameters", it) }
                        val openedFiles = ArrayList<String?>().also { setParameter("opened_file", it) }

                        for (i in 0 until length) {
                            if (store.flaggedForDelete[i]) TODO()
                            val arrIdx = indices[i]
                            val jobId = store.id[arrIdx]
                            val job = store.data(arrIdx) ?: continue

                            applicationNames.add(job.specification.application.name)
                            applicationVersions.add(job.specification.application.version)
                            timeAllocations.add(job.specification.timeAllocation?.toMillis())
                            names.add(job.specification.name)
                            outputFolders.add(job.outputFolder)
                            currentStates.add(job.state.name)
                            startedAtTimestamps.add(job.startedAt)
                            jobIds.add(jobId)
                            jobParameterFiles.add(job.jobParameters?.let {
                                defaultMapper.encodeToString(JsonElement.serializer(), it)
                            })
                            openedFiles.add(job.specification.openedFile)
                        }
                    },
                    """
                        with
                            data as (
                                select
                                    unnest(:application_name::text[]) application_name,
                                    unnest(:application_version::text[]) application_version,
                                    unnest(:time_allocation::int8[]) time_allocation,
                                    unnest(:name::text[]) name,
                                    unnest(:output_folder::text[]) output_folder,
                                    unnest(:current_state::text[]) current_state,
                                    to_timestamp(unnest(:started_at::int8[]) / 1000) started_at,
                                    unnest(:job_id::int8[]) job_id,
                                    unnest(:job_parameters::jsonb[]) job_parameters,
                                    unnest(:opened_file::text[][]) opened_file
                            )
                        insert into app_orchestrator.jobs 
                            (application_name, application_version, time_allocation_millis, name, output_folder, 
                            current_state, started_at, resource, job_parameters, opened_file) 
                        select 
                            data.application_name, application_version, time_allocation, name, output_folder, 
                            current_state, started_at, job_id, job_parameters, opened_file
                        from data
                        on conflict (resource) do update set 
                            application_name = excluded.application_name,
                            application_version = excluded.application_version,
                            time_allocation_millis = excluded.time_allocation_millis,
                            name = excluded.name,
                            output_folder = excluded.output_folder,
                            current_state = excluded.current_state,
                            started_at = excluded.started_at,
                            job_parameters = excluded.job_parameters,
                            opened_file = excluded.opened_file
                    """
                )

                session.sendPreparedStatement(
                    {
                        val names = ArrayList<String>().also { setParameter("name", it) }
                        val values = ArrayList<String>().also { setParameter("value", it) }
                        val jobIds = ArrayList<Long>().also { setParameter("job_id", it) }

                        for (i in 0 until length) {
                            val arrIdx = indices[i]
                            val jobId = store.id[arrIdx]
                            val job = store.data(arrIdx) ?: continue

                            for ((name, value) in job.specification.parameters ?: emptyMap()) {
                                jobIds.add(jobId)
                                names.add(name)
                                values.add(
                                    defaultMapper.encodeToString(
                                        AppParameterValue.serializer(),
                                        value
                                    )
                                )
                            }
                        }
                    },
                    """
                        with
                            data as (
                                select
                                    unnest(:job_id::int8[]) job_id,
                                    unnest(:name::text[]) name,
                                    unnest(:value::jsonb[]) value
                            ),
                            deleted_entries as (
                                delete from app_orchestrator.job_input_parameters p
                                using data d
                                where p.job_id = d.job_id
                            )
                        insert into app_orchestrator.job_input_parameters (name, value, job_id) 
                        select name, value, job_id
                        from data
                    """
                )

                session.sendPreparedStatement(
                    {
                        val jobIds = ArrayList<Long>().also { setParameter("job_id", it) }
                        val resources = ArrayList<String>().also { setParameter("resource", it) }

                        for (i in 0 until length) {
                            val arrIdx = indices[i]
                            val jobId = store.id[arrIdx]
                            val job = store.data(arrIdx) ?: continue

                            for (value in job.specification.resources ?: emptyList()) {
                                jobIds.add(jobId)
                                resources.add(
                                    defaultMapper.encodeToString(
                                        AppParameterValue.serializer(),
                                        value
                                    )
                                )
                            }
                        }
                    },
                    """
                        with
                            data as (
                                select
                                    unnest(:job_id::int8[]) job_id,
                                    unnest(:resource::jsonb[]) resource
                            ),
                            deleted_entries as (
                                delete from app_orchestrator.job_resources r
                                using data d
                                where r.job_id = d.job_id
                            )
                        insert into app_orchestrator.job_resources (resource, job_id) 
                        select d.resource, d.job_id
                        from data d
                    """
                )
            }
        }
    )

    private val docMapper = DocMapper<InternalJobState, Job>(idCards, productCache, providers) {
        Job(
            id,
            owner,
            updates.map { update ->
                val decoded = update.extra?.let {
                    defaultMapper.decodeFromJsonElement<JobUpdate>(it)
                } ?: JobUpdate()

                decoded.also {
                    it.timestamp = update.timestamp
                    it.status = update.status
                }
            },
            data.specification.copy(
                product = ProductReference(
                    resolvedProduct.name,
                    resolvedProduct.category.name,
                    resolvedProduct.category.provider,
                )
            ),
            JobStatus(
                data.state,
                null,
                data.startedAt,
                if (data.startedAt != null && data.specification.timeAllocation != null) {
                    (data.startedAt ?: 0L) + (data.specification.timeAllocation?.toMillis() ?: 0L)
                } else {
                    null
                },
                data.specification.application.let { (name, version) ->
                    applicationCache.retrieveApplication(name, version)
                },
                resolvedSupport as ResolvedSupport<Product.Compute, ComputeSupport>,
                resolvedProduct as Product.Compute,
                data.allowRestart,
            ),
            createdAt,
            JobOutput(
                outputFolder = data.outputFolder,
            ),
            permissions,
        )
    }

    private val proxy = ProxyToProvider(idCards, documents, docMapper, productCache, providers)
    // If we had singleton access to the database and serviceClients and if the docMapper becomes redundant:
    // private val proxy = ProxyToProvider(documents)


    init {
        documents.startSynchronizationJob(backgroundScope)
    }

    suspend fun create(
        actorAndProject: ActorAndProject,
        request: BulkRequest<JobSpecification>,
    ): BulkResponse<FindByStringId?> {
        providers.requireSupportAndCheckAllocations(actorAndProject, Jobs, request, "trying to start job") { support ->
            for (reqItem in request.items) {
                if (reqItem.product != support.product) continue

                val (_, tool, block) = findSupportBlock(reqItem, support)
                block.checkEnabled()

                val supportedProviders = tool.description.supportedProviders
                if (supportedProviders != null) {
                    if (reqItem.product.provider !in supportedProviders) {
                        error("The application is not supported by this provider. Try selecting a different application.")
                    }
                }
            }
        }

        val output = ArrayList<FindByStringId>()

        val card = idCards.fetchIdCard(actorAndProject)
        for (job in request.items) {
            val provider = job.product.provider
            validate(actorAndProject, job)

            output.add(
                FindByStringId(
                    documents.createViaProvider(
                        card,
                        job.product,
                        InternalJobState(job),
                        proxyBlock = { doc ->
                            providers.invokeCall(
                                provider,
                                actorAndProject,
                                { JobsProvider(provider).create },
                                bulkRequestOf(docMapper.map(null, doc))
                            ).responses.singleOrNull()?.id
                        }
                    ).toString()
                )
            )
        }

        return BulkResponse(output)
    }

    suspend fun register(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ProviderRegisteredResource<JobSpecification>>,
    ): BulkResponse<FindByStringId> {
        val ids = ArrayList<FindByStringId>()
        val card = idCards.fetchIdCard(actorAndProject)
        for (reqItem in request.items) {
            if (reqItem.providerGeneratedId?.contains(",") == true) {
                throw RPCException("Provider generated ID cannot contain ','", HttpStatusCode.BadRequest)
            }

            val uid = idCards.lookupUidFromUsername(reqItem.createdBy ?: "_ucloud")
                ?: throw RPCException("Unknown user supplied", HttpStatusCode.NotFound)

            val project = reqItem.project
            val pid = if (project != null) {
                idCards.lookupPidFromProjectId(project)
                    ?: throw RPCException("Unknown project supplied", HttpStatusCode.NotFound)
            } else {
                0
            }

            documents.register(
                card,
                reqItem.spec.product,
                uid,
                pid,
                InternalJobState(reqItem.spec),
                reqItem.providerGeneratedId,
                null,
            )

            if (reqItem.projectAllRead) {
                TODO()
            }

            if (reqItem.projectAllWrite) {
                TODO()
            }
        }
        return BulkResponse(ids)
    }

    suspend fun browse(
        actorAndProject: ActorAndProject,
        request: ResourceBrowseRequest<JobIncludeFlags>,
    ): PageV2<Job> {
        val browseStart = System.nanoTime()
        val card = idCards.fetchIdCard(actorAndProject)
        val normalizedRequest = request.normalize()
        println("req $normalizedRequest")
        val customFilter = if (request.flags.filterApplication != null || request.flags.filterState != null) {
            object : FilterFunction<InternalJobState> {
                override fun filter(doc: ResourceDocument<InternalJobState>): Boolean {
                    val data = doc.data!!
                    var success = true
                    val flags = request.flags

                    flags.filterApplication?.let { success = success && data.specification.application.name == it }
                    flags.filterState?.let { success = success && data.state == it }
                    return success
                }
            }
        } else {
            null
        }

        return ResourceOutputPool.withInstance { buffer ->
            println("about to browse")
            val start = System.nanoTime()
            val result = when (request.sortBy) {
                "name" -> {
                    documents.browseWithSort(
                        card,
                        buffer,
                        request.next,
                        request.flags,
                        outputBufferLimit = normalizedRequest.itemsPerPage,
                        additionalFilters = customFilter,
                        keyExtractor = object : DocKeyExtractor<InternalJobState, String?> {
                            override fun extract(doc: ResourceDocument<InternalJobState>): String? {
                                return doc.data?.specification?.name
                            }
                        },
                        comparator = Comparator { a, b ->
                            when {
                                a == null && b == null -> 0
                                a == null -> 1
                                b == null -> -1
                                else -> a.compareTo(b)
                            }
                        },
                        sortDirection = request.sortDirection
                    )
                }

                else -> {
                    documents.browse(
                        card,
                        buffer,
                        request.next,
                        request.flags,
                        outputBufferLimit = normalizedRequest.itemsPerPage,
                        additionalFilters = customFilter,
                        sortedBy = request.sortBy,
                        sortDirection = request.sortDirection
                    )
                }
            }

            val end = System.nanoTime()
            println("Time was ${end - start}ns")
            println("Got ${result.count} results")

            val page = ArrayList<Job>(result.count)
            for (idx in 0 until min(normalizedRequest.itemsPerPage, result.count)) {
                page.add(docMapper.map(card, buffer[idx]))
            }
            val unmarshallEnd = System.nanoTime()
            println("Conversion to output format took: ${unmarshallEnd - end}ns")

            return PageV2(normalizedRequest.itemsPerPage, page, result.next).also {
                println("Browse took: ${System.nanoTime() - browseStart}")
            }
        }
    }

    suspend fun retrieve(
        actorAndProject: ActorAndProject,
        request: ResourceRetrieveRequest<JobIncludeFlags>,
    ): Job? {
        val longId = request.id.toLongOrNull() ?: return null
        val card = idCards.fetchIdCard(actorAndProject)
        val doc = documents.retrieve(card, longId) ?: return null
        return docMapper.map(card, doc)
    }

    suspend fun addUpdate(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ResourceUpdateAndId<JobUpdate>>
    ) {
        // TODO(Dan): Allocates a lot of memory which isn't needed
        val card = idCards.fetchIdCard(actorAndProject)
        val updatesByJob = request.items.groupBy { it.id }.mapValues { it.value.map { it.update } }

        for ((jobId, updates) in updatesByJob) {
            documents.addUpdate(
                card,
                jobId.toLongOrNull() ?: continue,
                updates.map {
                    ResourceDocumentUpdate(
                        it.status,
                        defaultMapper.encodeToJsonElement(JobUpdate.serializer(), it)
                    )
                },
                consumer = f@{ job, uIdx, _ ->
                    val update = updates[uIdx]
                    val newState = update.state
                    val expectedState = update.expectedState
                    val expectedDifferentState = update.expectedDifferentState
                    val timeAllocation = update.newTimeAllocation
                    val allowRestart = update.allowRestart
                    val outputFolder = update.outputFolder
                    val newMounts = update.newMounts

                    if (expectedState != null && job.state != expectedState) {
                        return@f false
                    } else if (expectedDifferentState == true && newState != null && job.state == newState) {
                        return@f false
                    }

                    if (newState != null) {
                        job.state = newState

                        if (newState == JobState.RUNNING) {
                            job.startedAt = Time.now()
                        }
                    }

                    if (timeAllocation != null) {
                        job.specification.timeAllocation = SimpleDuration.fromMillis(timeAllocation)
                    }

                    if (allowRestart != null) {
                        job.allowRestart = allowRestart
                    }

                    if (outputFolder != null) {
                        job.outputFolder = outputFolder
                    }

                    if (newMounts != null) {
                        TODO()
                    }

                    return@f true
                }
            )
        }
    }

    suspend fun updateAcl(
        actorAndProject: ActorAndProject,
        request: BulkRequest<UpdatedAcl>
    ) {
        val card = idCards.fetchIdCard(actorAndProject)
        for (reqItem in request.items) {
            documents.updateAcl(
                card,
                reqItem.id.toLongOrNull() ?: throw RPCException("Invalid ID supplied", HttpStatusCode.NotFound),
                reqItem.deleted.map { NumericAclEntry.fromAclEntity(idCards, it) },
                reqItem.added.flatMap { NumericAclEntry.fromAclEntry(idCards, it) }
            )
        }

        // TODO Do we really need to contact the provider about this?
    }

    suspend fun terminate(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>
    ) {
        proxyToProvider(
            actorAndProject,
            request.items.mapNotNull { it.id.toLongOrNull() }.toSet().toLongArray(),
            Permission.EDIT,
            "trying to terminate job",
            featureValidation = null,
            fn = { provider, jobs ->
                providers.invokeCall(
                    provider,
                    actorAndProject,
                    { JobsProvider(provider).terminate },
                    BulkRequest(jobs),
                )

                Unit
            }
        )
    }

    suspend fun extend(
        actorAndProject: ActorAndProject,
        request: BulkRequest<JobsExtendRequestItem>,
    ) {
        proxyToProvider(
            actorAndProject,
            request.items.mapNotNull { it.jobId.toLongOrNull() }.toSet().toLongArray(),
            Permission.EDIT,
            "trying to extend duration",
            featureValidation = { job, support ->
                val block = findSupportBlock(job.specification, support).block
                block.checkFeature(block.timeExtension)
            },
            fn = { provider, jobs ->
                val providerRequest = jobs.map { job ->
                    JobsProviderExtendRequestItem(
                        job,
                        request.items.findLast { it.jobId == job.id }!!.requestedTime,
                    )
                }

                providers.invokeCall(
                    provider,
                    actorAndProject,
                    { JobsProvider(provider).extend },
                    BulkRequest(providerRequest),
                )

                Unit
            }
        )
    }

    suspend fun suspendOrUnsuspendJob(
        actorAndProject: ActorAndProject,
        request: BulkRequest<JobsSuspendRequestItem>,
        shouldSuspend: Boolean,
    ) {
        proxyToProvider(
            actorAndProject,
            request.items.mapNotNull { it.id.toLongOrNull() }.toSet().toLongArray(),
            Permission.EDIT,
            if (shouldSuspend) "trying to suspend job" else "trying to unsuspend job",
            featureValidation = { job, support ->
                val block = findSupportBlock(job.specification, support).block
                if (block !is ComputeSupport.VirtualMachine) error("Feature not supported")
                block.checkFeature(block.suspension)
            },
            fn = { provider, jobs ->
                val providerRequest = jobs.map { job ->
                    JobsProviderSuspendRequestItem(job)
                }

                providers.invokeCall(
                    provider,
                    actorAndProject,
                    {
                        if (shouldSuspend) JobsProvider(provider).suspend
                        else JobsProvider(provider).unsuspend
                    },
                    BulkRequest(providerRequest),
                )

                Unit
            }
        )
    }

    suspend fun openInteractiveSession(
        actorAndProject: ActorAndProject,
        request: BulkRequest<JobsOpenInteractiveSessionRequestItem>,
    ): BulkResponse<OpenSessionWithProvider?> {
        val responses = ArrayList<OpenSessionWithProvider>()
        proxyToProvider(
            actorAndProject,
            request.items.mapNotNull { it.id.toLongOrNull() }.toLongArray(),
            Permission.EDIT,
            "trying to open interface",
            featureValidation = { job, support ->
                val (app, tool, block) = findSupportBlock(job.specification, support)
                for (reqItem in request.items) {
                    if (reqItem.id != job.id) continue
                    when (reqItem.sessionType) {
                        InteractiveSessionType.WEB -> {
                            require(app.invocation.web != null)
                            require(block is ComputeSupport.WithWeb)
                            block.checkFeature(block.web)
                        }

                        InteractiveSessionType.VNC -> {
                            require(app.invocation.vnc != null)
                            block.checkFeature(block.vnc)
                        }

                        InteractiveSessionType.SHELL -> {
                            block.checkFeature(block.terminal)
                        }
                    }
                }
            },
            fn = { provider, jobs ->
                val providerRequest = request.items
                    .asSequence()
                    .filter { req -> jobs.any { it.id == req.id } }
                    .map { req ->
                        val job = jobs.find { it.id == req.id }!!
                        JobsProviderOpenInteractiveSessionRequestItem(
                            job,
                            req.rank,
                            req.sessionType
                        )
                    }
                    .toList()

                val providerDomain = providers.retrieveProviderHostInfo(provider).toString()
                responses.addAll(
                    providers
                        .invokeCall(
                            provider,
                            actorAndProject,
                            { JobsProvider(it).openInteractiveSession },
                            BulkRequest(providerRequest),
                        )
                        .responses
                        .asSequence()
                        .filterNotNull()
                        .map { OpenSessionWithProvider(providerDomain, provider, it) }
                )
            }
        )

        return BulkResponse(responses)
    }

    suspend fun follow(
        callHandler: CallHandler<JobsFollowRequest, JobsFollowResponse, *>,
    ): Unit = with(callHandler) {
        val card = idCards.fetchIdCard(callHandler.actorAndProject)

        val jobId =
            request.id.toLongOrNull() ?: throw RPCException("Unknown job: ${request.id}", HttpStatusCode.NotFound)
        val initialJob = documents.retrieve(card, jobId)?.let { docMapper.map(card, it) }
            ?: throw RPCException("Unknown job: ${request.id}", HttpStatusCode.NotFound)
        val logsSupported = runCatching {
            providers.requireSupport(
                Jobs,
                listOf(initialJob.specification.product),
                "trying to follow logs",
                validator = { support ->
                    val block = findSupportBlock(initialJob.specification, support).block
                    block.checkFeature(block.logs)
                }
            )
        }.isSuccess

        withContext<WSCall> {
            // NOTE(Dan): We do _not_ send the initial list of updates, instead we assume that clients will
            // retrieve them by themselves.
            sendWSMessage(JobsFollowResponse(emptyList(), emptyList(), initialJob.status))

            var lastUpdate = initialJob.updates.maxByOrNull { it.timestamp }?.timestamp ?: 0L
            var currentState = initialJob.status.state
            var streamId: String? = null
            val provider = initialJob.specification.product.provider

            coroutineScope {
                val logJob = if (!logsSupported) {
                    null
                } else {
                    launch {
                        while (isActive) {
                            try {
                                providers.invokeSubscription(
                                    provider,
                                    callHandler.actorAndProject,
                                    { JobsProvider(it).follow },
                                    JobsProviderFollowRequest.Init(initialJob),
                                    handler = { message: JobsProviderFollowResponse ->
                                        if (streamId == null) streamId = message.streamId

                                        sendWSMessage(
                                            JobsFollowResponse(
                                                emptyList(),
                                                listOf(JobsLog(message.rank, message.stdout, message.stderr))
                                            )
                                        )
                                    }
                                )
                            } catch (ignore: CancellationException) {
                                break
                            } catch (ex: Throwable) {
                                log.debug("Caught exception while following logs:\n${ex.stackTraceToString()}")
                                break
                            }
                        }
                    }
                }

                val updateJob = launch {
                    try {
                        var lastStatus: JobStatus? = null
                        while (isActive && !currentState.isFinal()) {
                            val newJob = documents.retrieve(card, jobId) ?: break
                            val data = newJob.data ?: break

                            currentState = data.state

                            val updates = newJob.update
                                .asSequence()
                                .filterNotNull()
                                .filter { it.createdAt > lastUpdate }
                                .mapNotNull { update ->
                                    update.extra?.let {
                                        defaultMapper.decodeFromJsonElement(JobUpdate.serializer(), it)
                                    }?.also {
                                        it.timestamp = update.createdAt
                                        it.status = update.update
                                    }
                                }
                                .toList()

                            if (updates.isNotEmpty()) {
                                sendWSMessage(
                                    JobsFollowResponse(
                                        updates,
                                        emptyList(),
                                        null
                                    )
                                )
                                lastUpdate = updates.maxByOrNull { it.timestamp }?.timestamp ?: 0L
                            }

                            val newStatus = docMapper.map(null, newJob).status
                            if (lastStatus != newStatus) {
                                sendWSMessage(JobsFollowResponse(emptyList(), emptyList(), newStatus))
                            }

                            lastStatus = newStatus
                            delay(1000)
                        }
                    } catch (ex: Throwable) {
                        if (ex !is CancellationException) {
                            log.warn(ex.stackTraceToString())
                        }
                    }
                }

                select {
                    if (logJob != null) logJob.onJoin {}
                    updateJob.onJoin {}
                }

                val capturedId = streamId
                if (capturedId != null) {
                    providers.invokeCall(
                        provider,
                        actorAndProject,
                        { JobsProvider(it).follow },
                        JobsProviderFollowRequest.CancelStream(capturedId),
                        useHttpClient = false,
                    )
                }

                runCatching { logJob?.cancel("No longer following or EOF") }
                runCatching { updateJob.cancel("No longer following or EOF") }
            }
        }
    }

    suspend fun retrieveUtilization(
        actorAndProject: ActorAndProject,
        request: JobsRetrieveUtilizationRequest
    ): JobsRetrieveUtilizationResponse {
        val jobId = request.jobId.toLongOrNull() ?: throw RPCException("Unknown job", HttpStatusCode.NotFound)
        var response: JobsRetrieveUtilizationResponse? = null
        proxyToProvider(
            actorAndProject,
            longArrayOf(jobId),
            Permission.READ,
            "trying to retrieve cluster utilization",
            featureValidation = { job, support ->
                findSupportBlock(job.specification, support).block.apply {
                    checkFeature(utilization)
                }
            },
            fn = { provider, jobs ->
                response = providers.invokeCall(
                    provider,
                    actorAndProject,
                    { JobsProvider(it).retrieveUtilization },
                    JobsProviderUtilizationRequest(
                        jobs.single().specification.product.category,
                    ),
                )
            }
        )
        return response ?: throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
    }

    suspend fun search(
        actorAndProject: ActorAndProject,
        request: ResourceSearchRequest<JobIncludeFlags>
    ): PageV2<Job> {
        val card = idCards.fetchIdCard(actorAndProject)
        val normalizedRequest = request.normalize()
        ResourceOutputPool.withInstance { buffer ->
            val result = documents.browse(
                card,
                buffer,
                request.next,
                request.flags,
                outputBufferLimit = normalizedRequest.itemsPerPage,
                additionalFilters = object : FilterFunction<InternalJobState> {
                    override fun filter(doc: ResourceDocument<InternalJobState>): Boolean {
                        if (request.query == "") return true
                        val data = doc.data!!

                        val jobName = data.specification.name
                        val appName = data.specification.application.name
                        if (jobName != null && request.query.contains(jobName, ignoreCase = true)) return true
                        if (request.query.contains(appName, ignoreCase = true)) return true
                        if (request.query.contains(doc.id.toString())) return true
                        return false
                    }
                }
            )

            val page = ArrayList<Job>(result.count)
            for (idx in 0 until min(normalizedRequest.itemsPerPage, result.count)) {
                page.add(docMapper.map(card, buffer[idx]))
            }

            return PageV2(normalizedRequest.itemsPerPage, page, result.next)
        }
    }

    suspend fun initializeProviders(actorAndProject: ActorAndProject) {
        providers.forEachRelevantProvider(actorAndProject) { provider ->
            providers.invokeCall(
                provider,
                actorAndProject,
                { JobsProvider(it).init },
                ResourceInitializationRequest(
                    ResourceOwner(
                        actorAndProject.actor.safeUsername(),
                        actorAndProject.project
                    )
                )
            )
        }
    }

    private suspend fun validate(actorAndProject: ActorAndProject, job: JobSpecification) {
        // TODO do something
    }

    private suspend fun proxyToProvider(
        actorAndProject: ActorAndProject,
        ids: LongArray,
        permission: Permission,
        actionDescription: String,
        featureValidation: (suspend (job: Job, support: ComputeSupport) -> Unit)?,
        fn: suspend (providerId: String, jobs: List<Job>) -> Unit
    ) {
        proxy.send(
            actorAndProject,
            ids.toList(),
            permission,
            actionDescription,
            featureValidation = { job, support -> featureValidation?.invoke(job, support as ComputeSupport) },
            fn
        )
    }

    private data class SupportInfo(
        val application: Application,
        val tool: Tool,
        val block: ComputeSupport.UniversalBackendSupport,
    )

    private suspend fun findSupportBlock(
        spec: JobSpecification,
        support: ComputeSupport
    ): SupportInfo {
        val (appName, appVersion) = spec.application
        val application = applicationCache.retrieveApplication(appName, appVersion)
            ?: error("Unknown application")

        val tool = application.invocation.tool.tool ?: error("No tool")

        val block = when (tool.description.backend) {
            ToolBackend.SINGULARITY -> error("unsupported tool backend")
            ToolBackend.DOCKER -> support.docker
            ToolBackend.VIRTUAL_MACHINE -> support.virtualMachine
            ToolBackend.NATIVE -> support.native
        }

        return SupportInfo(application, tool, block)
    }

    suspend fun retrieveProducts(
        actorAndProject: ActorAndProject,
    ): SupportByProvider<Product.Compute, ComputeSupport> {
        return providers.retrieveProducts(actorAndProject, Jobs)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
