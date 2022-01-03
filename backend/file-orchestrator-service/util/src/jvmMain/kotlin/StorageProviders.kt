package dk.sdu.cloud.file.orchestrator.service

import dk.sdu.cloud.accounting.util.ProviderComms
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.provider.api.ProviderSpecification

data class StorageCommunication(
    override val client: AuthenticatedClient,
    override val wsClient: AuthenticatedClient,
    override val provider: ProviderSpecification,
    val filesApi: FilesProvider,
    val fileCollectionsApi: FileCollectionsProvider,
) : ProviderComms

typealias StorageProviders = Providers<StorageCommunication>
