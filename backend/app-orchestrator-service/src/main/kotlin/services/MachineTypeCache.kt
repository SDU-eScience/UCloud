package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.accounting.api.RetrieveAllFromProviderRequest
import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.SimpleCache

class MachineTypeCache(private val serviceClient: AuthenticatedClient) {
    val machines = SimpleCache<Unit, List<Product.Compute>> {
        Products.retrieveAllFromProvider
            .call(RetrieveAllFromProviderRequest(UCLOUD_PROVIDER), serviceClient)
            .orThrow()
            .filterIsInstance<Product.Compute>()
    }

    suspend fun find(name: String): Product.Compute? {
        val machines = machines.get(Unit) ?: return null
        return machines.find { it.id == name }
    }

    suspend fun findDefault(): Product.Compute {
        // Machines should be ordered by their priority
        return machines.get(Unit)?.firstOrNull() ?: throw IllegalStateException("No default machine exists")
    }
}
