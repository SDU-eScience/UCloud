package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.WithPaginationRequest
import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.calls.UCloudApiOwnedBy
import kotlinx.serialization.Serializable

@Serializable
@UCloudApiOwnedBy(ToolStore::class)
@UCloudApiDoc("Request type to find a Page of resources defined by a name")
data class FindByNameAndPagination(
    val appName: String,
    override val itemsPerPage: Int? = null,
    override val page: Int? = null,
) : WithPaginationRequest
