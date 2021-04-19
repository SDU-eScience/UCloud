package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.PaginationRequestV2Consistency
import dk.sdu.cloud.WithPaginationRequestV2
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@Serializable
data class Share(
    val path: String,
    val sharedBy: String,
    val sharedWith: String,
    val approved: Boolean,
)

@Serializable
data class SharesRetrieveRequest(val path: String)
typealias SharesRetrieveResponse = Share

@Serializable
data class SharesBrowseRequest(
    val sharedByMe: Boolean,
    val filterPath: String? = null,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2
typealias SharesBrowseResponse = PageV2<Share>

typealias SharesApproveRequest = BulkRequest<SharesApproveRequestItem>

@Serializable
data class SharesApproveRequestItem(val path: String)
typealias SharesApproveResponse = Unit

typealias SharesDeleteRequest = BulkRequest<SharesDeleteRequestItem>

@Serializable
data class SharesDeleteRequestItem(val path: String, val sharedWith: String? = null)
typealias SharesDeleteResponse = Unit

typealias SharesCreateRequest = BulkRequest<SharesCreateRequestItem>

@Serializable
data class SharesCreateRequestItem(val path: String, val sharedWith: String)
typealias SharesCreateResponse = Unit

object Shares : CallDescriptionContainer("shares") {
    const val baseContext = "/api/files/shares"

    val retrieve = call<SharesRetrieveRequest, SharesRetrieveResponse, CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext)
    }

    val browse = call<SharesBrowseRequest, SharesBrowseResponse, CommonErrorMessage>("browse") {
        httpBrowse(baseContext)
    }

    val create = call<SharesCreateRequest, SharesCreateResponse, CommonErrorMessage>("create") {
        httpCreate(baseContext)
    }

    val approve = call<SharesApproveRequest, SharesApproveResponse, CommonErrorMessage>("approve") {
        httpUpdate(baseContext, "approve")
    }

    val delete = call<SharesDeleteRequest, SharesDeleteResponse, CommonErrorMessage>("delete") {
        httpDelete(baseContext)
    }
}
