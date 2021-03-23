package dk.sdu.cloud.file.orchestrator.service

import dk.sdu.cloud.accounting.api.FindProductRequest
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.SimpleCache
import io.ktor.http.*

class ProviderSupport(
    private val providers: Providers,
    private val serviceClient: AuthenticatedClient,
) {
    private val productCache = SimpleCache<ProductReference, Product.Storage>(
        maxAge = 60_000 * 15,
        lookup = { ref ->
            val productResp = Products.findProduct.call(
                FindProductRequest(ref.provider, ref.category, ref.id),
                serviceClient
            )

            if (productResp is IngoingCallResponse.Error) {
                log.warn("Received an error while resolving product from provider: $ref $productResp")
                null
            } else {
                val product = productResp.orThrow()
                if (product !is Product.Storage) {
                    log.warn("Did not receive a comptue related product: $ref")
                    null
                } else {
                    product
                }
            }
        }
    )

    private val providerProductCache = SimpleCache<String, List<FSSupportResolved>>(
        maxAge = 60_000 * 15,
        lookup = { provider ->
            runCatching {
                val comm = providers.prepareCommunication(provider)
                val providerResponse = comm.fileCollectionsApi.retrieveManifest.call(Unit, comm.client).orNull()
                if (providerResponse == null) {
                    log.warn("Did not receive a valid product response from: $provider")
                }

                providerResponse?.support?.mapNotNull {
                    val product = productCache.get(it.product)
                    if (product == null) {
                        null
                    } else {
                        FSSupportResolved(product, it)
                    }
                }
            }.getOrElse {
                log.debug(it.stackTraceToString())
                null
            }
        }
    )

    suspend fun retrieveProducts(providerIds: Collection<String>): Map<String, List<FSSupportResolved>> {
        if (providerIds.isEmpty()) return emptyMap()
        return providerIds.map { provider ->
            provider to (providerProductCache.get(provider) ?: emptyList())
        }.toMap()
    }

    suspend fun retrieveProductSupport(product: ProductReference): FSSupportResolved {
        return providerProductCache.get(product.provider)
            ?.find {
                it.product.id == product.id &&
                    it.product.category.id == product.category &&
                    it.product.category.provider == product.provider
            } ?: throw RPCException("Unknown product requested $product", HttpStatusCode.InternalServerError)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
