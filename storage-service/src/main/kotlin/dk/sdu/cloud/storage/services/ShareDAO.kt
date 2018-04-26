package dk.sdu.cloud.storage.services

import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.storage.api.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min

interface ShareDAO {
    suspend fun find(user: String, shareId: ShareId): Share?

    suspend fun list(
        user: String,
        paging: NormalizedPaginationRequest = NormalizedPaginationRequest(null, null)
    ): Page<SharesByPath>

    suspend fun findSharesForPath(
        user: String,
        path: String
    ): SharesByPath

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

    suspend fun deleteShare(user: String, shareId: ShareId)
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

    override suspend fun findSharesForPath(user: String, path: String): SharesByPath {
        synchronized(lock) {
            return allShares
                .filter { it.owner == user || it.sharedWith == user }
                .filter { it.path == path }
                .let {
                    if (it.isEmpty()) throw ShareException.NotFound()

                    val owner = it.first().owner
                    val sharedByMe = owner == user
                    SharesByPath(path, owner, sharedByMe, it.map { it.minimalize() })
                }
        }
    }

    override suspend fun list(user: String, paging: NormalizedPaginationRequest): Page<SharesByPath> {
        synchronized(lock) {
            return allShares
                .filter { it.owner == user || it.sharedWith == user }
                .groupBy { it.path }
                .map {
                    assert(it.value.isNotEmpty())

                    val owner = it.value.first().owner
                    val sharedByMe = owner == user
                    SharesByPath(it.key, owner, sharedByMe, it.value.map { it.minimalize() })
                }
                .paginate(paging)
        }
    }

    override suspend fun create(user: String, share: Share): ShareId {
        synchronized(lock) {
            val hasDuplicate = allShares.any { it.path == share.path && it.sharedWith == share.sharedWith }
            if (hasDuplicate) throw ShareException.DuplicateException()

            val generatedId = UUID.randomUUID().toString()
            allShares.add(share.copy(id = generatedId))
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

            val existingShare = allShares[index]

            allShares[index] = existingShare.copy(state = newState)
        }
    }

    override suspend fun deleteShare(user: String, shareId: ShareId) {
        synchronized(lock) {
            val success = allShares.removeIf {
                if (it.id == shareId) {
                    if (it.owner == user || it.sharedWith == user) {
                        true
                    } else {
                        throw ShareException.NotAllowed()
                    }
                } else {
                    false
                }
            }

            if (!success) throw ShareException.NotFound()
        }
    }

}