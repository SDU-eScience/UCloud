package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.api.FindProductRequest
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.app.orchestrator.api.ComputeProductSupportResolved
import dk.sdu.cloud.app.orchestrator.api.JobsRetrieveProductsResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.SimpleCache
import io.ktor.http.*

class ProviderSupportService(
    private val providers: Providers,
    private val serviceClient: AuthenticatedClient,
) {
    private val productCache = SimpleCache<ProductReference, Product.Compute>(
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
                if (product !is Product.Compute) {
                    log.warn("Did not receive a comptue related product: $ref")
                    null
                } else {
                    product
                }
            }
        }
    )

    private val providerProductCache = SimpleCache<String, List<ComputeProductSupportResolved>>(
        maxAge = 60_000 * 15,
        lookup = { provider ->
            runCatching {
                val comm = providers.prepareCommunication(provider)
                val providerResponse = comm.api.retrieveProducts.call(Unit, comm.client).orNull()
                if (providerResponse == null) {
                    log.warn("Did not receive a valid product response from: $provider")
                }

                providerResponse?.products?.mapNotNull {
                    val product = productCache.get(it.product)
                    if (product == null) {
                        null
                    } else {
                        ComputeProductSupportResolved(product, it.support)
                    }
                }?.sortedWith(
                    Comparator
                        .comparing<ComputeProductSupportResolved?, String?> { it.product.category.provider }
                        .thenComparing(Comparator.comparing { it.product.category.id })
                        .thenComparing(Comparator.comparing { it.product.priority })
                )
            }.getOrElse {
                log.debug(it.stackTraceToString())
                null
            }
        }
    )

    suspend fun retrieveProducts(providerIds: Collection<String>): JobsRetrieveProductsResponse {
        if (providerIds.isEmpty()) return JobsRetrieveProductsResponse(emptyMap())
        return JobsRetrieveProductsResponse(
            providerIds.map { provider ->
                provider to (providerProductCache.get(provider) ?: emptyList())
            }.toMap()
        )
    }

    suspend fun retrieveProductSupport(product: ProductReference): ComputeProductSupportResolved {
        return providerProductCache.get(product.provider)
            ?.find { it.product.id == product.id &&
                it.product.category.id == product.category &&
                it.product.category.provider == product.provider
            } ?: throw RPCException("Unknown product requested $product", HttpStatusCode.InternalServerError)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
