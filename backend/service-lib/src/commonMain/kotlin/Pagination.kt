package dk.sdu.cloud

import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.min

@Serializable
@UCloudApiOwnedBy(CoreTypes::class)
data class Page<out T>(
    val itemsInTotal: Int,
    val itemsPerPage: Int,
    val pageNumber: Int,
    val items: List<T>,
) {
    companion object {
        fun <T> forRequest(request: NormalizedPaginationRequest?, itemsInTotal: Int?, items: List<T>): Page<T> {
            val actualItemsInTotal = itemsInTotal ?: items.size
            return Page(actualItemsInTotal, request?.itemsPerPage ?: actualItemsInTotal, request?.page ?: 0, items)
        }
    }
}

val Page<*>.pagesInTotal: Int get() = ceil(itemsInTotal.toDouble() / itemsPerPage).toInt()

interface WithPaginationRequest {
    val itemsPerPage: Int?
    val page: Int?

    fun normalize() = NormalizedPaginationRequest(itemsPerPage, page)
}

fun WithPaginationRequest.normalizeWithFullReadEnabled(
    actor: Actor,
    privilegedOnly: Boolean = true,
): NormalizedPaginationRequest? {
    if (!privilegedOnly || actor == Actor.System ||
        (actor is Actor.User && actor.principal.role in Roles.PRIVILEGED)
    ) {
        if (itemsPerPage == PaginationRequest.FULL_READ) return null
    }
    return normalize()
}

@Serializable
@UCloudApiOwnedBy(CoreTypes::class)
data class PaginationRequest(
    override val itemsPerPage: Int? = null,
    override val page: Int? = null,
) : WithPaginationRequest {
    companion object {
        const val FULL_READ = -1
    }
}

class NormalizedPaginationRequest(
    itemsPerPage: Int?,
    page: Int?,
) {
    val itemsPerPage = when (itemsPerPage) {
        10, 25, 50, 100, 250 -> itemsPerPage
        else -> 50
    }

    val page = page?.takeIf { it >= 0 } ?: 0
}

fun <T, R> Page<T>.withNewItems(newItems: List<R>): Page<R> {
    return Page(
        itemsInTotal,
        itemsPerPage,
        pageNumber,
        newItems
    )
}

inline fun <T, R> Page<T>.mapItems(mapper: (T) -> R): Page<R> {
    val newItems = items.map(mapper)
    return Page(
        itemsInTotal,
        itemsPerPage,
        pageNumber,
        newItems
    )
}

inline fun <T, R : Any> Page<T>.mapItemsNotNull(mapper: (T) -> R?): Page<R> {
    val newItems = items.mapNotNull(mapper)
    return Page(
        itemsInTotal,
        itemsPerPage,
        pageNumber,
        newItems
    )
}

fun <T> List<T>.paginate(request: NormalizedPaginationRequest?): Page<T> {
    if (request == null) {
        return Page(size, size, 0, this)
    }

    val startIndex = request.itemsPerPage * request.page
    val items =
        if (startIndex > size) {
            emptyList()
        } else {
            subList(startIndex, min(startIndex + request.itemsPerPage, size))
        }

    return Page(size, request.itemsPerPage, request.page, items)
}

val NormalizedPaginationRequest.offset: Int
    get() = itemsPerPage * page


// Version 2 API
@UCloudApiDoc("""Represents a single 'page' of results
    
Every page contains the items from the current result set, along with information which allows the client to fetch
additional information.""")
@Serializable
@UCloudApiOwnedBy(CoreTypes::class)
data class PageV2<out T>(
    @UCloudApiDoc("The expected items per page, this is extracted directly from the request")
    val itemsPerPage: Int,
    @UCloudApiDoc("""The items returned in this page

NOTE: The amount of items might differ from `itemsPerPage`, even if there are more results. The only reliable way to
check if the end of results has been reached is by checking i `next == null`.""")
    val items: List<T>,

    @UCloudApiDoc("The token used to fetch additional items from this result set")
    val next: String?,
) : DocVisualizable {
    override fun visualize(): DocVisualization =
        DocVisualization.Card("PageV2", emptyList(), items.map { visualizeValue(it) })
}

fun <T> singlePageOf(vararg items: T): PageV2<T> {
    return PageV2(50, listOf(*items), null)
}

@Serializable
@UCloudApiOwnedBy(CoreTypes::class)
data class NormalizedPaginationRequestV2(
    val itemsPerPage: Int,
    val next: String?,
    val consistency: PaginationRequestV2Consistency,
    val itemsToSkip: Long?,
)

@Serializable
@UCloudApiOwnedBy(CoreTypes::class)
data class PaginationRequestV2(
    override val itemsPerPage: Int,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2

@UCloudApiDoc("""The base type for requesting paginated content.

Paginated content can be requested with one of the following `consistency` guarantees, this greatly changes the
semantics of the call:

| Consistency | Description |
|-------------|-------------|
| `PREFER` | Consistency is preferred but not required. An inconsistent snapshot might be returned. |
| `REQUIRE` | Consistency is required. A request will fail if consistency is no longer guaranteed. |

The `consistency` refers to if collecting all the results via the pagination API are _consistent_. We consider the
results to be consistent if it contains a complete view at some point in time. In practice this means that the results
must contain all the items, in the correct order and without duplicates.

If you use the `PREFER` consistency then you may receive in-complete results that might appear out-of-order and can
contain duplicate items. UCloud will still attempt to serve a snapshot which appears mostly consistent. This is helpful
for user-interfaces which do not strictly depend on consistency but would still prefer something which is mostly
consistent.

The results might become inconsistent if the client either takes too long, or a service instance goes down while
fetching the results. UCloud attempts to keep each `next` token alive for at least one minute before invalidating it.
This does not mean that a client must collect all results within a minute but rather that they must fetch the next page
within a minute of the last page. If this is not feasible and consistency is not required then `PREFER` should be used.

---

__📝 NOTE:__ Services are allowed to ignore extra criteria of the request if the `next` token is supplied. This is
needed in order to provide a consistent view of the results. Clients _should_ provide the same criterion as they
paginate through the results.

---
""")
interface WithPaginationRequestV2 {
    @UCloudApiDoc("Requested number of items per page. Supported values: 10, 25, 50, 100, 250.")
    val itemsPerPage: Int?

    @UCloudApiDoc("A token requesting the next page of items")
    val next: String?

    @UCloudApiDoc("Controls the consistency guarantees provided by the backend")
    val consistency: PaginationRequestV2Consistency?

    @UCloudApiDoc("Items to skip ahead")
    val itemsToSkip: Long?

    fun normalize(): NormalizedPaginationRequestV2 {
        if ((itemsToSkip ?: 0) < 0) throw RPCException("Invalid offset", HttpStatusCode.BadRequest)

        return NormalizedPaginationRequestV2(
            when (val itemsPerPage = itemsPerPage) {
                10, 25, 50, 100, 250 -> itemsPerPage
                else -> 50
            },
            next,
            consistency ?: PaginationRequestV2Consistency.PREFER,
            itemsToSkip
        )
    }
}

@Serializable
@UCloudApiOwnedBy(CoreTypes::class)
enum class PaginationRequestV2Consistency {
    @UCloudApiDoc("Consistency is preferred but not required. An inconsistent snapshot might be returned.")
    PREFER,

    @UCloudApiDoc("Consistency is required. A request will fail if consistency is no longer guaranteed.")
    @Deprecated("No longer supported")
    REQUIRE
}
