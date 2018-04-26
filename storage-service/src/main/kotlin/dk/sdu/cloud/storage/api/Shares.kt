package dk.sdu.cloud.storage.api

import dk.sdu.cloud.FindByStringId

enum class ShareState {
    REQUEST_SENT,
    ACCEPTED
}

typealias ShareId = String
typealias FindByShareId = FindByStringId

data class Share(
    val owner: String,
    val sharedWith: String,

    val path: String,
    val rights: Set<AccessRight>,

    val createdAt: Long? = null,
    val modifiedAt: Long? = null,

    val state: ShareState = ShareState.REQUEST_SENT,

    val id: ShareId? = null
)