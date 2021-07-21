package dk.sdu.cloud.accounting.util

import dk.sdu.cloud.accounting.api.FindProductRequest
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.accounting.api.providers.ResolvedSupport
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.SimpleCache
import io.ktor.http.*

class ProviderSupport<Communication : ProviderComms, P : Product, Support : ProductSupport>(
    private val providers: Providers<Communication>,
    private val serviceClient: AuthenticatedClient,
    private val fetchSupport: suspend (comms: Communication) -> List<Support>
) {
    @Suppress("UNCHECKED_CAST")
    private val productCache = SimpleCache<ProductReference, P>(
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
                product as P
            }
        }
    )

    private val providerProductCache = SimpleCache<String, List<ResolvedSupport<P, Support>>>(
        maxAge = 60_000 * 15,
        lookup = { provider ->
            runCatching {
                val comm = providers.prepareCommunication(provider)
                val providerResponse = fetchSupport(comm)

                providerResponse.mapNotNull {
                    val product = productCache.get(it.product)
                    if (product == null) {
                        null
                    } else {
                        ResolvedSupport(product, it)
                    }
                }
            }.getOrElse {
                log.debug(it.stackTraceToString())
                null
            }
        }
    )

    suspend fun retrieveProducts(providerIds: Collection<String>): Map<String, List<ResolvedSupport<P, Support>>> {
        if (providerIds.isEmpty()) return emptyMap()
        return providerIds.map { provider ->
            provider to (providerProductCache.get(provider) ?: emptyList())
        }.toMap()
    }

    suspend fun retrieveProductSupport(product: ProductReference): ResolvedSupport<P, Support> {
        return providerProductCache.get(product.provider)
            ?.find {
                it.product.name == product.id &&
                    it.product.category.name == product.category &&
                    it.product.category.provider == product.provider
            } ?: throw RPCException("Unknown product requested $product", HttpStatusCode.InternalServerError)
    }

    suspend fun retrieveProviderSupport(provider: String): List<ResolvedSupport<P, Support>> {
        return providerProductCache.get(provider)
            ?: throw RPCException("Unknown provider: $provider", HttpStatusCode.BadRequest)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
