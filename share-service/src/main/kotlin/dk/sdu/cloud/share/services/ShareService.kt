package dk.sdu.cloud.share.services

import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.TokenExtensionRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.client.jwtAuth
import dk.sdu.cloud.file.api.ACLEntryRequest
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindByPath
import dk.sdu.cloud.file.api.UpdateAclRequest
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.orNull
import dk.sdu.cloud.service.orThrow
import dk.sdu.cloud.share.api.CreateShareRequest
import dk.sdu.cloud.share.api.Share
import dk.sdu.cloud.share.api.ShareId
import dk.sdu.cloud.share.api.ShareState
import dk.sdu.cloud.share.api.SharesByPath
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class ShareService<DBSession>(
    private val serviceCloud: AuthenticatedCloud,
    private val db: DBSessionFactory<DBSession>,
    private val shareDAO: ShareDAO<DBSession>
) {
    fun list(
        user: String,
        paging: NormalizedPaginationRequest = NormalizedPaginationRequest(null, null)
    ): Page<SharesByPath> {
        return db.withTransaction { shareDAO.list(it, user, paging) }
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
        user: String,
        share: CreateShareRequest,
        userCloud: AuthenticatedCloud
    ): ShareId {
        FileDescriptions.stat
            .call(FindByPath(share.path), userCloud)
            .orNull()
            ?.takeIf { it.ownerName == user } ?: throw ShareException.NotFound()

        val lookup = UserDescriptions.lookupUsers.call(
            LookupUsersRequest(listOf(share.sharedWith)),
            serviceCloud
        ) as? RESTResponse.Ok ?: throw ShareException.InternalError("Could not look up user")

        lookup.result.results[share.sharedWith]
            ?: throw ShareException.BadRequest("The user you are attempting to share with does not exist")

        val rewritten = Share(
            owner = user,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis(),
            state = ShareState.REQUEST_SENT,
            path = share.path,
            sharedWith = share.sharedWith,
            rights = share.rights
        )

        val result = db.withTransaction { shareDAO.create(it, user, rewritten) }

        coroutineScope {
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
                    serviceCloud
                )
            }
        }

        return result
    }

    suspend fun updateRights(
        user: String,
        shareId: ShareId,
        newRights: Set<AccessRight>,
        userCloud: AuthenticatedCloud
    ) {
        val existingShare = db.withTransaction {
            shareDAO.updateRights(it, user, shareId, newRights)
        }

        if (existingShare.state == ShareState.ACCEPTED) {
            updateFSPermissions(existingShare, newRights, userCloud)
        }
    }

    suspend fun updateState(
        user: String,
        shareId: ShareId,
        newState: ShareState,
        userCloud: AuthenticatedCloud
    ) {
        db.withTransaction { session ->
            val existingShare = shareDAO.find(session, user, shareId)

            when (user) {
                existingShare.sharedWith -> when (newState) {
                    ShareState.ACCEPTED -> {
                        // This is okay
                    }

                    else -> throw ShareException.NotAllowed()
                }

                existingShare.owner -> throw ShareException.NotAllowed()

                else -> {
                    throw ShareException.InternalError(
                        "ShareDAO returned a result but user is not owner or " +
                                "being sharedWith! $existingShare $user"
                    )
                }
            }

            if (newState == ShareState.ACCEPTED) {
                // TODO This needs to be done in the context of the owner!
                updateFSPermissions(existingShare, existingShare.rights, userCloud)
                // TODO Create link for someone else?
                /*
                commandRunnerFactory.withContext(existingShare.sharedWith) {
                    fs.createSymbolicLink(
                        it,
                        existingShare.path,
                        joinPath(homeDirectory(ctx), existingShare.path.substringAfterLast('/'))
                    )
                }
                */
            }

            log.debug("Updating state")
            shareDAO.updateState(session, user, shareId, newState)
        }
    }

    suspend fun deleteShare(
        user: String,
        shareId: ShareId,
        userCloud: AuthenticatedCloud
    ) {
        db.withTransaction { dbSession ->
            val existingShare = shareDAO.find(dbSession, user, shareId)
            if (existingShare.owner != user) {

            } else {
                // deleteShare requires ownership of the share
                shareDAO.deleteShare(dbSession, user, shareId)
                FileDescriptions.updateAcl.call(
                    UpdateAclRequest(
                        existingShare.path,
                        true,
                        listOf(
                            ACLEntryRequest(
                                existingShare.sharedWith,
                                emptySet(),
                                revoke = true,
                                isUser = true
                            )
                        )
                    ),
                    userCloud
                )
            }
        }
    }

    suspend fun createUserCloud(userToken: String): AuthenticatedCloud {
        val token = AuthDescriptions.tokenExtension.call(
            TokenExtensionRequest(
                userToken,
                listOf(
                    FileDescriptions.updateAcl.requiredAuthScope
                ).map { it.toString() },
                expiresIn = FIVE_MINUTES_MS
            ),
            serviceCloud
        ).orThrow()

        return serviceCloud.parent.jwtAuth(token.accessToken)
    }

    private suspend fun updateFSPermissions(
        existingShare: Share,
        newRights: Set<AccessRight>,
        userCloud: AuthenticatedCloud
    ) {
        FileDescriptions.updateAcl.call(
            UpdateAclRequest(
                existingShare.path,
                true,
                listOf(
                    ACLEntryRequest(
                        existingShare.sharedWith,
                        newRights,
                        isUser = true
                    )
                )
            ), userCloud
        ).orThrow()
    }

    companion object : Loggable {
        override val log = logger()

        private const val FIVE_MINUTES_MS = 1000L * 60 * 5
    }
}
