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
    val machines = SimpleCache<String, List<Product.Compute>> { provider ->
        Products.retrieveAllFromProvider
            .call(RetrieveAllFromProviderRequest(provider), serviceClient)
            .orThrow()
            .filterIsInstance<Product.Compute>()
    }

    suspend fun find(provider: String, id: String, category: String): Product.Compute? {
        val machines = machines.get(provider) ?: return null
        return machines.find { it.id == id && it.category.id == category && it.category.provider == provider }
    }
}
