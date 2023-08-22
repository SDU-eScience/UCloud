package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.WalletOwner
import dk.sdu.cloud.accounting.api.providers.ResourceChargeCredits
import dk.sdu.cloud.accounting.api.providers.ResourceChargeCreditsResponse
import dk.sdu.cloud.accounting.api.providers.WithBrowseRequest
import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.accounting.util.Payment
import dk.sdu.cloud.accounting.util.PaymentService
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.provider.api.Permission
import java.util.Comparator
import kotlin.math.min

sealed class BrowseStrategy {
    class Default() : BrowseStrategy()
    data class Index(val index: Collection<Long>) : BrowseStrategy()
    data class Custom<T>(
        val extractor: DocKeyExtractor<InternalJobState, T>,
        val comparator: Comparator<T>
    ) : BrowseStrategy()
}

suspend fun <Internal, Result> ResourceStore<Internal>.browseWithStrategy(
    mapper: DocMapper<Internal, Result>,
    card: IdCard,
    request: WithBrowseRequest<*>,
    filterFunction: FilterFunction<Internal>,
    strategy: BrowseStrategy? = null,
): PageV2<Result> {
    val pagination = request.normalize()

    ResourceOutputPool.withInstance { pool ->
        val result = when (strategy) {
            is BrowseStrategy.Index -> {
                paginateUsingIdsFromCustomIndex(card, pool, strategy.index, filterFunction,
                    pagination.itemsPerPage, pagination.next,)
            }

            is BrowseStrategy.Custom<*> -> {
                @Suppress("UNCHECKED_CAST")
                browseWithSort(
                    card,
                    pool,
                    pagination.next,
                    request.flags,
                    strategy.extractor as DocKeyExtractor<Internal, Any?>,
                    strategy.comparator as Comparator<Any?>,
                    request.sortDirection,
                    pagination.itemsPerPage,
                    filterFunction
                )
            }

            else -> {
                browse(
                    card,
                    pool,
                    request.next,
                    request.flags,
                    outputBufferLimit = pagination.itemsPerPage,
                    additionalFilters = filterFunction,
                    sortedBy = request.sortBy,
                    sortDirection = request.sortDirection
                )
            }
        }

        val page = ArrayList<Result>(result.count)
        for (idx in 0 until min(pagination.itemsPerPage, result.count)) {
            page.add(mapper.map(card, pool[idx]))
        }

        check(result.next == null || result.next != request.next) // no loops are allowed
        return PageV2(pagination.itemsPerPage, page, result.next)
    }
}

/**
 * Performs an optional comparison between two values.
 *
 * If the left hand side is null then the result is true, otherwise the result is true if the two values equal each
 * other.
 *
 * This function is intended to be used in filter functions, for example:
 *
 * ```kotlin
 * // ...
 * if (!optcmp(flags.filterState, job.state)) return false
 * if (!optcmp(flags.filterName, job.name)) return false
 * if (!optcmp(flags.filterApplication, job.application)) return false
 * return true
 * // ...
 * ```
 */
fun <T : Any> optcmp(left: T?, right: T): Boolean {
    if (left == null) return true
    return left == right
}


// TODO(Dan): As we expand this to multiple resources, then we probably need to extract this into some sort of
//  utility method.
suspend fun PaymentService.chargeOrCheckCredits(
    idCards: IIdCardService,
    productCache: ProductCache,
    documents: ResourceStore<*>,

    actorAndProject: ActorAndProject,
    request: BulkRequest<ResourceChargeCredits>,
    checkOnly: Boolean
): ResourceChargeCreditsResponse {
    val ids = request.items.mapNotNull { it.id.toLongOrNull() }.toSet().toLongArray()
    val card = idCards.fetchIdCard(actorAndProject)

    val paymentRequests = ArrayList<Payment>()
    val paymentResourceIds = ArrayList<String>()

    @Suppress("UNCHECKED_CAST")
    (documents as ResourceStore<Any?>)

    ResourceOutputPool.withInstance { pool ->
        val count = documents.retrieveBulk(card, ids, pool, Permission.PROVIDER)
        for (i in 0 until count) {
            val doc = pool[i]
            for (reqItem in request.items) {
                if (reqItem.id.toLongOrNull() != doc.id) continue

                val project =
                    if (doc.project == 0) null
                    else idCards.lookupPid(doc.project)

                val createdBy = idCards.lookupUid(doc.createdBy)
                    ?: throw RPCException(
                        "Could not lookup user: ${doc.createdBy}",
                        HttpStatusCode.InternalServerError
                    )

                val resolvedProduct = productCache.productIdToProduct(doc.product)
                    ?: throw RPCException(
                        "Could not lookup product: ${doc.product}",
                        HttpStatusCode.InternalServerError
                    )

                if (resolvedProduct.freeToUse) continue

                paymentRequests.add(
                    Payment(
                        reqItem.chargeId,
                        reqItem.periods,
                        reqItem.units,
                        resolvedProduct.pricePerUnit,
                        reqItem.id,
                        reqItem.performedBy ?: createdBy,
                        if (project != null) {
                            WalletOwner.Project(project)
                        } else {
                            WalletOwner.User(createdBy)
                        },
                        resolvedProduct.toReference(),
                        reqItem.description,
                        reqItem.chargeId
                    )
                )

                paymentResourceIds.add(doc.id.toString())
            }
        }
    }

    val chargeResult =
        if (checkOnly) creditCheckForPayments(paymentRequests)
        else charge(paymentRequests)

    val insufficient = chargeResult.mapIndexedNotNull { index, result ->
        when (result) {
            PaymentService.ChargeResult.Charged -> null
            PaymentService.ChargeResult.Duplicate -> null
            PaymentService.ChargeResult.InsufficientFunds -> FindByStringId(paymentResourceIds[index])
        }
    }

    return ResourceChargeCreditsResponse(insufficient, emptyList())
}
