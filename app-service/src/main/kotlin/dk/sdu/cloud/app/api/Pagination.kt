package dk.sdu.cloud.app.api

data class Page<out T>(
    val itemsInTotal: Int,
    val itemsPerPage: Int,

    val pageNumber: Int,
    val items: List<T>
) {
    val pagesInTotal: Int = itemsInTotal / itemsPerPage
}

data class PaginationRequest(
    val itemsPerPage: Int? = null,
    val page: Int? = null
) {
    fun normalize() = NormalizedPaginationRequest(itemsPerPage, page)
}

class NormalizedPaginationRequest(
    itemsPerPage: Int?,
    page: Int?
) {
    val itemsPerPage = when (itemsPerPage) {
        10, 25, 50, 100-> itemsPerPage
        else -> 10
    }

    val page = page ?: 0
}
