package dk.sdu.cloud.service

data class Page<out T>(
    val itemsInTotal: Int,
    val itemsPerPage: Int,

    val pageNumber: Int,
    val items: List<T>
) {
    val pagesInTotal: Int = itemsInTotal / itemsPerPage
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
