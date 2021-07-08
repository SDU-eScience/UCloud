package dk.sdu.cloud.file.orchestrator.service

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.orchestrator.api.extractPathMetadata
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.db.async.DBContext
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class FilesService(
    private val fileCollections: FileCollectionService,
    private val providers: StorageProviders,
    private val providerSupport: StorageProviderSupport,
    private val metadataService: MetadataService,
) : ResourceSvc<UFile, UFileIncludeFlags, UFileSpecification, ResourceUpdate, Product.Storage, FSSupport> {
    private val proxy =
        ProviderProxy<StorageCommunication, Product.Storage, FSSupport, UFile>(providers, providerSupport)
    private val metadataCache = SimpleCache<Pair<ActorAndProject, String>, MetadataService.RetrieveWithHistory>(
        lookup = { (actorAndProject, parentPath) ->
            metadataService.retrieveWithHistory(actorAndProject, parentPath)
        }
    )

    /*
    suspend fun retrieve(actorAndProject: ActorAndProject, request: FilesRetrieveRequest): FilesRetrieveResponse {
        val pathMetadata = extractPathMetadata(request.path)
        val comms = providers.prepareCommunication(pathMetadata.productReference.provider)
        val (product, support) = providerSupport.retrieveProductSupport(pathMetadata.productReference)
        verifyReadRequest(request, support)

        return coroutineScope {
            val retrieveJob = async {
                comms.filesApi.retrieve.call(
                    request,
                    comms.client
                ).orThrow()
            }

            val metadataJob = async {
                if (request.includeMetadata == true) {
                    val r = metadataService.retrieveWithHistory(actorAndProject,
                        request.path.parent(),
                        listOf(request.path.fileName())
                    )
                    FileMetadataHistory(r.templates, r.metadataByFile.values.singleOrNull() ?: emptyMap())
                } else {
                    null
                }
            }

            val retrieved = retrieveJob.await()
            val metadata = metadataJob.await()

            retrieved.copy(metadata = metadata)
        }
    }
     */

    private fun verifyReadRequest(request: FilesIncludeFlags, support: FSSupport) {
        if (request.allowUnsupportedInclude != true) {
            // Request verification is needed
            if (request.includePermissions == true && support.files.aclSupported != true) {
                throw RPCException("Operation not supported by the provider", HttpStatusCode.BadRequest)
            }

            if (request.includeSizes == true && support.stats.sizeInBytes != true &&
                support.stats.sizeIncludingChildrenInBytes != true
            ) {
                throw RPCException("Operation not supported by the provider", HttpStatusCode.BadRequest)
            }

            if (request.includeTimestamps == true && support.stats.accessedAt != true &&
                support.stats.createdAt != true && support.stats.modifiedAt != true
            ) {
                throw RPCException("Operation not supported by the provider", HttpStatusCode.BadRequest)
            }

            if (request.includeUnixInfo == true && support.stats.unixOwner != true &&
                support.stats.unixGroup != true && support.stats.unixPermissions != true
            ) {
                throw RPCException("Operation not supported by the provider", HttpStatusCode.BadRequest)
            }
        }
    }

    /*
    private suspend fun <T : WithPathMoving> prepareCopyOrMove(request: BulkRequest<T>): Map<String, List<T>> {
        val requestsByProvider = HashMap<String, List<T>>()
        for (requestItem in request.items) {
            val newMetadata = extractPathMetadata(requestItem.oldPath)
            val oldMetadata = extractPathMetadata(requestItem.newPath)

            if (newMetadata.productReference.provider != oldMetadata.productReference.provider) {
                throw RPCException("Cannot move two files across two different providers", HttpStatusCode.BadRequest)
            }

            val (_, newSupport) = providerSupport.retrieveProductSupport(newMetadata.productReference)
            val (_, oldSupport) = providerSupport.retrieveProductSupport(oldMetadata.productReference)

            if (newSupport.files.isReadOnly) {
                throw RPCException("Cannot move file to the new location (read-only system)", HttpStatusCode.BadRequest)
            }

            val provider = newMetadata.productReference.provider
            requestsByProvider[provider] = (requestsByProvider[provider] ?: emptyList()) + requestItem
        }
        return requestsByProvider
    }
     */

    private suspend fun collectionFromPath(
        actorAndProject: ActorAndProject,
        path: String?,
        permission: Permission
    ): FileCollection {
        if (path == null) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        val collection = extractPathMetadata(path).collection
        return fileCollections.retrieveBulk(actorAndProject, listOf(collection), listOf(permission)).singleOrNull()
            ?: throw RPCException("File not found", HttpStatusCode.NotFound)
    }

    override suspend fun browse(
        actorAndProject: ActorAndProject,
        request: ResourceBrowseRequest<UFileIncludeFlags>,
        useProject: Boolean,
        ctx: DBContext?
    ): PageV2<UFile> {
        val resolvedCollection = collectionFromPath(actorAndProject, request.flags.path, Permission.Read)
        return proxy.pureProxy(
            actorAndProject,
            { it.filesApi.browse },
            FilesProviderBrowseRequest(resolvedCollection, request)
        )
    }

    override suspend fun retrieve(
        actorAndProject: ActorAndProject,
        id: String,
        flags: UFileIncludeFlags?,
        ctx: DBContext?,
        asProvider: Boolean
    ): UFile {
        val resolvedCollection = collectionFromPath(actorAndProject, flags?.path ?: id, Permission.Read)
        return proxy.pureProxy(actorAndProject, { it.filesApi.retrieve },
            FilesProviderRetrieveRequest(resolvedCollection, ResourceRetrieveRequest(flags ?: UFileIncludeFlags(), id)))
    }

    override suspend fun create(
        actorAndProject: ActorAndProject,
        request: BulkRequest<UFileSpecification>
    ): BulkResponse<FindByStringId?> {
        throw RPCException("Files can only be created via uploads", HttpStatusCode.BadRequest)
    }

    override suspend fun updateAcl(
        actorAndProject: ActorAndProject,
        request: BulkRequest<UpdatedAcl>
    ): BulkResponse<Unit?> {
        TODO("Not yet implemented")
    }

    override suspend fun delete(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>
    ): BulkResponse<Unit?> {
        TODO("Not yet implemented")
    }

    override suspend fun addUpdate(
        actorAndProject: ActorAndProject,
        updates: BulkRequest<ResourceUpdateAndId<ResourceUpdate>>
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun register(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ProviderRegisteredResource<UFileSpecification>>
    ): BulkResponse<FindByStringId> {
        TODO("Not yet implemented")
    }

    override suspend fun retrieveProducts(actorAndProject: ActorAndProject): SupportByProvider<Product.Storage, FSSupport> {
        TODO("Not yet implemented")
    }

    override suspend fun chargeCredits(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ResourceChargeCredits>
    ): ResourceChargeCreditsResponse {
        TODO("Not yet implemented")
    }

    override suspend fun search(
        actorAndProject: ActorAndProject,
        request: ResourceSearchRequest<UFileIncludeFlags>,
        ctx: DBContext?
    ): PageV2<UFile> {
        TODO("Not yet implemented")
    }
}
