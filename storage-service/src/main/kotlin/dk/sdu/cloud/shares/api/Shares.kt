package dk.sdu.cloud.shares.api

import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.files.api.AccessRight

enum class ShareState {
    REQUEST_SENT,
    ACCEPTED
}

typealias ShareId = Long
typealias FindByShareId = FindByLongId

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