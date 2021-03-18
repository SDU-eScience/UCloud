package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.WithPaginationRequest
import kotlinx.serialization.Serializable

@Serializable
data class FindByNameAndPagination(
    val appName: String,
    override val itemsPerPage: Int? = null,
    override val page: Int? = null,
) : WithPaginationRequest
