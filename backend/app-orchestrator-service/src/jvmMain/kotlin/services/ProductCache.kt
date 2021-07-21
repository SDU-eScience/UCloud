package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.accounting.api.RetrieveAllFromProviderRequest
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.SimpleCache

class ProductCache(private val serviceClient: AuthenticatedClient) {
    val productsByProvider = SimpleCache<String, List<Product>> { provider ->
        Products.retrieveAllFromProvider
            .call(RetrieveAllFromProviderRequest(provider), serviceClient)
            .orThrow()
    }

    suspend inline fun <reified T : Product> find(
        provider: String,
        id: String,
        category: String
    ): T? {
        val machines = productsByProvider.get(provider) ?: return null
        return machines
            .filterIsInstance<T>()
            .find { it.name == id && it.category.name == category && it.category.provider == provider }
    }
}
