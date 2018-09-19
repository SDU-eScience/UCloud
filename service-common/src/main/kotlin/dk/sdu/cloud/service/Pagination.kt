package dk.sdu.cloud.service

import kotlin.math.min
import kotlin.math.ceil

data class Page<out T>(
    val itemsInTotal: Int,
    val itemsPerPage: Int,

    val pageNumber: Int,
    val items: List<T>
) {
    val pagesInTotal: Int = ceil(itemsInTotal.toDouble() / itemsPerPage).toInt()
}

interface WithPaginationRequest {
    val itemsPerPage: Int?
    val page: Int?

    fun normalize() = NormalizedPaginationRequest(itemsPerPage, page)
}

data class PaginationRequest(
    override val itemsPerPage: Int? = null,
    override val page: Int? = null
): WithPaginationRequest

class NormalizedPaginationRequest(
    itemsPerPage: Int?,
    page: Int?
) {
    val itemsPerPage = when (itemsPerPage) {
        10, 25, 50, 100 -> itemsPerPage
        else -> 10
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

fun <T> List<T>.paginate(request: NormalizedPaginationRequest): Page<T> {
    val startIndex = request.itemsPerPage * request.page
    val items =
        if (startIndex > size) emptyList()
        else subList(startIndex, min(startIndex + request.itemsPerPage, size))

    return Page(size, request.itemsPerPage, request.page, items)
}