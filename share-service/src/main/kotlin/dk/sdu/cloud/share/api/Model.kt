package dk.sdu.cloud.share.api

import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.file.api.AccessRight

enum class ShareState {
    REQUEST_SENT,
    ACCEPTED,
    UPDATING,
    FAILURE
}

typealias ShareId = Long
typealias FindByShareId = FindByLongId

data class Share(
    val id: ShareId,
    val state: ShareState,

    val owner: String,
    val sharedWith: String,

    val path: String,
    val rights: Set<AccessRight>,

    val createdAt: Long,
    val modifiedAt: Long
)

data class SharesByPath(
    val path: String,
    val sharedBy: String,
    val sharedByMe: Boolean,
    val shares: List<MinimalShare>
)

data class MinimalShare(
    val id: ShareId,
    val sharedWith: String,
    val rights: Set<AccessRight>,
    val state: ShareState
)

