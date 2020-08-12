package dk.sdu.cloud.service

import dk.sdu.cloud.Roles
import kotlin.math.ceil
import kotlin.math.min

data class Page<out T>(
    val itemsInTotal: Int,
    val itemsPerPage: Int,

    val pageNumber: Int,
    val items: List<T>
) {
    val pagesInTotal: Int = ceil(itemsInTotal.toDouble() / itemsPerPage).toInt()

    companion object {
        fun <T> forRequest(request: NormalizedPaginationRequest?, itemsInTotal: Int?, items: List<T>): Page<T> {
            val actualItemsInTotal = itemsInTotal ?: items.size
            return Page(actualItemsInTotal, request?.itemsPerPage ?: actualItemsInTotal, request?.page ?: 0, items)
        }
    }
}

interface WithPaginationRequest {
    val itemsPerPage: Int?
    val page: Int?

    fun normalize() = NormalizedPaginationRequest(itemsPerPage, page)
}

fun WithPaginationRequest.normalizeWithFullReadEnabled(
    actor: Actor,
    privilegedOnly: Boolean = true
): NormalizedPaginationRequest? {
    if (!privilegedOnly || actor == Actor.System ||
        (actor is Actor.User && actor.principal.role in Roles.PRIVILEGED)
    ) {
        if (itemsPerPage == PaginationRequest.FULL_READ) return null
    }
    return normalize()
}

data class PaginationRequest(
    override val itemsPerPage: Int? = null,
    override val page: Int? = null
) : WithPaginationRequest {
    companion object {
        const val FULL_READ = -1
    }
}

class NormalizedPaginationRequest(
    itemsPerPage: Int?,
    page: Int?
) {
    val itemsPerPage = when (itemsPerPage) {
        10, 25, 50, 100, 250 -> itemsPerPage
        else -> 50
    }

    val page = page?.takeIf { it >= 0 } ?: 0
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
