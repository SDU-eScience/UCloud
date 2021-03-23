package dk.sdu.cloud.file.orchestrator.service

import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.orchestrator.api.extractPathMetadata
import io.ktor.http.*

class FilesService(
    private val providers: Providers,
    private val providerSupport: ProviderSupport,
) {
    suspend fun browse(request: FilesBrowseRequest): FilesBrowseResponse {
        val pathMetadata = extractPathMetadata(request.path)
        val comms = providers.prepareCommunication(pathMetadata.productReference.provider)
        val (product, support) = providerSupport.retrieveProductSupport(pathMetadata.productReference)
        verifyReadRequest(request, support)

        return comms.filesApi.browse.call(request, comms.client).orThrow()
    }

    suspend fun retrieve(request: FilesRetrieveRequest): FilesRetrieveResponse {
        val pathMetadata = extractPathMetadata(request.path)
        val comms = providers.prepareCommunication(pathMetadata.productReference.provider)
        val (product, support) = providerSupport.retrieveProductSupport(pathMetadata.productReference)
        verifyReadRequest(request, support)

        return comms.filesApi.retrieve.call(request, comms.client).orThrow()
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

    suspend fun move(request: FilesMoveRequest): FilesMoveResponse {
        val requestsByProvider = prepareCopyOrMove(request)

        val responses = ArrayList<LongRunningTask<FindByPath>>()
        for ((provider, requestItems) in requestsByProvider) {
            val comms = providers.prepareCommunication(provider)
            responses.addAll(
                comms.filesApi.move.call(bulkRequestOf(requestItems), comms.client).orThrow().responses
            )
        }

        // TODO Wrong order
        return FilesMoveResponse(responses)
    }

    suspend fun copy(request: FilesCopyRequest): FilesCopyResponse {
        val requestsByProvider = prepareCopyOrMove(request)

        val responses = ArrayList<LongRunningTask<FindByPath>>()
        for ((provider, requestItems) in requestsByProvider) {
            val comms = providers.prepareCommunication(provider)
            responses.addAll(
                comms.filesApi.copy.call(bulkRequestOf(requestItems), comms.client).orThrow().responses
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

    suspend fun delete(request: FilesDeleteRequest): FilesDeleteResponse {
        return proxyRequest(
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
                comms.filesApi.delete.call(bulkRequestOf(requestItems), comms.client).orThrow()
            }
        )
    }

    suspend fun createUpload(request: FilesCreateUploadRequest): FilesCreateUploadResponse {
        return proxyRequest(
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
                comms.filesApi.createUpload.call(bulkRequestOf(requestItems), comms.client).orThrow()
            }
        )
    }

    suspend fun createDownload(request: FilesCreateDownloadRequest): FilesCreateDownloadResponse {
        return proxyRequest(
            request,
            verifyRequest = { support, _ -> },
            proxyRequest = { comms, requestItems ->
                comms.filesApi.createDownload.call(bulkRequestOf(requestItems), comms.client).orThrow()
            }
        )
    }

    suspend fun createFolder(request: FilesCreateFolderRequest): FilesCreateFolderResponse {
        return proxyRequest(
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
                comms.filesApi.createFolder.call(bulkRequestOf(requestItems), comms.client).orThrow()
            }
        )
    }

    suspend fun trash(request: FilesTrashRequest): FilesTrashResponse {
        return proxyRequest(
            request,
            verifyRequest = { support, _ ->
                if (support.files.trashSupported) {
                    throw RPCException(
                        "Cannot create files at this location (read-only system)",
                        HttpStatusCode.BadRequest
                    )
                }
            },
            proxyRequest = { comms, requestItems ->
                comms.filesApi.trash.call(bulkRequestOf(requestItems), comms.client).orThrow()
            }
        )
    }

    suspend fun updateAcl(request: FilesUpdateAclRequest): FilesUpdateAclResponse {
        proxyRequest(
            request,
            verifyRequest = { support, _ ->
                if (support.files.aclSupported && support.files.aclModifiable) {
                    throw RPCException("Cannot update permissions on this system", HttpStatusCode.BadRequest)
                }
            },
            proxyRequest = { comms, requestItems ->
                comms.filesApi.updateAcl.call(bulkRequestOf(requestItems), comms.client).orThrow()
                BulkResponse<Unit>(emptyList())
            }
        )
    }

    private suspend inline fun <T : WithPath, R> proxyRequest(
        request: BulkRequest<T>,
        verifyRequest: (FSSupport, T) -> Unit,
        proxyRequest: (comms: ProviderCommunication, requestItems: List<T>) -> BulkResponse<R>
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
            val comms = providers.prepareCommunication(provider)
            responses.addAll(proxyRequest(comms, requestItems).responses)
        }
        return BulkResponse(responses)
    }
}
