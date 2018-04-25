package dk.sdu.cloud.storage.services

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.RESTHandler
import dk.sdu.cloud.storage.api.Share
import dk.sdu.cloud.storage.api.ShareId
import dk.sdu.cloud.storage.api.ShareState
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.experimental.launch
import org.slf4j.LoggerFactory

sealed class ShareException(override val message: String) : RuntimeException(message) {
    class NotFound : ShareException("Not found")
    class NotAllowed : ShareException("Not allowed")
}

suspend inline fun RESTHandler<*, *, CommonErrorMessage>.tryWithShareService(body: () -> Unit) {
    try {
        body()
    } catch (ex: ShareException) {
        error(CommonErrorMessage(ex.message), HttpStatusCode.InternalServerError)
    }
}

class ShareService(
    private val source: ShareDAO,
    private val fileSystemService: FileSystemService
) {
    suspend fun list(
        user: String,
        byState: ShareState? = null,
        paging: NormalizedPaginationRequest = NormalizedPaginationRequest(null, null)
    ): Page<Share> {
        return source.list(user, byState, paging)
    }

    suspend fun create(
        user: String,
        share: Share,
        cloud: AuthenticatedCloud
    ): ShareId {
        // Check if user is allowed to share this file
        val stat = fileSystemService.stat(user, share.path) ?: throw ShareException.NotFound()
        if (stat.ownerName != user) {
            throw ShareException.NotAllowed()
        }

        // TODO Need to verify sharedWith exists!

        val result = source.create(user, share)
        launch {
            NotificationDescriptions.create.call(
                CreateNotification(
                    user = share.sharedWith,
                    notification = Notification(
                        type = "SHARE_REQUEST",
                        message = "$user has shared a file with you",

                        meta = mapOf(
                            "shareId" to result,
                            "path" to share.path,
                            "rights" to share.rights
                        )
                    )
                ),
                cloud
            )
        }

        return result
    }


    suspend fun update(
        user: String,
        shareId: ShareId,
        share: Share
    ) {
        val existingShare = source.find(user, shareId) ?: throw ShareException.NotFound()

        val newShare = share.copy(
            modifiedAt = System.currentTimeMillis(),
            owner = user,

            // We don't allow for all attributes to change
            id = existingShare.id,
            sharedWith = existingShare.sharedWith,
            state = existingShare.state,
            createdAt = existingShare.createdAt
        )

        source.update(user, shareId, newShare)
    }

    suspend fun updateState(
        user: String,
        shareId: ShareId,
        newState: ShareState
    ) {
        val existingShare = source.find(user, shareId) ?: throw ShareException.NotFound()

        when (user) {
            existingShare.sharedWith -> when (newState) {
                ShareState.REJECTED, ShareState.ACCEPTED -> {
                    // This is okay
                }

                else -> throw ShareException.NotAllowed()
            }

            existingShare.owner -> when (newState) {
                ShareState.REVOKED -> {
                    // This is okay
                }

                else -> throw ShareException.NotAllowed()
            }

            else -> {
                log.warn("ShareDAO returned a result but user is not owner or being sharedWith! $existingShare $user")
                throw IllegalStateException()
            }
        }

        source.updateState(user, shareId, newState)

        // TODO Actually update FS (This might take time, maybe do processing later? How do we ensure atomicity?)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ShareService::class.java)
    }
}