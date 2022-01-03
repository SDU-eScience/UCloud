package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.PaginationRequestV2Consistency
import dk.sdu.cloud.WithPaginationRequestV2
import kotlinx.serialization.Serializable

@Serializable
data class FindBySupportedFileExtension(
    val files: List<String>,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2
