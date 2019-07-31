package dk.sdu.cloud.share.services

import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.share.api.ShareId
import dk.sdu.cloud.share.api.ShareState

data class InternalShare(
    val id: ShareId,
    val owner: String,

    val sharedWith: String,
    val state: ShareState,

    val path: String,
    val rights: Set<AccessRight>,

    val fileId: String,
    val ownerToken: String,
    val recipientToken: String?,

    val createdAt: Long? = null,
    val modifiedAt: Long? = null
)
