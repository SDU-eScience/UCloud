package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.accounting.util.IdCardService
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.accounting.util.ResourceDocument
import dk.sdu.cloud.accounting.util.ResourceStore
import dk.sdu.cloud.accounting.util.invokeCall
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.orchestrator.api.JobIncludeFlags
import dk.sdu.cloud.app.orchestrator.api.JobSpecification
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.JobUpdate
import dk.sdu.cloud.app.orchestrator.api.JobsProvider
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import io.ktor.utils.io.pool.*

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
    private val documents: ResourceStore<InternalJobState>,
    private val idCards: IdCardService,
    private val providers: Providers<*>,
) {
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
                documents.updateProviderId(allocatedId, providerId.id)
            }

            output.add(FindByStringId(allocatedId.toString()))
        }

        return BulkResponse(output)
    }

    suspend fun browse(
        actorAndProject: ActorAndProject,
        request: ResourceBrowseRequest<JobIncludeFlags>,
    ): PageV2<Job> {
        val card = idCards.fetchIdCard(actorAndProject)
        val normalizedRequest = request.normalize()
        return ResourceOutputPool.withInstance { buffer ->
            // TODO(Dan): Sorting is an issue, especially if we are sorting using a custom property.
            val result = documents.browse(card, buffer, request.next, request.flags)

            val page = ArrayList<Job>(result.count)
            for (idx in 0 until result.count) {
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
        val doc = documents.retrieve(card, longId)
        if (doc == null) return null
        return unmarshallDocument(doc)
    }

    suspend fun terminate(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>
    ) {
        val card = idCards.fetchIdCard(actorAndProject)
        val allJobs = ResourceOutputPool.withInstance<InternalJobState, List<Job>> { buffer ->
            val count = documents.retrieveBulk(
                card,
                request.items.mapNotNull { it.id.toLongOrNull() }.toLongArray(),
                buffer
            )

            val result = ArrayList<Job>()
            for (idx in 0 until count) {
                result.add(unmarshallDocument(buffer[0]))
            }

            result
        }

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
        TODO()
    }

    private suspend fun validate(actorAndProject: ActorAndProject, job: JobSpecification) {
        // TODO do something
    }

    private suspend fun findProduct(ref: ProductReference): Int {
        return 0
    }

    private suspend fun unmarshallDocument(doc: ResourceDocument<InternalJobState>): Job {
        TODO()
    }
}
