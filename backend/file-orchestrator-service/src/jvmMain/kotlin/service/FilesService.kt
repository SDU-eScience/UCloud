package dk.sdu.cloud.file.orchestrator.service

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.orchestrator.api.extractPathMetadata
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.mapItems
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

typealias MoveHandler = suspend (batch: List<FilesMoveRequestItem>) -> Unit
typealias DeleteHandler = suspend (batch: List<FindByStringId>) -> Unit

class FilesService(
    private val fileCollections: FileCollectionService,
    providers: StorageProviders,
    providerSupport: StorageProviderSupport,
    private val metadataService: MetadataService,
) : ResourceSvc<UFile, UFileIncludeFlags, UFileSpecification, ResourceUpdate, Product.Storage, FSSupport> {
    private val moveHandlers = ArrayList<MoveHandler>()
    private val deleteHandlers = ArrayList<DeleteHandler>()
    private val proxy =
        ProviderProxy<StorageCommunication, Product.Storage, FSSupport, UFile>(providers, providerSupport)
    private val metadataCache = SimpleCache<Pair<ActorAndProject, String>, MetadataService.RetrieveWithHistory>(
        lookup = { (actorAndProject, parentPath) ->
            metadataService.retrieveWithHistory(actorAndProject, parentPath)
        }
    )

    fun addMoveHandler(handler: MoveHandler) {
        moveHandlers.add(handler)
    }

    fun addDeleteHandler(handler: DeleteHandler) {
        deleteHandlers.add(handler)
    }

    private fun verifyReadRequest(request: UFileIncludeFlags, support: FSSupport) {
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

    private suspend fun collectionFromPath(
        actorAndProject: ActorAndProject,
        path: String?,
        permission: Permission
    ): FileCollection {
        if (path == null) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        val collection = extractPathMetadata(path).collection
        return fileCollections.retrieveBulk(
            actorAndProject,
            listOf(collection),
            listOf(permission),
            simpleFlags = SimpleResourceIncludeFlags(includeSupport = true)
        ).singleOrNull() ?: throw RPCException("File not found", HttpStatusCode.NotFound)
    }

    override suspend fun browse(
        actorAndProject: ActorAndProject,
        request: ResourceBrowseRequest<UFileIncludeFlags>,
        useProject: Boolean,
        ctx: DBContext?
    ): PageV2<UFile> {
        val path = request.flags.path ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        val resolvedCollection = collectionFromPath(actorAndProject, path, Permission.Read)
        verifyReadRequest(request.flags, resolvedCollection.status.resolvedSupport!!.support)
        return coroutineScope {
            val browseJob = async {
                proxy.pureProxy(
                    actorAndProject,
                    resolvedCollection.specification.product,
                    { it.filesApi.browse },
                    FilesProviderBrowseRequest(resolvedCollection, request)
                )
            }

            val metadataJob = async {
                if (request.flags.includeMetadata == true) {
                    metadataCache.get(Pair(actorAndProject, path))
                } else {
                    null
                }
            }

            val browse = browseJob.await()
            val metadata = metadataJob.await()

            browse.mapItems {
                val metadataForFile = metadata?.metadataByFile?.get(it.id) ?: emptyMap()
                val templates = metadata?.templates ?: emptyMap()

                it.toUFile(resolvedCollection, FileMetadataHistory(templates, metadataForFile))
            }
        }
    }

    private fun PartialUFile.toUFile(resolvedCollection: FileCollection, metadata: FileMetadataHistory?): UFile {
        return UFile(
            id,
            UFileSpecification(
                resolvedCollection.id,
                resolvedCollection.specification.product
            ),
            createdAt,
            status.copy(metadata = metadata),
            owner ?: resolvedCollection.owner,
            permissions ?: resolvedCollection.permissions
        )
    }

    override suspend fun retrieve(
        actorAndProject: ActorAndProject,
        id: String,
        flags: UFileIncludeFlags?,
        ctx: DBContext?,
        asProvider: Boolean
    ): UFile {
        val path = flags?.path ?: id
        val resolvedCollection = collectionFromPath(actorAndProject, path, Permission.Read)
        verifyReadRequest(flags ?: UFileIncludeFlags(), resolvedCollection.status.resolvedSupport!!.support)

        return coroutineScope {
            val retrieveJob = async {
                proxy.pureProxy(
                    actorAndProject,
                    resolvedCollection.specification.product,
                    { it.filesApi.retrieve },
                    FilesProviderRetrieveRequest(
                        resolvedCollection,
                        ResourceRetrieveRequest(flags ?: UFileIncludeFlags(), id)
                    )
                )
            }

            val metadataJob = async {
                if (flags?.includeMetadata == true) {
                    val r = metadataService.retrieveWithHistory(actorAndProject,
                        path.parent(),
                        listOf(path.fileName())
                    )
                    FileMetadataHistory(r.templates, r.metadataByFile.values.singleOrNull() ?: emptyMap())
                } else {
                    null
                }
            }

            retrieveJob.await().toUFile(resolvedCollection, metadataJob.await())
        }
    }

    override suspend fun updateAcl(
        actorAndProject: ActorAndProject,
        request: BulkRequest<UpdatedAcl>
    ): BulkResponse<Unit?> {
        return bulkProxyEdit(
            actorAndProject,
            request,
            Permission.Admin,
            { it.filesApi.updateAcl },
            { extractPathMetadata(it.id).collection },
            { req, coll -> UpdatedAclWithResource(dummyResource(req.id, coll), req.added, req.deleted) },
            dontTolerateReadOnly = false,
            checkRequest = { _, fsSupport ->
                if (!fsSupport.files.aclModifiable || !fsSupport.files.aclSupported) {
                    throw RPCException(
                        "You cannot change the ACL of this file. Try a different drive.",
                        HttpStatusCode.BadRequest,
                        FEATURE_NOT_SUPPORTED_BY_PROVIDER
                    )
                }
            }
        )
    }

    override suspend fun delete(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>
    ): BulkResponse<Unit?> {
        return bulkProxyEdit(
            actorAndProject,
            request,
            Permission.Edit,
            { it.filesApi.delete },
            { extractPathMetadata(it.id).collection },
            { req, coll -> dummyResource(req.id, coll) },
            afterCall = { _, resources, response ->
                val batch = ArrayList<FindByStringId>()
                for ((index, res) in resources.withIndex()) {
                    if (response.responses[index] != null) {
                        batch.add(res.first)
                    }
                }

                deleteHandlers.forEach { handler -> handler(batch) }
            }
        )
    }

    override suspend fun retrieveProducts(
        actorAndProject: ActorAndProject
    ): SupportByProvider<Product.Storage, FSSupport> {
        return fileCollections.retrieveProducts(actorAndProject)
    }

    override suspend fun search(
        actorAndProject: ActorAndProject,
        request: ResourceSearchRequest<UFileIncludeFlags>,
        ctx: DBContext?
    ): PageV2<UFile> {
        // TODO This one is a bit harder since we have to contact every provider that a user might have files on
        TODO("Not yet implemented")
    }

    suspend fun move(
        actorAndProject: ActorAndProject,
        request: FilesMoveRequest
    ): FilesMoveResponse {
        return proxy.bulkProxy(
            actorAndProject,
            request,
            object : BulkProxyInstructions<StorageCommunication, FSSupport, FileCollection, FilesMoveRequestItem,
                BulkRequest<FilesProviderMoveRequestItem>, LongRunningTask>() {
                val newCollectionsByRequest =
                    HashMap<FilesMoveRequestItem, RequestWithRefOrResource<FilesMoveRequestItem, FileCollection>>()
                override val isUserRequest = true
                override fun retrieveCall(comms: StorageCommunication) = comms.filesApi.move

                override suspend fun verifyAndFetchResources(
                    actorAndProject: ActorAndProject,
                    request: BulkRequest<FilesMoveRequestItem>
                ): List<RequestWithRefOrResource<FilesMoveRequestItem, FileCollection>> {
                    val oldCollections = verifyAndFetchByIdable(actorAndProject, request, Permission.Edit) {
                        extractPathMetadata(it.oldId).collection }
                    val newCollections = verifyAndFetchByIdable(actorAndProject, request, Permission.Edit) {
                        extractPathMetadata(it.newId).collection }
                    for (item in newCollections) newCollectionsByRequest[item.first] = item

                    val oldProviders = oldCollections.map { it.second.resource.specification.product.provider }
                    val newProviders = newCollections.map { it.second.resource.specification.product.provider }
                    for ((old, new) in oldProviders.zip(newProviders)) {
                        if (old != new) {
                            throw RPCException(
                                "Cannot move files between providers. Try a different target drive.",
                                HttpStatusCode.BadRequest
                            )
                        }
                    }

                    return newCollections
                }

                override suspend fun verifyRequest(
                    request: FilesMoveRequestItem,
                    res: ProductRefOrResource<FileCollection>,
                    support: FSSupport
                ) {
                    if (support.files.isReadOnly) {
                        throw RPCException(
                            "File-system is read only and cannot be modified",
                            HttpStatusCode.BadRequest,
                            FEATURE_NOT_SUPPORTED_BY_PROVIDER
                        )
                    }
                }

                override suspend fun beforeCall(
                    provider: String,
                    resources: List<RequestWithRefOrResource<FilesMoveRequestItem, FileCollection>>
                ): BulkRequest<FilesProviderMoveRequestItem> {
                    return BulkRequest(resources.map { (req, res) ->
                        val oldCollection = (res as ProductRefOrResource.SomeResource).resource
                        val newCollection =
                            (newCollectionsByRequest[req]!!.second as ProductRefOrResource.SomeResource).resource

                        FilesProviderMoveRequestItem(
                            oldCollection,
                            newCollection,
                            req.oldId,
                            req.newId,
                            req.conflictPolicy
                        )
                    })
                }

                override suspend fun afterCall(
                    provider: String,
                    resources: List<RequestWithRefOrResource<FilesMoveRequestItem, FileCollection>>,
                    response: BulkResponse<LongRunningTask?>
                ) {
                    val batch = ArrayList<FilesMoveRequestItem>()
                    for ((index, res) in resources.withIndex()) {
                        if (response.responses[index] != null) {
                            batch.add(res.first)
                        }
                    }

                    moveHandlers.forEach { handler -> handler(batch) }
                }
            }
        )
    }

    suspend fun copy(
        actorAndProject: ActorAndProject,
        request: FilesCopyRequest
    ): FilesCopyResponse {
        return proxy.bulkProxy(
            actorAndProject,
            request,
            object : BulkProxyInstructions<StorageCommunication, FSSupport, FileCollection, FilesCopyRequestItem,
                BulkRequest<FilesProviderCopyRequestItem>, LongRunningTask>() {
                val newCollectionsByRequest =
                    HashMap<FilesCopyRequestItem, RequestWithRefOrResource<FilesCopyRequestItem, FileCollection>>()
                override val isUserRequest = true
                override fun retrieveCall(comms: StorageCommunication) = comms.filesApi.copy

                override suspend fun verifyAndFetchResources(
                    actorAndProject: ActorAndProject,
                    request: BulkRequest<FilesCopyRequestItem>
                ): List<RequestWithRefOrResource<FilesCopyRequestItem, FileCollection>> {
                    val oldCollections = verifyAndFetchByIdable(actorAndProject, request, Permission.Read) {
                        extractPathMetadata(it.oldId).collection }
                    val newCollections = verifyAndFetchByIdable(actorAndProject, request, Permission.Edit) {
                        extractPathMetadata(it.newId).collection }
                    for (item in newCollections) newCollectionsByRequest[item.first] = item

                    val oldProviders = oldCollections.map { it.second.resource.specification.product.provider }
                    val newProviders = newCollections.map { it.second.resource.specification.product.provider }
                    for ((old, new) in oldProviders.zip(newProviders)) {
                        if (old != new) {
                            throw RPCException(
                                "Cannot move files between providers. Try a different target drive.",
                                HttpStatusCode.BadRequest
                            )
                        }
                    }

                    return newCollections
                }

                override suspend fun verifyRequest(
                    request: FilesCopyRequestItem,
                    res: ProductRefOrResource<FileCollection>,
                    support: FSSupport
                ) {
                    if (support.files.isReadOnly) {
                        throw RPCException(
                            "File-system is read only and cannot be modified",
                            HttpStatusCode.BadRequest,
                            FEATURE_NOT_SUPPORTED_BY_PROVIDER
                        )
                    }
                }

                override suspend fun beforeCall(
                    provider: String,
                    resources: List<RequestWithRefOrResource<FilesCopyRequestItem, FileCollection>>
                ): BulkRequest<FilesProviderCopyRequestItem> {
                    return BulkRequest(resources.map { (req, res) ->
                        val oldCollection = (res as ProductRefOrResource.SomeResource).resource
                        val newCollection =
                            (newCollectionsByRequest[req]!!.second as ProductRefOrResource.SomeResource).resource

                        FilesProviderCopyRequestItem(
                            oldCollection,
                            newCollection,
                            req.oldId,
                            req.newId,
                            req.conflictPolicy
                        )
                    })
                }
            }
        )
    }

    suspend fun createUpload(
        actorAndProject: ActorAndProject,
        request: FilesCreateUploadRequest
    ): FilesCreateUploadResponse {
        // TODO(Dan): This needs to remap the results to add provider info!
        return bulkProxyEdit(
            actorAndProject,
            request,
            Permission.Edit,
            { it.filesApi.createUpload },
            { extractPathMetadata(it.id).collection },
            { req, coll -> FilesProviderCreateUploadRequestItem(
                coll,
                req.id,
                req.supportedProtocols,
                req.conflictPolicy
            )}
        )
    }

    suspend fun createDownload(
        actorAndProject: ActorAndProject,
        request: FilesCreateDownloadRequest
    ): FilesCreateDownloadResponse {
        // TODO(Dan): This needs to remap the results to add provider info!
        return bulkProxyEdit(
            actorAndProject,
            request,
            Permission.Read,
            { it.filesApi.createDownload },
            { extractPathMetadata(it.id).collection },
            { req, coll -> FilesProviderCreateDownloadRequestItem(coll, req.id) }
        )
    }

    suspend fun createFolder(
        actorAndProject: ActorAndProject,
        request: FilesCreateFolderRequest
    ): FilesCreateFolderResponse {
        return bulkProxyEdit(
            actorAndProject,
            request,
            Permission.Edit,
            { it.filesApi.createFolder },
            { extractPathMetadata(it.id).collection },
            { req, coll -> FilesProviderCreateFolderRequestItem(coll, req.id, req.conflictPolicy) }
        )
    }

    suspend fun trash(
        actorAndProject: ActorAndProject,
        request: FilesTrashRequest
    ): FilesTrashResponse {
        return bulkProxyEdit(
            actorAndProject,
            request,
            Permission.Edit,
            { it.filesApi.trash },
            { extractPathMetadata(it.id).collection },
            { req, coll -> FilesProviderTrashRequestItem(coll, req.id) }
        )
    }

    private suspend inline fun <R : Any> verifyAndFetchByIdable(
        actorAndProject: ActorAndProject,
        request: BulkRequest<R>,
        permission: Permission,
        idGetter: (R) -> String,
    ): List<Pair<R, ProductRefOrResource.SomeResource<FileCollection>>> {
        val collections = fileCollections.retrieveBulk(
            actorAndProject,
            request.items.map(idGetter),
            listOf(permission)
        )

        return request.items.zip(collections).map { (req, coll) ->
            req to ProductRefOrResource.SomeResource(coll)
        }
    }

    private fun dummyResource(fileId: String, collection: FileCollection): UFile = UFile(
        fileId,
        UFileSpecification(collection.id, collection.specification.product),
        0L,
        UFileStatus(),
        collection.owner,
        null
    )

    private suspend fun <Req : Any, ReqProvider : Any, Res : Any> bulkProxyEdit(
        actorAndProject: ActorAndProject,
        request: BulkRequest<Req>,
        permission: Permission,
        call: (comms: StorageCommunication) ->
            CallDescription<BulkRequest<ReqProvider>, BulkResponse<Res?>, CommonErrorMessage>,
        idFetcher: (Req) -> String,
        requestMapper: (Req, FileCollection) -> ReqProvider,
        isUserRequest: Boolean = true,
        dontTolerateReadOnly: Boolean = true,
        checkRequest: (Req, FSSupport) -> Unit = { _, _ -> },
        afterCall: suspend (provider: String, resources: List<RequestWithRefOrResource<Req, FileCollection>>,
                    response: BulkResponse<Res?>) -> Unit = { _, _, _ -> }
    ): BulkResponse<Res?> {
        return proxy.bulkProxy(
            actorAndProject,
            request,
            object : BulkProxyInstructions<StorageCommunication, FSSupport, FileCollection, Req,
                BulkRequest<ReqProvider>, Res>() {
                override val isUserRequest = isUserRequest
                override fun retrieveCall(comms: StorageCommunication) = call(comms)

                override suspend fun verifyAndFetchResources(
                    actorAndProject: ActorAndProject,
                    request: BulkRequest<Req>
                ) = verifyAndFetchByIdable(actorAndProject, request, permission, idFetcher)

                override suspend fun verifyRequest(
                    request: Req,
                    res: ProductRefOrResource<FileCollection>,
                    support: FSSupport
                ) {
                    if (dontTolerateReadOnly && support.files.isReadOnly) {
                        throw RPCException(
                            "File-system is read only and cannot be modified",
                            HttpStatusCode.BadRequest,
                            FEATURE_NOT_SUPPORTED_BY_PROVIDER
                        )
                    }

                    checkRequest(request, support)
                }

                override suspend fun beforeCall(
                    provider: String,
                    resources: List<RequestWithRefOrResource<Req, FileCollection>>
                ): BulkRequest<ReqProvider> {
                    return BulkRequest(resources.map { (req, res) ->
                        val collection = (res as ProductRefOrResource.SomeResource).resource
                        requestMapper(req, collection)
                    })
                }

                override suspend fun afterCall(
                    provider: String,
                    resources: List<RequestWithRefOrResource<Req, FileCollection>>,
                    response: BulkResponse<Res?>
                ) {
                    afterCall(provider, resources, response)
                }
            }
        )
    }
}
