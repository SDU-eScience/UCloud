package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.accounting.util.ResourceDocument
import dk.sdu.cloud.accounting.util.invokeCall
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.orchestrator.api.JobIncludeFlags
import dk.sdu.cloud.app.orchestrator.api.JobSpecification
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.JobStatus
import dk.sdu.cloud.app.orchestrator.api.JobUpdate
import dk.sdu.cloud.app.orchestrator.api.JobsProvider
import dk.sdu.cloud.app.store.api.AppParameterValue
import dk.sdu.cloud.app.store.api.Application
import dk.sdu.cloud.app.store.api.ApplicationInvocationDescription
import dk.sdu.cloud.app.store.api.ApplicationMetadata
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.NormalizedToolDescription
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.app.store.api.Tool
import dk.sdu.cloud.app.store.api.ToolBackend
import dk.sdu.cloud.app.store.api.ToolReference
import dk.sdu.cloud.app.store.api.WordInvocationParameter
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.provider.api.ResourcePermissions
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.random.Random

data class InternalJobState(
    val specification: JobSpecification,
    val state: JobState,
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
    private val _providers: Providers<*>?,
) {
    private val idCards = IdCardService(db)
    private val documents = ResourceStore(
        "job",
        db,
        ResourceStore.Callbacks(
            loadState = { session, count, ids ->
                val state = arrayOfNulls<InternalJobState>(count)

                session.sendPreparedStatement(
                    { setParameter("ids", ids.slice(0 until count)) },
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
                            r.resources
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
                    val resources = row.getString(12)?.let { text ->
                        defaultMapper.decodeFromString(
                            ListSerializer(AppParameterValue.serializer()),
                            text
                        )
                    }

                    val slot = ids.indexOf(id)
                    state[slot] = InternalJobState(
                        JobSpecification(
                            NameAndVersion(appName, appVersion),
                            ProductReference("", "", ""),
                            name,
                            replicas,
                            false,
                            params,
                            resources,
                            timeAllocMillis?.let { ms -> SimpleDuration.fromMillis(ms) },
                            openedFile,
                            restartOnExit,
                            sshEnabled,
                        ),
                        currentState
                    )
                }

                @Suppress("UNCHECKED_CAST")
                state as Array<InternalJobState>
            }
        )
    )

    val providers get() = _providers!!
    suspend fun create(
        actorAndProject: ActorAndProject,
        request: BulkRequest<JobSpecification>,
    ): BulkResponse<FindByStringId?> {
        val output = ArrayList<FindByStringId>()

        val card = idCards.fetchIdCard(actorAndProject)
        for (job in request.items) {
            val provider = job.product.provider
            validate(actorAndProject, job)

            val doc = ResourceDocument<InternalJobState>()
            val allocatedId = documents.create(
                card,
                findProduct(job.product),
                InternalJobState(job, JobState.IN_QUEUE),
                output = doc
            )

            // TODO(Dan): We need to store the internal state in the database here?

            val result = try {
                providers.invokeCall(
                    provider,
                    actorAndProject,
                    { JobsProvider(provider).create },
                    bulkRequestOf(unmarshallDocument(doc)),
                    actorAndProject.signedIntentFromUser,
                )
            } catch (ex: Throwable) {
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
        println("calling browse")
        val card = idCards.fetchIdCard(actorAndProject)
        println("got card $card")
        val normalizedRequest = request.normalize()
        println("req $normalizedRequest")
        return ResourceOutputPool.withInstance { buffer ->
            // TODO(Dan): Sorting is an issue, especially if we are sorting using a custom property.
            println("about to browse")
            val start = System.nanoTime()
            val result = documents.browse(card, buffer, request.next, request.flags)
            val end = System.nanoTime()
            println("Time was ${end - start}ns")

            val page = ArrayList<Job>(result.count)
            for (idx in 0 until min(normalizedRequest.itemsPerPage, result.count)) {
                page.add(unmarshallDocument(buffer[idx]))
            }

            return PageV2(normalizedRequest.itemsPerPage, page, result.next)
        }
    }

    suspend fun retrieve(
        actorAndProject: ActorAndProject,
        request: ResourceRetrieveRequest<JobIncludeFlags>,
    ): Job? {
        val longId = request.id.toLongOrNull() ?: return null
        val card = idCards.fetchIdCard(actorAndProject)
        val doc = documents.retrieve(card, longId) ?: return null
        return unmarshallDocument(doc)
    }

    suspend fun terminate(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>
    ) {
        val card = idCards.fetchIdCard(actorAndProject)
        val allJobs = ResourceOutputPool.withInstance<InternalJobState, List<Job>> { buffer ->

            // TODO(Dan): Wasteful memory allocation and copies
            val count = documents.retrieveBulk(
                card,
                request.items.mapNotNull { it.id.toLongOrNull() }.toLongArray(),
                buffer
            )

            // TODO(Dan): We could skip this step entirely and move directly to the bytes needed to send the request.
            //  No system is going to care about a BulkRequest<Job> when we already have the internal data to inspect.
            //  This step could also do the grouping step below.
            val result = ArrayList<Job>()
            for (idx in 0 until count) {
                result.add(unmarshallDocument(buffer[0]))
            }

            result
        }

        // TODO(Dan): See earlier todo about going from internal to bytes
        val jobsByProvider = allJobs.groupBy { it.specification.product.provider }
        for ((provider, jobs) in jobsByProvider) {
            providers.invokeCall(
                provider,
                actorAndProject,
                { JobsProvider(provider).terminate },
                BulkRequest(jobs),
                actorAndProject.signedIntentFromUser
            )
        }
    }

    suspend fun addUpdate(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ResourceUpdateAndId<JobUpdate>>
    ) {
        // TODO(Dan): Allocates a lot of memory which isn't needed
        val card = idCards.fetchIdCard(actorAndProject)
        val updatesByJob = request.items.groupBy { it.id }.mapValues { it.value.map { it.update } }

        for ((job, updates) in updatesByJob) {
            documents.addUpdate(
                card,
                job.toLongOrNull() ?: continue,
                updates.map {
                    ResourceStore.Update(
                        it.status,
                        defaultMapper.encodeToJsonElement(JobUpdate.serializer(), it)
                    )
                }
            )
        }
    }

    private suspend fun validate(actorAndProject: ActorAndProject, job: JobSpecification) {
        // TODO do something
    }

    private suspend fun findProduct(ref: ProductReference): Int {
        return 0
    }

    private suspend fun unmarshallDocument(doc: ResourceDocument<InternalJobState>): Job {
        val data = doc.data!!
        return Job(
            doc.id.toString(),
            ResourceOwner(
                "",
                null
            ),
            emptyList(),
            data.specification,
            JobStatus(
                data.state,
            ),
            doc.createdAt,
            permissions = ResourcePermissions(
                listOf(Permission.ADMIN),
                emptyList()
            )
        )
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
