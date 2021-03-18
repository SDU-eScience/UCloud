package dk.sdu.cloud.share.api

import dk.sdu.cloud.file.api.AccessRight
import kotlinx.serialization.Serializable

@Serializable
enum class ShareState {
    REQUEST_SENT,
    UPDATING,
    ACCEPTED
}

@Serializable
data class Share(
    val state: ShareState,

    val owner: String,
    val sharedWith: String,

    val path: String,
    val rights: Set<AccessRight>,

    val createdAt: Long,
    val modifiedAt: Long
)

@Serializable
data class SharesByPath(
    val path: String,
    val sharedBy: String,
    val sharedByMe: Boolean,
    val shares: List<MinimalShare>
)

@Serializable
data class MinimalShare(
    val sharedWith: String,
    val rights: Set<AccessRight>,
    val state: ShareState
)

