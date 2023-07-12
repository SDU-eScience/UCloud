package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.accounting.api.providers.SupportByProvider
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.DBTransaction
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.utils.io.pool.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.random.Random

data class InternalJobState(
    val specification: JobSpecification,
    var state: JobState = JobState.IN_QUEUE,
    var outputFolder: String? = null,
    var startedAt: Long? = null,
    var allowRestart: Boolean = false,
    var jobParameters: JsonElement? = null,
)

object ResourceOutputPool : DefaultPool<Array<ResourceDocument<Any>>>(128) {
    override fun produceInstance(): Array<ResourceDocument<Any>> = Array(1024) { ResourceDocument() }

    override fun clearInstance(instance: Array<ResourceDocument<Any>>): Array<ResourceDocument<Any>> {
        for (doc in instance) {
            doc.data = null
            doc.createdBy = 0
            doc.createdAt = 0
            doc.project = 0
            doc.id = 0
            doc.providerId = null
            Arrays.fill(doc.update, null)
            Arrays.fill(doc.acl, null)
        }

        return instance
    }

    inline fun <T, R> withInstance(block: (Array<ResourceDocument<T>>) -> R): R {
        return useInstance {
            @Suppress("UNCHECKED_CAST")
            block(it as Array<ResourceDocument<T>>)
        }
    }
}

class JobResourceService2(
    private val db: DBContext,
    private val providers: ProviderCommunications,
    private val backgroundScope: BackgroundScope,
) {
    private val idCards = IdCardService(db)
    private val productCache = ProductCache(db)
    private val applicationCache = ApplicationCache(db)
    private val documents = ResourceStore(
        "job",
        db,
        productCache,
        idCards,
        object : ResourceStore.Callbacks<InternalJobState> {
            override suspend fun loadState(
                session: DBTransaction,
                count: Int,
                resources: LongArray
            ): Array<InternalJobState> {
                val state = arrayOfNulls<InternalJobState>(count)

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

                    val appName = row.getString(1)!!
                    val appVersion = row.getString(2)!!
                    val name = row.getString(3)
                    val replicas = row.getInt(4)!!
                    val timeAllocMillis = row.getLong(5)
                    val openedFile = row.getString(6)
                    val restartOnExit = row.getBoolean(7)
                    val sshEnabled = row.getBoolean(8)
                    val outputFolder = row.getString(9)
                    val currentState = JobState.valueOf(row.getString(10)!!)
                    val params = row.getString(11)?.let { text ->
                        defaultMapper.decodeFromString(
                            MapSerializer(String.serializer(), AppParameterValue.serializer()),
                            text
                        )
                    }
                    val decodedResources = row.getString(12)?.let { text ->
                        defaultMapper.decodeFromString(
                            ListSerializer(AppParameterValue.serializer()),
                            text
                        )
                    }
                    val startedAt = row.getLong(13)
                    val allowRestart = row.getBoolean(14) ?: false
                    val jobParameters = row.getString(15)?.let { text ->
                        defaultMapper.decodeFromString(JsonElement.serializer(), text)
                    }

                    val slot = resources.indexOf(id)
                    state[slot] = InternalJobState(
                        JobSpecification(
                            NameAndVersion(appName, appVersion),
                            ProductReference("", "", ""),
                            name,
                            replicas,
                            false,
                            params,
                            decodedResources,
                            timeAllocMillis?.let { ms -> SimpleDuration.fromMillis(ms) },
                            openedFile,
                            restartOnExit,
                            sshEnabled,
                        ),
                        currentState,
                        outputFolder,
                        startedAt,
                        allowRestart,
                        jobParameters,
                    )
                }

                @Suppress("UNCHECKED_CAST")
                return state as Array<InternalJobState>
            }

            override suspend fun saveState(
                session: DBTransaction,
                store: ResourceStoreByOwner<InternalJobState>,
                indices: IntArray,
                length: Int
            ) {
                session.sendPreparedStatement(
                    {
                        val applicationNames = ArrayList<String>().also { setParameter("application_name", it) }
                        val applicationVersions = ArrayList<String>().also { setParameter("application_version", it)}
                        val timeAllocations = ArrayList<Long?>().also { setParameter("time_allocation", it)}
                        val names = ArrayList<String?>().also { setParameter("name", it)}
                        val outputFolders = ArrayList<String?>().also { setParameter("output_folder", it)}
                        val currentStates = ArrayList<String>().also { setParameter("current_state", it)}
                        val startedAtTimestamps = ArrayList<Long?>().also { setParameter("started_at", it)}
                        val jobIds = ArrayList<Long>().also { setParameter("job_id", it)}
                        val jobParameterFiles = ArrayList<String?>().also { setParameter("job_parameters", it)}
                        val openedFiles = ArrayList<String?>().also { setParameter("opened_file", it)}

                        for (i in 0 until length) {
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
                                values.add(defaultMapper.encodeToString(
                                    AppParameterValue.serializer(),
                                    value
                                ))
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
                                resources.add(defaultMapper.encodeToString(
                                    AppParameterValue.serializer(),
                                    value
                                ))
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

                val (application, tool, block) = findSupportBlock(reqItem, support)
                block.checkEnabled()

                val supportedProviders = tool.description.supportedProviders
                if (supportedProviders  != null) {
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

            val doc = ResourceDocument<InternalJobState>()
            val allocatedId = documents.create(
                card,
                job.product,
                InternalJobState(job, JobState.IN_QUEUE),
                output = doc
            )

            val result = try {
                providers.invokeCall(
                    provider,
                    actorAndProject,
                    { JobsProvider(provider).create },
                    bulkRequestOf(unmarshallDocument(card, doc)),
                )
            } catch (ex: Throwable) {
                log.warn(ex.toReadableStacktrace().toString())
                // TODO(Dan): This is not guaranteed to run ever. We will get stuck
                //  if never "confirmed" by the provider.
                documents.delete(card, longArrayOf(allocatedId))
                throw ex
                // TODO do we continue or stop here?
            }

            val providerId = result.responses.single()
            if (providerId != null) {
                documents.updateProviderId(card, allocatedId, providerId.id)
            }

            output.add(FindByStringId(allocatedId.toString()))
        }

        return BulkResponse(output)
    }

    suspend fun browse(
        actorAndProject: ActorAndProject,
        request: ResourceBrowseRequest<JobIncludeFlags>,
    ): PageV2<Job> {
        val browseStart = System.nanoTime()
        println("calling browse")
        val card = idCards.fetchIdCard(actorAndProject)
        println("got card $card")
        val normalizedRequest = request.normalize()
        println("req $normalizedRequest")
        return ResourceOutputPool.withInstance { buffer ->
            // TODO(Dan): Sorting is an issue, especially if we are sorting using a custom property.
            println("about to browse")
            val start = System.nanoTime()
            val result = documents.browse(card, buffer, request.next, request.flags, outputBufferLimit = normalizedRequest.itemsPerPage)
            val end = System.nanoTime()
            println("Time was ${end - start}ns")
            println("Got ${result.count} results")

            val page = ArrayList<Job>(result.count)
            for (idx in 0 until min(normalizedRequest.itemsPerPage, result.count)) {
                page.add(unmarshallDocument(card, buffer[idx]))
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
        return unmarshallDocument(card, doc)
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
        val card = idCards.fetchIdCard(actorAndProject)
        val jobsToExtend = ResourceOutputPool.withInstance<InternalJobState, List<Job>> { pool ->
            if (pool.size < ids.size) {
                throw RPCException("Request is too large!", HttpStatusCode.PayloadTooLarge)
            }

            val count = documents.retrieveBulk(card, ids, pool, permission)

            if (count != ids.size) {
                throw RPCException(
                    "Could not find the jobs that you requested. Do you have permission to perform this action?",
                    HttpStatusCode.NotFound
                )
            }

            val result = ArrayList<Job>()
            for (idx in 0 until count) {
                result.add(unmarshallDocument(card, pool[idx]))
            }

            result
        }

        val grouped = jobsToExtend.groupBy { it.specification.product.provider }

        if (featureValidation != null) {
            for ((provider, jobs) in grouped) {
                val products = jobs.map { it.specification.product }.toSet()
                providers.requireSupport(Jobs, products, actionDescription) { support ->
                    for (job in jobs) {
                        if (job.specification.product != support.product) continue
                        featureValidation(job, support)
                    }
                }
            }
        }

        for ((provider, jobs) in grouped) {
            fn(provider, jobs)
        }
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

    private suspend fun unmarshallDocument(card: IdCard, doc: ResourceDocument<InternalJobState>): Job {
        val data = doc.data!!
        data.specification.product = productCache.productIdToReference(doc.product) ?: ProductReference("", "", "")
        return Job(
            doc.id.toString(),
            ResourceOwner(
                idCards.lookupUid(doc.createdBy) ?: "_ucloud",
                idCards.lookupPid(doc.project),
            ),
            doc.update.asSequence().filterNotNull().mapNotNull {
                val extra = it.extra
                val mapped = if (extra != null) {
                    defaultMapper.decodeFromJsonElement(JobUpdate.serializer(), extra)
                } else {
                    null
                }

                mapped?.status = it.update
                mapped?.timestamp = it.createdAt
                mapped
            }.toList(),
            data.specification,
            JobStatus(
                data.state,
                startedAt = data.startedAt,
                expiresAt = if (data.startedAt != null && data.specification.timeAllocation != null) {
                    (data.startedAt ?: 0L) + (data.specification.timeAllocation?.toMillis() ?: 0L)
                } else {
                    null
                },
                allowRestart = data.allowRestart,
                resolvedProduct = productCache.productIdToProduct(doc.product) as Product.Compute?,
                resolvedApplication = applicationCache.retrieveApplication(
                    data.specification.application.name,
                    data.specification.application.version
                )
            ),
            doc.createdAt,
            permissions = run {
                val myself = when (card) {
                    is IdCard.Provider -> {
                        listOf(Permission.PROVIDER, Permission.READ, Permission.EDIT)
                    }

                    is IdCard.User -> {
                        if (doc.project == 0 && card.uid == doc.createdBy) {
                            listOf(Permission.ADMIN, Permission.READ, Permission.EDIT)
                        } else if (card.adminOf.contains(doc.project)) {
                            listOf(Permission.ADMIN, Permission.READ, Permission.EDIT)
                        } else {
                            val permissions = HashSet<Permission>()
                            for (entry in doc.acl) {
                                if (entry == null) break

                                if (entry.isUser && entry.entity == card.uid) {
                                    permissions.add(entry.permission)
                                } else if (!entry.isUser && card.groups.contains(entry.entity)) {
                                    permissions.add(entry.permission)
                                }
                            }

                            permissions.toList()
                        }
                    }
                }

                val others = HashMap<AclEntity, HashSet<Permission>>()
                for (entry in doc.acl) {
                    if (entry == null) break

                    val entity = if (entry.isUser) {
                        AclEntity.User(idCards.lookupUid(entry.entity) ?: error("Invalid UID in ACL? ${entry.entity}"))
                    } else {
                        idCards.lookupGid(entry.entity) ?: error("Invalid GID in ACL? ${entry.entity}")
                    }

                    val set = others[entity] ?: HashSet<Permission>().also { others[entity] = it }
                    set.add(entry.permission)
                }

                ResourcePermissions(
                    myself,
                    others.map { ResourceAclEntry(it.key, it.value.toList()) }
                )
            },
            output = JobOutput(
                data.outputFolder
            ),
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}

class ApplicationCache(private val db: DBContext) {
    private val mutex = ReadWriterMutex()
    private val applications = HashMap<NameAndVersion, Application>()
    private val didWarmup = AtomicBoolean(false)

    suspend fun fillCache(appName: String? = null) {
        mutex.withWriter {
            db.withSession { session ->
                val rows = session.sendPreparedStatement(
                    {
                        setParameter("app_name", appName)
                    },
                    """
                        select app_store.application_to_json(app, t)
                        from
                            app_store.applications app
                            join app_store.tools t on 
                                app.tool_name = t.name 
                                and app.tool_version = t.version
                        where
                            :app_name::text is null
                            or app.name = :app_name
                    """
                ).rows
                for (row in rows) {
                    val app = defaultMapper.decodeFromString(Application.serializer(), row.getString(0)!!)
                    val key = NameAndVersion(app.metadata.name, app.metadata.version)
                    applications[key] = app
                }
            }
        }
    }

    suspend fun retrieveApplication(name: String, version: String, allowLookup: Boolean = true): Application? {
        if (didWarmup.compareAndSet(false, true)) fillCache()

        val result = mutex.withReader { applications[NameAndVersion(name, version)] }
        if (result != null) return result
        if (!allowLookup) return null

        fillCache(name)
        return retrieveApplication(name, version, allowLookup = false)
    }
}

fun createDummyJob(): Job {
    return Job(
        Random.nextLong().absoluteValue.toString(),
        ResourceOwner(
            randomString(),
            if (Random.nextBoolean()) randomString() else null
        ),
        emptyList(),
        JobSpecification(
            NameAndVersion(randomString(), randomString()),
            ProductReference(randomString(), randomString(), randomString()),
            parameters = buildMap {
                repeat(Random.nextInt(0, 5)) { idx ->
                    put(randomString(), AppParameterValue.Text(randomString()))
                }
            },
            timeAllocation = SimpleDuration(13, 37, 0)
        ),
        JobStatus(
            JobState.RUNNING,
            startedAt = Random.nextLong().absoluteValue,
            resolvedApplication = Application(
                ApplicationMetadata(
                    randomString(),
                    randomString(),
                    listOf(randomString(), randomString()),
                    randomString(),
                    randomString(),
                    randomString(),
                    true
                ),
                ApplicationInvocationDescription(
                    ToolReference(
                        randomString(),
                        randomString(),
                        Tool(
                            randomString(),
                            Random.nextLong().absoluteValue,
                            Random.nextLong().absoluteValue,
                            NormalizedToolDescription(
                                NameAndVersion(randomString(), randomString()),
                                randomString(),
                                1,
                                SimpleDuration(13, 37, 0),
                                emptyList(),
                                listOf(randomString()),
                                randomString(),
                                randomString(),
                                ToolBackend.DOCKER,
                                randomString(),
                                randomString(),
                                null
                            )
                        )
                    ),
                    listOf(WordInvocationParameter(randomString())),
                    listOf(),
                    listOf()
                )
            ),
        ),
        Random.nextLong().absoluteValue,
    )
}

fun randomString(minSize: Int = 8, maxSize: Int = 16): String {
    return CharArray(Random.nextInt(minSize, maxSize + 1)) { Char(Random.nextInt(48, 91)) }.concatToString()
}
