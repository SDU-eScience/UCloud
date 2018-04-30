package dk.sdu.cloud.storage.services

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.RESTHandler
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.storage.api.*
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.experimental.launch
import org.slf4j.LoggerFactory

sealed class ShareException(override val message: String) : RuntimeException(message) {
    class NotFound : ShareException("Not found")
    class NotAllowed : ShareException("Not allowed")
    class DuplicateException : ShareException("Already exists")
    class PermissionException : ShareException("Not allowed")
    class BadRequest(why: String) : ShareException("Bad request: $why")
}

private val log = LoggerFactory.getLogger(ShareService::class.java)

suspend fun RESTHandler<*, *, CommonErrorMessage>.handleShareException(ex: Exception) {
    when (ex) {
        is ShareException -> {
            @Suppress("UNUSED_VARIABLE")
            val ignored = when (ex) {
                is ShareException.NotFound -> {
                    error(CommonErrorMessage(ex.message), HttpStatusCode.NotFound)
                }

                is ShareException.NotAllowed -> {
                    error(CommonErrorMessage(ex.message), HttpStatusCode.Forbidden)
                }

                is ShareException.DuplicateException -> {
                    error(CommonErrorMessage(ex.message), HttpStatusCode.Conflict)
                }
                is ShareException.PermissionException -> {
                    error(CommonErrorMessage(ex.message), HttpStatusCode.Forbidden)
                }
                is ShareException.BadRequest -> {
                    error(CommonErrorMessage(ex.message), HttpStatusCode.BadRequest)
                }
            }
        }

        is IllegalArgumentException -> {
            log.debug("Bad request:")
            log.debug(ex.stackTraceToString())
            error(CommonErrorMessage("Bad request"), HttpStatusCode.BadRequest)
        }

        else -> {
            log.warn("Unknown exception caught in share service!")
            log.warn(ex.stackTraceToString())
            error(CommonErrorMessage("Internal Server Error"), HttpStatusCode.InternalServerError)
        }
    }
}

suspend inline fun RESTHandler<*, *, CommonErrorMessage>.tryWithShareService(body: () -> Unit) {
    try {
        body()
    } catch (ex: Exception) {
        handleShareException(ex)
    }
}

class ShareService(
    private val source: ShareDAO,
    private val fs: FileSystemService
) {
    suspend fun list(
        user: String,
        paging: NormalizedPaginationRequest = NormalizedPaginationRequest(null, null)
    ): Page<SharesByPath> {
        return source.list(user, paging)
    }

    suspend fun retrieveShareForPath(
        user: String,
        path: String
    ): SharesByPath {
        val stat = fs.stat(user, path) ?: throw ShareException.NotFound()
        if (stat.ownerName != user) {
            throw ShareException.NotAllowed()
        }

        return source.findSharesForPath(user, path)
    }

    suspend fun create(
        user: String,
        share: CreateShareRequest,
        cloud: AuthenticatedCloud
    ): ShareId {
        // Check if user is allowed to share this file
        val stat = fs.stat(user, share.path) ?: throw ShareException.NotFound()
        if (stat.ownerName != user) {
            throw ShareException.NotAllowed()
        }

        // TODO Need to verify sharedWith exists!
        val rewritten = Share(
            owner = user,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis(),
            state = ShareState.REQUEST_SENT,
            path = share.path,
            sharedWith = share.sharedWith,
            rights = share.rights
        )

        val result = source.create(user, rewritten)

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
        newRights: Set<AccessRight>
    ) {
        val existingShare = source.find(user, shareId) ?: throw ShareException.NotFound()
        if (existingShare.owner != user) throw ShareException.NotAllowed()

        val newShare = existingShare.copy(
            modifiedAt = System.currentTimeMillis(),
            rights = newRights
        )

        if (existingShare.state == ShareState.ACCEPTED) {
            fs.grantRights(existingShare.owner, existingShare.sharedWith, existingShare.path, newRights)
        }

        source.update(user, shareId, newShare)
    }

    suspend fun updateState(
        user: String,
        shareId: ShareId,
        newState: ShareState
    ) {
        log.debug("Updating state $user $shareId $newState")
        val existingShare = source.find(user, shareId) ?: throw ShareException.NotFound()
        log.debug("Existing share: $existingShare")

        when (user) {
            existingShare.sharedWith -> when (newState) {
                ShareState.ACCEPTED -> {
                    // This is okay
                }

                else -> throw ShareException.NotAllowed()
            }

            existingShare.owner -> throw ShareException.NotAllowed()

            else -> {
                log.warn("ShareDAO returned a result but user is not owner or being sharedWith! $existingShare $user")
                throw IllegalStateException()
            }
        }

        // TODO How do we ensure atomicity?
        if (newState == ShareState.ACCEPTED) {
            fs.grantRights(existingShare.owner, existingShare.sharedWith, existingShare.path, existingShare.rights)
            fs.createSoftSymbolicLink(
                existingShare.sharedWith,
                fs.findFreeNameForNewFile(
                    user,
                    fs.joinPath(fs.homeDirectory(user), existingShare.path.substringAfterLast('/'))
                ),
                existingShare.path
            )
        }

        log.debug("Updating state")
        source.updateState(user, shareId, newState)
    }

    suspend fun deleteShare(
        user: String,
        shareId: ShareId
    ) {
        val existingShare = source.find(user, shareId) ?: throw ShareException.NotFound()
        fs.revokeRights(existingShare.owner, existingShare.sharedWith, existingShare.path)
        source.deleteShare(user, shareId)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ShareService::class.java)
    }
}