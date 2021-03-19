package dk.sdu.cloud.file.orchestrator.service

import dk.sdu.cloud.file.orchestrator.FilesBrowseRequest
import dk.sdu.cloud.file.orchestrator.FilesBrowseResponse
import dk.sdu.cloud.file.orchestrator.api.extractProviderAndCollectionFromPath

class FilesService(
    private val providers: Providers,
    private val providerSupport: ProviderSupport,
) {
    suspend fun browse(request: FilesBrowseRequest): FilesBrowseResponse {
        val (providerId, collection) = extractProviderAndCollectionFromPath(request.path)
        val comms = providers.prepareCommunication(providerId)


    }
}