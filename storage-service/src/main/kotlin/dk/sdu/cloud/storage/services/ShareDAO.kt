package dk.sdu.cloud.storage.services

import dk.sdu.cloud.files.api.AccessRight
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.shares.api.Share
import dk.sdu.cloud.shares.api.ShareState
import dk.sdu.cloud.shares.api.SharesByPath

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
    fun find(
        session: Session,
        user: String,
        shareId: Long
    ): Share

    fun list(
        session: Session,
        user: String,
        paging: NormalizedPaginationRequest = NormalizedPaginationRequest(null, null)
    ): Page<SharesByPath>

    fun findSharesForPath(
        session: Session,
        user: String,
        path: String
    ): SharesByPath

    fun create(
        session: Session,
        user: String,
        share: Share
    ): Long

    fun updateState(
        session: Session,
        user: String,
        shareId: Long,
        newState: ShareState
    ): Share

    fun updateRights(
        session: Session,
        user: String,
        shareId: Long,
        rights: Set<AccessRight>
    ): Share

    fun deleteShare(
        session: Session,
        user: String,
        shareId: Long
    ): Share
}

