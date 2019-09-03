package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.service.WithPaginationRequest

data class FindByNameAndPagination(
    val name: String,
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest
