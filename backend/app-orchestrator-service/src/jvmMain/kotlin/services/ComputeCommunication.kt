package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.util.ProviderComms
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.provider.api.*

data class ComputeCommunication(
    val api: JobsProvider,
    override val client: AuthenticatedClient,
    override val wsClient: AuthenticatedClient,
    val ingressApi: IngressProvider,
    val licenseApi: LicenseProvider,
    val networkApi: NetworkIPProvider,
    override val provider: ProviderSpecification,
) : ProviderComms
