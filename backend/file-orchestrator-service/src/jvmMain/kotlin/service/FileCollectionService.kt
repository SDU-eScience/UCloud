package dk.sdu.cloud.file.orchestrator.service

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.orchestrator.api.extractPathMetadata
import dk.sdu.cloud.safeUsername
import io.ktor.http.*

class FileCollectionService(
    private val providers: Providers,
    private val providerSupport: ProviderSupport,
    private val projectCache: ProjectCache,
) {
    suspend fun browse(
        actorAndProject: ActorAndProject,
        request: FileCollectionsBrowseRequest,
    ): FileCollectionsBrowseResponse {
        val comms = providers.prepareCommunication(request.provider)

        return comms.fileCollectionsApi.browse.call(
            proxiedRequest(
                projectCache,
                actorAndProject,
                FileCollectionsProviderBrowseRequest(
                    request.itemsPerPage,
                    request.next,
                    request.consistency,
                    request.itemsToSkip
                )
            ),
            comms.client
        ).orThrow()
    }

    suspend fun retrieve(
        actorAndProject: ActorAndProject,
        request: FileCollectionsRetrieveRequest,
    ): FileCollection {
        val comms = providers.prepareCommunication(request.provider)

        return comms.fileCollectionsApi.retrieve.call(
            proxiedRequest(
                projectCache,
                actorAndProject,
                FindByStringId(request.id)
            ),
            comms.client
        ).orThrow()
    }

    suspend fun retrieveManifest(
        request: FileCollectionsRetrieveManifestRequest,
    ): FileCollectionsRetrieveManifestResponse {
        val comms = providers.prepareCommunication(request.provider)
        return comms.fileCollectionsApi.retrieveManifest.call(Unit, comms.client).orThrow()
    }

    suspend fun create(
        actorAndProject: ActorAndProject,
        request: FileCollectionsCreateRequest,
    ): BulkResponse<FindByStringId> {
        val requestsByProvider = HashMap<String, List<Pair<Int, FileCollection.Spec>>>()
        for ((index, reqItem) in request.items.withIndex()) {
            requestsByProvider[reqItem.product.provider] =
                (requestsByProvider[reqItem.product.provider] ?: emptyList()) + Pair(index, reqItem)
        }
        val allIds = arrayOfNulls<FindByStringId?>(request.items.size)
        for ((provider, requests) in requestsByProvider) {
            val comms = providers.prepareCommunication(provider)
            val ids = comms.fileCollectionsApi.create.call(
                proxiedRequest(projectCache, actorAndProject, bulkRequestOf(requests.map { it.second })),
                comms.client,
            ).orThrow().responses
            for ((index, id) in ids.withIndex()) {
                allIds[requestsByProvider[provider]!![index].first] = id
            }
        }
        return BulkResponse(allIds.filterNotNull())
    }

    suspend fun rename(
        actorAndProject: ActorAndProject,
        request: FileCollectionsRenameRequest,
    ) {
        val requestsByProvider = request.items.groupBy { it.provider }
        for ((provider, requests) in requestsByProvider) {
            val comms = providers.prepareCommunication(provider)
            comms.fileCollectionsApi.rename.call(
                proxiedRequest(
                    projectCache,
                    actorAndProject,
                    bulkRequestOf(requests.map { FileCollectionsProviderRenameRequestItem(it.id, it.newTitle) })
                ),
                comms.client
            ).orThrow()
        }
    }

    suspend fun delete(
        actorAndProject: ActorAndProject,
        request: FileCollectionsDeleteRequest,
    ) {
        val requestsByProvider = request.items.groupBy { it.provider }
        for ((provider, requests) in requestsByProvider) {
            val comms = providers.prepareCommunication(provider)
            comms.fileCollectionsApi.delete.call(
                proxiedRequest(
                    projectCache,
                    actorAndProject,
                    bulkRequestOf(requests.map { FindByStringId(it.id) })
                ),
                comms.client
            ).orThrow()
        }
    }

    suspend fun updateAcl(
        actorAndProject: ActorAndProject,
        request: FileCollectionsUpdateAclRequest,
    ) {
        val requestsByProvider = request.items.groupBy { it.provider }
        for ((provider, requests) in requestsByProvider) {
            val comms = providers.prepareCommunication(provider)
            comms.fileCollectionsApi.updateAcl.call(
                proxiedRequest(
                    projectCache,
                    actorAndProject,
                    bulkRequestOf(requests.map { FileCollectionsProviderUpdateAclRequestItem(it.id, it.newAcl) })
                ),
                comms.client
            ).orThrow()
        }
    }
}
