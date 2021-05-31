package dk.sdu.cloud.file.orchestrator.service

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.orchestrator.api.extractPathMetadata
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.paginateV2
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*
import kotlinx.serialization.decodeFromString

class FileCollectionService(
    private val providers: Providers,
    private val providerSupport: ProviderSupport,
    private val projectCache: ProjectCache,
    private val db: DBContext,
) {
    suspend fun browse(
        actorAndProject: ActorAndProject,
        request: FileCollectionsBrowseRequest,
    ): FileCollectionsBrowseResponse {
        providers.prepareCommunication(request.provider)

        return db.paginateV2(
            actorAndProject.actor,
            request.normalize(),
            create = { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("user", actorAndProject.actor.safeUsername())
                            setParameter("project", actorAndProject.project)
                        },
                        """
                            declare c cursor for
                            select
                                provider.resource_to_json(r, file_orchestrator.collection_to_json(c)) ||
                                    jsonb_build_object('status', jsonb_build_object('quota', jsonb_build_object(), 'support', null))
                            from
                                provider.accessible_resources(:user, 'file_collection', 'READ', null, :project) r join
                                file_orchestrator.collections c on (r.resource).id = c.resource
                        """
                    )
            },
            mapper = { _, rows ->
                rows.map { defaultMapper.decodeFromString(it.getString(0)!!) }
            }
        )
    }

    suspend fun retrieve(
        actorAndProject: ActorAndProject,
        request: FileCollectionsRetrieveRequest,
    ): FileCollection {
        providers.prepareCommunication(request.provider)

        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("user", actorAndProject.actor.safeUsername())
                        setParameter("project", actorAndProject.project)
                        setParameter("id", request.id)
                    },
                    """
                        select provider.resource_to_json(r, file_orchestrator.collection_to_json(c))
                            from
                                provider.accessible_resources(:user, 'file_collection', 'READ', :id,
                                    :project::text) r join
                                file_orchestrator.collections c on (r.resource).id = c.resource
                    """
                )
                .rows
                .singleOrNull()
                ?.let { defaultMapper.decodeFromString(it.getString(0)!!) }
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
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
