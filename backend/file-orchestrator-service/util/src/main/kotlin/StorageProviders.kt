package dk.sdu.cloud.file.orchestrator.service

import dk.sdu.cloud.accounting.util.ProviderComms
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.provider.api.ProviderSpecification

data class StorageCommunication(
    override val client: AuthenticatedClient,
    override val wsClient: AuthenticatedClient,
    override val provider: ProviderSpecification,
    val filesApi: FilesProvider,
    val fileCollectionsApi: FileCollectionsProvider,
    val _auth: RefreshingJWTAuthenticator? = null,
    val _hostInfo: HostInfo? = null,
) : ProviderComms {
    val auth: RefreshingJWTAuthenticator get() = _auth!!
    val hostInfo: HostInfo get() = _hostInfo!!
}

typealias StorageProviders = Providers<StorageCommunication>
