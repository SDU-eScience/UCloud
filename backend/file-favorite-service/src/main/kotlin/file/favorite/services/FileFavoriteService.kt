package dk.sdu.cloud.file.favorite.services

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindMetadataRequest
import dk.sdu.cloud.file.api.MetadataDescriptions
import dk.sdu.cloud.file.api.MetadataUpdate
import dk.sdu.cloud.file.api.RemoveMetadataRequest
import dk.sdu.cloud.file.api.StatRequest
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.StorageFileAttribute
import dk.sdu.cloud.file.api.UpdateMetadataRequest
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.paginate
import dk.sdu.cloud.service.stackTraceToString

data class FavoritePayload(
    @get:JsonProperty("isFavorite")
    val isFavorite: Boolean
)

class FileFavoriteService(
    private val serviceClient: AuthenticatedClient
) {
    suspend fun toggleFavorite(
        files: List<String>,
        user: SecurityPrincipalToken,
        userCloud: AuthenticatedClient
    ): List<String> {
        // Note: This function must ensure that the user has the correct privileges to the file!
        val failures = ArrayList<String>()
        files.forEach { path ->
            try {
                FileDescriptions.stat.call(
                    StatRequest(path, attributes = "${StorageFileAttribute.path}"),
                    userCloud
                ).orThrow()

                val isFavorite: Boolean = runCatching {
                    val payload = MetadataDescriptions.findMetadata.call(
                        FindMetadataRequest(path, FAVORITE_METADATA_TYPE, user.principal.username),
                        serviceClient
                    ).orNull()?.metadata?.singleOrNull()?.jsonPayload ?: return@runCatching false

                    defaultMapper.readValue<FavoritePayload>(payload).isFavorite
                }.getOrElse { ex ->
                    log.info(ex.stackTraceToString())
                    false
                }

                if (isFavorite) {
                    MetadataDescriptions.removeMetadata.call(
                        RemoveMetadataRequest(
                            listOf(
                                FindMetadataRequest(
                                    path,
                                    FAVORITE_METADATA_TYPE,
                                    user.principal.username
                                )
                            )
                        ),
                        serviceClient
                    ).orThrow()
                } else {
                    MetadataDescriptions.updateMetadata.call(
                        UpdateMetadataRequest(
                            listOf(
                                MetadataUpdate(
                                    path,
                                    FAVORITE_METADATA_TYPE,
                                    user.principal.username,
                                    defaultMapper.writeValueAsString(FavoritePayload(!isFavorite))
                                )
                            )
                        ),
                        serviceClient
                    ).orThrow()
                }
            } catch (e: RPCException) {
                log.debug(e.stackTraceToString())
                failures.add(path)
            }
        }
        return failures
    }

    suspend fun getFavoriteStatus(files: List<StorageFile>, user: SecurityPrincipalToken): Map<String, Boolean> {
        val allMetadata = MetadataDescriptions.findMetadata.call(
            FindMetadataRequest(null, FAVORITE_METADATA_TYPE, user.principal.username),
            serviceClient
        ).orThrow()

        val filesSet = files.map { it.path.normalize() }.toSet()

        val result = HashMap<String, Boolean>()
        files.forEach { result[it.path.normalize()] = false }

        allMetadata.metadata
            .asSequence()
            .filter { it.path.normalize() in filesSet }
            .forEach {
                val isFavorite =
                    runCatching { defaultMapper.readValue<FavoritePayload>(it.jsonPayload) }.getOrNull()?.isFavorite
                if (isFavorite != null) {
                    result[it.path.normalize()] = isFavorite
                }
            }

        return result
    }

    suspend fun listAll(
        pagination: NormalizedPaginationRequest,
        user: SecurityPrincipalToken,
        userClient: AuthenticatedClient
    ): Page<StorageFile> {
        val allMetadata = MetadataDescriptions.findMetadata.call(
            FindMetadataRequest(null, FAVORITE_METADATA_TYPE, user.principal.username),
            serviceClient
        ).orThrow()

        return allMetadata.metadata
            .paginate(pagination)
            .run {
                val newItems = items.mapNotNull { metadata ->
                    FileDescriptions.stat.call(
                        StatRequest(metadata.path),
                        userClient
                    ).orNull()
                }

                Page(itemsInTotal, itemsPerPage, pageNumber, newItems)
            }
    }

    companion object : Loggable {
        override val log = logger()

        const val FAVORITE_METADATA_TYPE = "favorite"
    }
}
