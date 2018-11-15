package dk.sdu.cloud.storage.services

import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.share.api.CreateShareRequest
import dk.sdu.cloud.share.api.Share
import dk.sdu.cloud.share.api.ShareId
import dk.sdu.cloud.share.api.ShareState
import dk.sdu.cloud.share.api.SharesByPath
import dk.sdu.cloud.storage.util.homeDirectory
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

sealed class ShareException(override val message: String, statusCode: HttpStatusCode) :
    RPCException(message, statusCode) {
    class NotFound : ShareException("Not found", HttpStatusCode.NotFound)
    class NotAllowed : ShareException("Not allowed", HttpStatusCode.Forbidden)
    class DuplicateException : ShareException("Already exists", HttpStatusCode.Conflict)
    class PermissionException : ShareException("Not allowed", HttpStatusCode.Forbidden)
    class BadRequest(why: String) : ShareException("Bad request: $why", HttpStatusCode.BadRequest)
    class InternalError(why: String) : ShareException("Internal error: $why", HttpStatusCode.InternalServerError)
}

private val log = LoggerFactory.getLogger(ShareService::class.java)

class ShareService<DBSession, Ctx : FSUserContext>(
    private val db: DBSessionFactory<DBSession>,
    private val shareDAO: ShareDAO<DBSession>,

    private val commandRunnerFactory: FSCommandRunnerFactory<Ctx>,
    private val aclService: ACLService<Ctx>,
    private val fs: CoreFileSystemService<Ctx>
) {

    fun list(
        ctx: Ctx,
        paging: NormalizedPaginationRequest = NormalizedPaginationRequest(null, null)
    ): Page<SharesByPath> {
        return db.withTransaction { shareDAO.list(it, ctx.user, paging) }
    }

    fun findSharesForPath(
        user: String,
        path: String
    ): SharesByPath {
        return db.withTransaction { shareDAO.findShareForPath(it, user, path) }
    }

    fun listSharesByStatus(
        user: String,
        status: ShareState,
        paging: NormalizedPaginationRequest = NormalizedPaginationRequest(null, null)
    ): Page<SharesByPath> {
        return db.withTransaction { shareDAO.listByStatus(it, user, status, paging) }
    }

    suspend fun create(
        ctx: Ctx,
        share: CreateShareRequest,
        cloud: AuthenticatedCloud
    ): ShareId {
        // Check if user is allowed to share this file
        val stat = fs.statOrNull(ctx, share.path, setOf(FileAttribute.OWNER)) ?: throw ShareException.NotFound()
        if (stat.owner != ctx.user) {
            throw ShareException.NotAllowed()
        }

        val lookup = UserDescriptions.lookupUsers.call(
            LookupUsersRequest(listOf(share.sharedWith)),
            cloud
        ) as? RESTResponse.Ok ?: throw ShareException.InternalError("Could not look up user")

        lookup.result.results[share.sharedWith]
            ?: throw ShareException.BadRequest("The user you are attempting to share with does not exist")

        val rewritten = Share(
            owner = ctx.user,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis(),
            state = ShareState.REQUEST_SENT,
            path = share.path,
            sharedWith = share.sharedWith,
            rights = share.rights
        )

        val result = db.withTransaction { shareDAO.create(it, ctx.user, rewritten) }

        coroutineScope {
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
                    cloud
                )
            }
        }

        return result
    }

    fun updateRights(
        ctx: Ctx,
        shareId: ShareId,
        newRights: Set<AccessRight>
    ) {
        val existingShare = db.withTransaction {
            shareDAO.updateRights(it, ctx.user, shareId, newRights)
        }

        if (existingShare.state == ShareState.ACCEPTED) {
            commandRunnerFactory.withContext(existingShare.owner) {
                aclService.grantRights(it, existingShare.path, FSACLEntity.User(existingShare.sharedWith), newRights)
            }
        }
    }

    fun updateState(
        ctx: Ctx,
        shareId: ShareId,
        newState: ShareState
    ) {
        db.withTransaction { session ->
            val existingShare = shareDAO.find(session, ctx.user, shareId)

            when (ctx.user) {
                existingShare.sharedWith -> when (newState) {
                    ShareState.ACCEPTED -> {
                        // This is okay
                    }

                    else -> throw ShareException.NotAllowed()
                }

                existingShare.owner -> throw ShareException.NotAllowed()

                else -> {
                    log.warn(
                        "ShareDAO returned a result but user is not owner or " +
                                "being sharedWith! $existingShare ${ctx.user}"
                    )
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
            shareDAO.updateState(session, ctx.user, shareId, newState)
        }
    }

    fun deleteShare(
        user: String,
        shareId: ShareId
    ) {
        db.withTransaction { dbSession ->
            val existingShare = shareDAO.deleteShare(dbSession, user, shareId)
            commandRunnerFactory.withContext(existingShare.owner) {
                aclService.revokeRights(it, existingShare.path, FSACLEntity.User(existingShare.sharedWith))
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ShareService::class.java)
    }
}
