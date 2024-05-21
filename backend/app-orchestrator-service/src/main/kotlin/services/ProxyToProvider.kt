package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.accounting.util.IIdCardService
import dk.sdu.cloud.accounting.util.ProductCache
import dk.sdu.cloud.app.orchestrator.api.Jobs
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.provider.api.Permission
import java.util.ArrayList

class ProxyToProvider<Internal, External>(
    val idCards: IIdCardService,
    val documents: ResourceStore<Internal>,
    val mapper: DocMapper<Internal, External>,
    val productCache: ProductCache,
    val providers: ProviderCommunications,
) {
    data class Entry<External>(
        val items: ArrayList<External> = ArrayList(),
        val products: ArrayList<ProductReference> = ArrayList(),
    )

    suspend fun <T> send(
        actorAndProject: ActorAndProject,
        ids: List<Long>,
        permission: Permission,
        actionDescription: String,
        featureValidation: (suspend (resource: External, support: ProductSupport) -> Unit)?,
        fn: suspend (providerId: String, resources: List<External>) -> T
    ) {
        val card = idCards.fetchIdCard(actorAndProject)
        val grouped = HashMap<String, Entry<External>>()
        ResourceOutputPool.withInstance { pool ->
            if (pool.size < ids.size) {
                throw RPCException("Request is too large!", HttpStatusCode.PayloadTooLarge)
            }

            val count = documents.retrieveBulk(card, ids.toLongArray(), pool, permission)

            if (count != ids.size) {
                throw RPCException(
                    "Could not find the items that you requested. Do you have permission to perform this action?",
                    HttpStatusCode.NotFound
                )
            }

            for (idx in 0 until count) {
                val doc = pool[idx]
                val productRef = productCache.productIdToReference(doc.product) ?: error("unknown product")
                val entry = grouped.getOrPut(productRef.provider) { Entry() }
                entry.items.add(mapper.map(card, doc))
                entry.products.add(productRef)
            }
        }

        if (featureValidation != null) {
            for ((_, entry) in grouped) {
                providers.requireSupport(Jobs, entry.products.toSet(), actionDescription) { support ->
                    for ((index, product) in entry.products.withIndex()) {
                        if (product != support.product) continue
                        val item = entry.items[index]
                        featureValidation(item, support)
                    }
                }
            }
        }

        for ((provider, entry) in grouped) {
            fn(provider, entry.items)
        }
    }
}
