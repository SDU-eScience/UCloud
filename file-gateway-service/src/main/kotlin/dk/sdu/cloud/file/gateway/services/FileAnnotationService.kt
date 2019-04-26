package dk.sdu.cloud.file.gateway.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.fileId
import dk.sdu.cloud.file.favorite.api.FavoriteStatusRequest
import dk.sdu.cloud.file.favorite.api.FavoriteStatusResponse
import dk.sdu.cloud.file.favorite.api.FileFavoriteDescriptions
import dk.sdu.cloud.file.gateway.api.FileResource
import dk.sdu.cloud.file.gateway.api.StorageFileWithMetadata
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class FileAnnotationService {
    suspend fun annotate(
        resourcesToLoad: Set<FileResource>,
        files: List<StorageFile>,
        userCloud: AuthenticatedClient
    ): List<StorageFileWithMetadata> {
        var favoriteStatusResponse: FavoriteStatusResponse? = null

        coroutineScope {
            if (FileResource.FAVORITES in resourcesToLoad) {
                val favoriteStatus = async {
                    FileFavoriteDescriptions.favoriteStatus.call(
                        FavoriteStatusRequest(files),
                        userCloud
                    ).orThrow()
                }

                favoriteStatusResponse = favoriteStatus.await()
            }
        }

        return files.map {
            StorageFileWithMetadata(
                delegate = it,
                favorited = favoriteStatusResponse?.favorited?.get(it.fileId)
            )
        }
    }
}
