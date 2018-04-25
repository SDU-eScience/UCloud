package dk.sdu.cloud.storage.services

import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.storage.api.Share
import dk.sdu.cloud.storage.api.ShareId
import dk.sdu.cloud.storage.api.ShareState
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min

interface ShareDAO {
    suspend fun find(user: String, shareId: ShareId): Share?

    suspend fun list(
        user: String,
        byState: ShareState? = null,
        paging: NormalizedPaginationRequest = NormalizedPaginationRequest(null, null)
    ): Page<Share>

    suspend fun create(
        user: String,
        share: Share
    ): ShareId

    suspend fun update(
        user: String,
        shareId: ShareId,
        share: Share
    )

    suspend fun updateState(
        user: String,
        shareId: ShareId,
        newState: ShareState
    )
}

fun <T> List<T>.paginate(request: NormalizedPaginationRequest): Page<T> {
    val startIndex = request.itemsPerPage * request.page
    val items =
        if (startIndex > size) emptyList()
        else subList(startIndex, min(startIndex + request.itemsPerPage, size))

    return Page(size, request.itemsPerPage, request.page, items)
}

class InMemoryShareDAO : ShareDAO {
    private val allShares: MutableList<Share> = ArrayList()
    private val lock = Any()

    override suspend fun find(user: String, shareId: ShareId): Share? {
        synchronized(lock) {
            return allShares.find { it.id == shareId }?.takeIf { it.owner == user || it.sharedWith == user }
        }
    }

    override suspend fun list(user: String, byState: ShareState?, paging: NormalizedPaginationRequest): Page<Share> {
        synchronized(lock) {
            return allShares
                .filter { it.owner == user || it.sharedWith == user }
                .filter { if (byState == null) true else it.state == byState }
                .paginate(paging)
        }
    }

    override suspend fun create(user: String, share: Share): ShareId {
        synchronized(lock) {
            val generatedId = UUID.randomUUID().toString()
            val rewritten = share.copy(
                owner = user,
                id = generatedId,
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis(),
                state = ShareState.REQUEST_SENT
            )

            allShares.add(rewritten)
            return generatedId
        }
    }

    override suspend fun update(user: String, shareId: ShareId, share: Share) {
        synchronized(lock) {
            val index = allShares
                .indexOfFirst { it.id == shareId }
                .takeIf { it != -1 } ?: throw ShareException.NotFound()

            allShares[index].takeIf { it.owner == user } ?: throw ShareException.NotFound()
            allShares[index] = share
        }
    }

    override suspend fun updateState(user: String, shareId: ShareId, newState: ShareState) {
        synchronized(lock) {
            val index = allShares
                .indexOfFirst { it.id == shareId }
                .takeIf { it != -1 } ?: throw ShareException.NotFound()

            val existingShare = allShares[index].takeIf { it.owner == user } ?: throw ShareException.NotFound()

            allShares[index] = existingShare.copy(state = newState)
        }
    }
}