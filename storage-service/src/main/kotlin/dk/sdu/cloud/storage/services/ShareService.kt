package dk.sdu.cloud.storage.services

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.RESTHandler
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.storage.api.*
import dk.sdu.cloud.storage.util.homeDirectory
import dk.sdu.cloud.storage.util.joinPath
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.experimental.launch
import org.slf4j.LoggerFactory

sealed class ShareException(override val message: String) : RuntimeException(message) {
    class NotFound : ShareException("Not found")
    class NotAllowed : ShareException("Not allowed")
    class DuplicateException : ShareException("Already exists")
    class PermissionException : ShareException("Not allowed")
    class BadRequest(why: String) : ShareException("Bad request: $why")
    class InternalError(val why: String) : ShareException("Internal error")
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
                is ShareException.InternalError -> {
                    log.warn("Internal error! Why: ${ex.why}")
                    error(CommonErrorMessage("Internal server error"), HttpStatusCode.InternalServerError)
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

class ShareService<Ctx : FSUserContext>(
    private val source: ShareDAO,
    private val commandRunnerFactory: FSCommandRunnerFactory<Ctx>,
    private val aclService: ACLService<Ctx>,
    private val fs: CoreFileSystemService<Ctx>
) {

    suspend fun list(
        ctx: Ctx,
        paging: NormalizedPaginationRequest = NormalizedPaginationRequest(null, null)
    ): Page<SharesByPath> {
        return source.list(ctx.user, paging)
    }

    suspend fun retrieveShareForPath(
        ctx: Ctx,
        path: String
    ): SharesByPath {
        val stat = fs.statOrNull(ctx, path, setOf(FileAttribute.OWNER)) ?: throw ShareException.NotFound()
        if (stat.owner != ctx.user) {
            throw ShareException.NotAllowed()
        }

        return source.findSharesForPath(ctx.user, path)
    }

    suspend fun create(
        ctx: Ctx,
        share: CreateShareRequest,
        serviceCloud: AuthenticatedCloud
    ): ShareId {
        // Check if user is allowed to share this file
        val stat = fs.statOrNull(ctx, share.path, setOf(FileAttribute.OWNER)) ?: throw ShareException.NotFound()
        if (stat.owner != ctx.user) {
            throw ShareException.NotAllowed()
        }

        val lookup = UserDescriptions.lookupUsers.call(
            LookupUsersRequest(listOf(share.sharedWith)),
            serviceCloud
        ) as? RESTResponse.Ok ?: throw ShareException.InternalError("Could not look up user")

        lookup.result.results[share.sharedWith] ?:
            throw ShareException.BadRequest("The user you are attempting to share with does not exist")

        val rewritten = Share(
            owner = ctx.user,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis(),
            state = ShareState.REQUEST_SENT,
            path = share.path,
            sharedWith = share.sharedWith,
            rights = share.rights
        )

        val result = source.create(ctx.user, rewritten)

        launch {
            NotificationDescriptions.create.call(
                CreateNotification(
                    user = share.sharedWith,
                    notification = Notification(
                        type = "SHARE_REQUEST",
                        message = "${ctx.user} has shared a file with you",

                        meta = mapOf(
                            "shareId" to result,
                            "path" to share.path,
                            "rights" to share.rights
                        )
                    )
                ),
                serviceCloud
            )
        }

        return result
    }

    suspend fun update(
        ctx: Ctx,
        shareId: ShareId,
        newRights: Set<AccessRight>
    ) {
        val existingShare = source.find(ctx.user, shareId) ?: throw ShareException.NotFound()
        if (existingShare.owner != ctx.user) throw ShareException.NotAllowed()

        val newShare = existingShare.copy(
            modifiedAt = System.currentTimeMillis(),
            rights = newRights
        )

        if (existingShare.state == ShareState.ACCEPTED) {
            commandRunnerFactory.withContext(existingShare.owner) {
                aclService.grantRights(it, existingShare.path, FSACLEntity.User(existingShare.sharedWith), newRights)
            }
        }

        source.update(ctx.user, shareId, newShare)
    }

    suspend fun updateState(
        ctx: Ctx,
        shareId: ShareId,
        newState: ShareState
    ) {
        log.debug("Updating state ${ctx.user} $shareId $newState")
        val existingShare = source.find(ctx.user, shareId) ?: throw ShareException.NotFound()
        log.debug("Existing share: $existingShare")

        when (ctx.user) {
            existingShare.sharedWith -> when (newState) {
                ShareState.ACCEPTED -> {
                    // This is okay
                }

                else -> throw ShareException.NotAllowed()
            }

            existingShare.owner -> throw ShareException.NotAllowed()

            else -> {
                log.warn("ShareDAO returned a result but user is not owner or being sharedWith! $existingShare ${ctx.user}")
                throw IllegalStateException()
            }
        }

        if (newState == ShareState.ACCEPTED) {
            commandRunnerFactory.withContext(existingShare.owner) {
                aclService.grantRights(
                    it,
                    existingShare.path,
                    FSACLEntity.User(existingShare.sharedWith),
                    existingShare.rights
                )
            }

            commandRunnerFactory.withContext(existingShare.sharedWith) {
                fs.createSymbolicLink(
                    it,
                    existingShare.path,
                    joinPath(homeDirectory(ctx), existingShare.path.substringAfterLast('/'))
                )
            }
        }

        log.debug("Updating state")
        source.updateState(ctx.user, shareId, newState)
    }

    suspend fun deleteShare(
        user: String,
        shareId: ShareId
    ) {
        val existingShare = source.find(user, shareId) ?: throw ShareException.NotFound()
        commandRunnerFactory.withContext(existingShare.owner) {
            aclService.revokeRights(it, existingShare.path, FSACLEntity.User(existingShare.sharedWith))
        }
        source.deleteShare(user, shareId)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ShareService::class.java)
    }
}