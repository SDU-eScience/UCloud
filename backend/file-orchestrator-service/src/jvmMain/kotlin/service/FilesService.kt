package dk.sdu.cloud.file.orchestrator.service

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.orchestrator.api.extractPathMetadata
import dk.sdu.cloud.provider.api.IntegrationProvider
import dk.sdu.cloud.provider.api.IntegrationProviderInitRequest
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.SimpleCache
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

suspend fun <T> proxiedRequest(
    projectCache: ProjectCache,
    actorAndProject: ActorAndProject,
    request: T,
): ProxiedRequest<T> {
    val project = actorAndProject.project
    if (project != null) {
        projectCache.retrieveRole(actorAndProject.actor.safeUsername(), project)
            ?: throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
    }

    return ProxiedRequest.createIPromiseToHaveVerifiedTheProjectBeforeCreating(
        actorAndProject.actor.safeUsername(),
        project,
        request
    )
}

class FilesService(
    private val providers: Providers,
    private val providerSupport: ProviderSupport,
    private val projectCache: ProjectCache,
    private val metadataService: MetadataService,
) {
    private val metadataCache = SimpleCache<Pair<ActorAndProject, String>, MetadataService.RetrieveWithHistory>(
        lookup = { (actorAndProject, parentPath) ->
            metadataService.retrieveWithHistory(actorAndProject, parentPath)
        }
    )

    suspend fun browse(actorAndProject: ActorAndProject, request: FilesBrowseRequest): FilesBrowseResponse {
        val pathMetadata = extractPathMetadata(request.path)
        val comms = providers.prepareCommunication(pathMetadata.productReference.provider)
        val (product, support) = providerSupport.retrieveProductSupport(pathMetadata.productReference)
        verifyReadRequest(request, support)

        return coroutineScope {
            val browseJob = async {
                comms.filesApi.browse.call(
                    proxiedRequest(projectCache, actorAndProject, request),
                    comms.client
                ).orThrow()
            }

            val metadataJob = async {
                if (request.includeMetadata == true) {
                    metadataCache.get(Pair(actorAndProject, request.path))
                } else {
                    null
                }
            }

            val files = browseJob.await()
            val metadata = metadataJob.await()

            files.copy(
                items = files.items.map { file ->
                    val metadataForFile = metadata?.metadataByFile?.get(file.path) ?: emptyMap()
                    val templates = metadata?.templates ?: emptyMap()
                    file.copy(metadata = FileMetadataHistory(templates, metadataForFile))
                }
            )
        }
    }

    suspend fun retrieve(actorAndProject: ActorAndProject, request: FilesRetrieveRequest): FilesRetrieveResponse {
        val pathMetadata = extractPathMetadata(request.path)
        val comms = providers.prepareCommunication(pathMetadata.productReference.provider)
        val (product, support) = providerSupport.retrieveProductSupport(pathMetadata.productReference)
        verifyReadRequest(request, support)

        return coroutineScope {
            val retrieveJob = async {
                comms.filesApi.retrieve.call(
                    proxiedRequest(
                        projectCache,
                        actorAndProject,
                        request
                    ),
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

    suspend fun move(actorAndProject: ActorAndProject, request: FilesMoveRequest): FilesMoveResponse {
        val requestsByProvider = prepareCopyOrMove(request)

        val responses = ArrayList<LongRunningTask>()
        for ((provider, requestItems) in requestsByProvider) {
            val comms = providers.prepareCommunication(provider)
            responses.addAll(
                comms.filesApi.move.call(
                    proxiedRequest(
                        projectCache,
                        actorAndProject,
                        bulkRequestOf(requestItems)
                    ),
                    comms.client
                ).orThrow().responses
            )
        }

        // TODO Wrong order
        return FilesMoveResponse(responses)
    }

    suspend fun copy(actorAndProject: ActorAndProject, request: FilesCopyRequest): FilesCopyResponse {
        val requestsByProvider = prepareCopyOrMove(request)

        val responses = ArrayList<LongRunningTask>()
        for ((provider, requestItems) in requestsByProvider) {
            val comms = providers.prepareCommunication(provider)
            responses.addAll(
                comms.filesApi.copy.call(
                    proxiedRequest(
                        projectCache,
                        actorAndProject,
                        bulkRequestOf(requestItems)
                    ),
                    comms.client
                ).orThrow().responses
            )
        }

        // TODO Wrong order
        return FilesCopyResponse(responses)
    }

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

    suspend fun delete(actorAndProject: ActorAndProject, request: FilesDeleteRequest): FilesDeleteResponse {
        return proxyRequest(
            actorAndProject.actor,
            request,
            verifyRequest = { support, _ ->
                if (support.files.isReadOnly) {
                    throw RPCException(
                        "Cannot create files at this location (read-only system)",
                        HttpStatusCode.BadRequest
                    )
                }
            },
            proxyRequest = { comms, requestItems ->
                comms.filesApi.delete.call(
                    proxiedRequest(
                        projectCache,
                        actorAndProject,
                        bulkRequestOf(requestItems)
                    ),
                    comms.client
                ).orThrow()
            }
        )
    }

    suspend fun createUpload(
        actorAndProject: ActorAndProject,
        request: FilesCreateUploadRequest,
    ): FilesCreateUploadResponse {
        return proxyRequest(
            actorAndProject.actor,
            request,
            verifyRequest = { support, _ ->
                if (support.files.isReadOnly) {
                    throw RPCException(
                        "Cannot create files at this location (read-only system)",
                        HttpStatusCode.BadRequest
                    )
                }
            },
            proxyRequest = { comms, requestItems ->
                comms.filesApi.createUpload.call(
                    proxiedRequest(
                        projectCache,
                        actorAndProject,
                        bulkRequestOf(requestItems)
                    ),
                    comms.client
                ).orThrow()
            }
        )
    }

    suspend fun createDownload(
        actorAndProject: ActorAndProject,
        request: FilesCreateDownloadRequest,
    ): FilesCreateDownloadResponse {
        return proxyRequest(
            actorAndProject.actor,
            request,
            verifyRequest = { support, _ -> },
            proxyRequest = { comms, requestItems ->
                comms.filesApi.createDownload.call(
                    proxiedRequest(
                        projectCache,
                        actorAndProject,
                        bulkRequestOf(requestItems)
                    ),
                    comms.client
                ).orThrow()
            }
        )
    }

    suspend fun createFolder(
        actorAndProject: ActorAndProject,
        request: FilesCreateFolderRequest,
    ): FilesCreateFolderResponse {
        return proxyRequest(
            actorAndProject.actor,
            request,
            verifyRequest = { support, _ ->
                if (support.files.isReadOnly) {
                    throw RPCException(
                        "Cannot create files at this location (read-only system)",
                        HttpStatusCode.BadRequest
                    )
                }
            },
            proxyRequest = { comms, requestItems ->
                comms.filesApi.createFolder.call(
                    proxiedRequest(
                        projectCache,
                        actorAndProject,
                        bulkRequestOf(requestItems)
                    ),
                    comms.client
                ).orThrow()
            }
        )
    }

    suspend fun trash(actorAndProject: ActorAndProject, request: FilesTrashRequest): FilesTrashResponse {
        return proxyRequest(
            actorAndProject.actor,
            request,
            verifyRequest = { support, _ ->
                if (!support.files.trashSupported) {
                    throw RPCException(
                        "Trash not supported by this system",
                        HttpStatusCode.BadRequest
                    )
                }
            },
            proxyRequest = { comms, requestItems ->
                comms.filesApi.trash.call(
                    proxiedRequest(
                        projectCache,
                        actorAndProject,
                        bulkRequestOf(requestItems)
                    ),
                    comms.client
                ).orThrow()
            }
        )
    }

    suspend fun updateAcl(actorAndProject: ActorAndProject, request: FilesUpdateAclRequest): FilesUpdateAclResponse {
        proxyRequest(
            actorAndProject.actor,
            request,
            verifyRequest = { support, _ ->
                if (!support.files.aclSupported || !support.files.aclModifiable) {
                    throw RPCException("Cannot update permissions on this system", HttpStatusCode.BadRequest)
                }
            },
            proxyRequest = { comms, requestItems ->
                comms.filesApi.updateAcl.call(
                    proxiedRequest(
                        projectCache,
                        actorAndProject,
                        bulkRequestOf(requestItems)
                    ),
                    comms.client
                ).orThrow()
                BulkResponse<Unit>(emptyList())
            }
        )
    }

    private suspend inline fun <T : WithPath, R> proxyRequest(
        actor: Actor,
        request: BulkRequest<T>,
        verifyRequest: (FSSupport, T) -> Unit,
        proxyRequest: (comms: ProviderCommunication, requestItems: List<T>) -> BulkResponse<R>,
    ): BulkResponse<R> {
        val requestsByProvider = HashMap<String, List<T>>()
        for (requestItem in request.items) {
            val metadata = extractPathMetadata(requestItem.path)
            val (_, support) = providerSupport.retrieveProductSupport(metadata.productReference)

            verifyRequest(support, requestItem)

            val provider = metadata.productReference.provider
            requestsByProvider[provider] = (requestsByProvider[provider] ?: emptyList()) + requestItem
        }

        val responses = ArrayList<R>()
        for ((provider, requestItems) in requestsByProvider) {
            val integration = IntegrationProvider(provider)
            val comms = providers.prepareCommunication(provider)
            integration.init.call(IntegrationProviderInitRequest(actor.safeUsername()), comms.client)
            responses.addAll(proxyRequest(comms, requestItems).responses)
        }
        return BulkResponse(responses)
    }
}
