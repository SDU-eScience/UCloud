package dk.sdu.cloud.share.services

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.bearerAuth
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withoutAuthentication
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.paginate
import dk.sdu.cloud.share.api.MinimalShare
import dk.sdu.cloud.share.api.SharesByPath
import io.ktor.http.HttpStatusCode

class ShareQueryService(
    private val client: AuthenticatedClient
) {
    suspend fun list(
        user: String,
        sharedByMe: Boolean,
        paging: NormalizedPaginationRequest = NormalizedPaginationRequest(null, null)
    ): Page<SharesByPath> {
        if (sharedByMe) {
            val homeFolder = homeDirectory(user)
            val metadata = MetadataDescriptions.findByPrefix.call(
                FindByPrefixRequest(
                    homeFolder,
                    null,
                    METADATA_TYPE_SHARES
                ),
                client
            ).orThrow().metadata

            return metadataToSharesByPath(metadata, user, paging)
        } else {
            val metadata = MetadataDescriptions.findMetadata.call(
                FindMetadataRequest(null, METADATA_TYPE_SHARES, user),
                client
            ).orThrow().metadata
            return metadataToSharesByPath(metadata, user, paging)
        }
    }

    private fun metadataToSharesByPath(
        metadata: List<MetadataUpdate>,
        user: String,
        paging: NormalizedPaginationRequest
    ): Page<SharesByPath> {
        val metadataByPath = metadata
            .asSequence()
            .map { it.path to defaultMapper.readValue<InternalShare>(it.jsonPayload) }
            .groupBy { it.first }

        val sharesByPath = metadataByPath
            .mapValues { (path, shares) ->
                SharesByPath(
                    path,
                    shares.first().second.sharedBy,
                    shares.first().second.sharedBy == user,
                    shares.map { (_, share) -> MinimalShare(share.sharedWith, share.rights, share.state) }
                )
            }

        val keysToKeep = sharesByPath.keys.toList().paginate(paging).items.toSet()
        val items = sharesByPath.filter { it.key in keysToKeep }

        return Page(
            sharesByPath.size,
            paging.itemsPerPage,
            paging.page,
            items.values.toList()
        )
    }

    suspend fun findSharesForPath(
        user: String,
        path: String,
        userAccessToken: String
    ): SharesByPath {
        val userClient = client.withoutAuthentication().bearerAuth(userAccessToken)
        FileDescriptions.stat.call(StatRequest(path), userClient).orThrow()

        val metadata = MetadataDescriptions.findMetadata.call(
            FindMetadataRequest(path, METADATA_TYPE_SHARES, null),
            client
        ).orThrow().metadata

        return metadataToSharesByPath(metadata, user, NormalizedPaginationRequest(null, null))
            .items
            .singleOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
    }

    suspend fun listFiles(
        user: String,
        paging: NormalizedPaginationRequest,
        userToken: String
    ): Page<StorageFile> {
        val userClient = client.withoutAuthentication().bearerAuth(userToken)
        val page = list(user, false, paging)
        val newItems = page.items.mapNotNull { item ->
            FileDescriptions.stat.call(
                StatRequest(item.path),
                userClient
            ).orNull()
        }

        return Page(page.itemsInTotal, page.itemsPerPage, page.pageNumber, newItems)
    }
}

