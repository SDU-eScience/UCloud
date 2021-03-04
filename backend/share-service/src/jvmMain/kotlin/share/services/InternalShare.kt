package dk.sdu.cloud.share.services

import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.share.api.ShareState

data class InternalShare(
    val sharedBy: String,
    val sharedWith: String,
    val state: ShareState,
    val rights: Set<AccessRight>,
    val ownerToken: String,
    val recipientToken: String?,
    val createdAt: Long = Time.now(),
    val modifiedAt: Long = Time.now()
)

const val METADATA_TYPE_SHARES = "share"
