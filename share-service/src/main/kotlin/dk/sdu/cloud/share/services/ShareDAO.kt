package dk.sdu.cloud.share.services

import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.share.api.Share
import dk.sdu.cloud.share.api.ShareState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

data class ShareRelationQuery(val username: String, val sharedByMe: Boolean)

/**
 * Provides an interface to the [Share] data layer
 *
 * All methods in this interface will throw [ShareException] when appropriate.
 *
 * Authorization is performed by this interface, however, only at the "share" level. Any file system restrictions are
 * expected by the layers above. For example: Clients should do authorization to make sure the user can create a share
 * for a given file.
 */
interface ShareDAO<Session> {
    suspend fun create(
        session: Session,
        owner: String,
        sharedWith: String,
        path: String,
        initialRights: Set<AccessRight>,
        fileId: String,
        ownerToken: String
    ): Long

    suspend fun findById(
        session: Session,
        auth: AuthRequirements,
        shareId: Long
    ): InternalShare

    suspend fun findAllByPath(
        session: Session,
        auth: AuthRequirements,
        path: String
    ): List<InternalShare>

    suspend fun findAllByFileId(
        session: Session,
        auth: AuthRequirements,
        fileId: String
    ): List<InternalShare>

    suspend fun list(
        session: Session,
        auth: AuthRequirements,
        shareRelation: ShareRelationQuery,
        paging: NormalizedPaginationRequest = NormalizedPaginationRequest(null, null)
    ): ListSharesResponse

    suspend fun listSharedToMe(
        session: Session,
        user: String,
        paging: NormalizedPaginationRequest
    ): Page<InternalShare>

    suspend fun updateShare(
        session: Session,
        auth: AuthRequirements,
        shareId: Long,
        recipientToken: String? = null,
        state: ShareState? = null,
        rights: Set<AccessRight>? = null,
        path: String? = null,
        ownerToken: String? = null
    )

    suspend fun deleteShare(
        session: Session,
        auth: AuthRequirements,
        shareId: Long
    )

    suspend fun onFilesMoved(
        session: Session,
        events: List<StorageEvent.Moved>
    )

    suspend fun findAllByFileIds(
        session: Session,
        fileIds: List<String>
    ): List<InternalShare>

    suspend fun deleteAllByShareId(
        session: Session,
        shareIds: List<Long>
    )

    suspend fun listAll(scope: CoroutineScope, session: Session): ReceiveChannel<InternalShare>
}

data class ListSharesResponse(
    val allSharesForPage: List<InternalShare>,
    val groupCount: Int
)

/**
 * Authorization requirements for a Share action.
 *
 * @param user The username of a user. A value of null indicates that this action is being performed by the service. It
 *             will as a result always pass validation.
 *
 * @param requireRole The role to require of the [user].
 */
data class AuthRequirements(
    val user: String? = null,
    val requireRole: ShareRole? = null
)

enum class ShareRole {
    PARTICIPANT,
    OWNER,
    RECIPIENT
}
