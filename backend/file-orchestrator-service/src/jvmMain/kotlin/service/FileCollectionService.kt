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
}
