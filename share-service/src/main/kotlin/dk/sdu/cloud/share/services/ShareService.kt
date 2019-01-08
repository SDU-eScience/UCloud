package dk.sdu.cloud.share.services

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.TokenExtensionRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.BearerTokenAuthenticatedCloud
import dk.sdu.cloud.client.JWTAuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.client.bearerAuth
import dk.sdu.cloud.file.api.ACLEntryRequest
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.CreateLinkRequest
import dk.sdu.cloud.file.api.DeleteFileRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.FindByPath
import dk.sdu.cloud.file.api.FindHomeFolderRequest
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.UpdateAclRequest
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.homeDirectory
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.indexing.api.LookupDescriptions
import dk.sdu.cloud.indexing.api.ReverseLookupRequest
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
import dk.sdu.cloud.service.throwIfInternal
import dk.sdu.cloud.share.api.CreateShareRequest
import dk.sdu.cloud.share.api.MinimalShare
import dk.sdu.cloud.share.api.ShareId
import dk.sdu.cloud.share.api.ShareState
import dk.sdu.cloud.share.api.SharesByPath
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class ShareService<DBSession>(
    private val serviceCloud: AuthenticatedCloud,
    private val db: DBSessionFactory<DBSession>,
    private val shareDao: ShareDAO<DBSession>,
    private val userCloudFactory: (refreshToken: String) -> AuthenticatedCloud,
    private val devMode: Boolean = false
) {
    suspend fun create(
        user: String,
        share: CreateShareRequest,
        userCloud: BearerTokenAuthenticatedCloud
    ): ShareId {
        log.debug("Creating share for $user $share")
        lateinit var fileId: String
        lateinit var ownerToken: String

        // Verify request
        coroutineScope {
            log.debug("Verifying file exists")
            val statJob = async {
                FileDescriptions.stat
                    .call(FindByPath(share.path), userCloud)
                    .orNull()
                    ?.takeIf { devMode || it.ownerName == user } ?: throw ShareException.NotFound()
            }

            log.debug("Verifying that user exists")
            val lookupJob = launch {
                val lookup = UserDescriptions.lookupUsers.call(
                    LookupUsersRequest(listOf(share.sharedWith)),
                    serviceCloud
                ) as? RESTResponse.Ok ?: throw ShareException.InternalError("Could not look up user")

                log.debug("Lookup success!")

                lookup.result.results[share.sharedWith]
                    ?: throw ShareException.BadRequest("The user you are attempting to share with does not exist")
            }

            fileId = statJob.await().fileId
            log.debug("File exists")
            lookupJob.join()
            log.debug("User exists")
        }

        // Acquire token for owner
        // TODO If DB is failing we should delete this token
        coroutineScope {
            log.debug("Creating token for user")
            val extendJob = async {
                AuthDescriptions.tokenExtension.call(
                    TokenExtensionRequest(
                        userCloud.token,
                        listOf(
                            FileDescriptions.updateAcl.requiredAuthScope
                        ).map { it.toString() },
                        1000L * 60,
                        allowRefreshes = true
                    ),
                    serviceCloud
                ).orThrow()
            }

            ownerToken = extendJob.await().refreshToken ?:
                    throw ShareException.InternalError("Bad response from token extension. refreshToken == null")
        }

        // Save internal state
        log.debug("Saving internal state...")
        val result = db.withTransaction {
            shareDao.create(
                it,
                user,
                share.sharedWith,
                share.path,
                share.rights,
                fileId,
                ownerToken
            )
        }

        // Notify recipient
        log.debug("Sending notification...")
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

        log.debug("File share request sent!")
        return result
    }

    fun list(
        user: String,
        state: ShareState? = null,
        paging: NormalizedPaginationRequest = NormalizedPaginationRequest(null, null)
    ): Page<SharesByPath> {
        val page = db.withTransaction {
            shareDao.list(
                it,
                AuthRequirements(user, ShareRole.PARTICIPANT),
                state = state,
                paging = paging
            )
        }

        return Page(
            page.groupCount,
            paging.itemsPerPage,
            paging.page,
            page.allSharesForPage.groupByPath(user)
        )
    }

    fun findSharesForPath(
        user: String,
        path: String
    ): SharesByPath {
        return db.withTransaction { shareDao.findAllByPath(it, AuthRequirements(user), path) }
            .groupByPath(user)
            .single()
    }

    private fun List<InternalShare>.groupByPath(user: String): List<SharesByPath> {
        val byPath = groupBy { it.path }
        return byPath.map { (path, sharesForPath) ->
            val owner = sharesForPath.first().owner
            val sharedByMe = owner == user

            SharesByPath(path, owner, sharedByMe, sharesForPath.map {
                MinimalShare(it.id, it.sharedWith, it.rights, it.state)
            })
        }
    }

    suspend fun updateRights(
        user: String,
        shareId: ShareId,
        newRights: Set<AccessRight>
    ) {
        val existingShare = db.withTransaction {
            shareDao.updateShare(it, AuthRequirements(user, ShareRole.OWNER), shareId, rights = newRights)
        }

        if (existingShare.state == ShareState.ACCEPTED) {
            updateFSPermissions(existingShare, newRights, userCloudFactory(existingShare.ownerToken)).orThrow()
        }
    }

    suspend fun acceptShare(
        user: String,
        shareId: ShareId,
        userCloud: JWTAuthenticatedCloud
    ) {
        val auth = AuthRequirements(user, ShareRole.RECIPIENT)
        val existingShare = db.withTransaction { session -> shareDao.findById(session, auth, shareId) }

        coroutineScope {
            val updateCall = async {
                val ownerCloud = userCloudFactory(existingShare.ownerToken)
                updateFSPermissions(existingShare, existingShare.rights, ownerCloud).orThrow()
            }

            val extendCall = async {
                AuthDescriptions.tokenExtension.call(
                    TokenExtensionRequest(
                        userCloud.token,
                        listOf(
                            FileDescriptions.stat.requiredAuthScope,
                            FileDescriptions.createLink.requiredAuthScope,
                            FileDescriptions.deleteFile.requiredAuthScope
                        ).map { it.toString() },
                        expiresIn = 1000L * 60,
                        allowRefreshes = true
                    ),
                    serviceCloud
                ).orThrow()
            }

            val linkCall = async {
                FileDescriptions.createLink.call(
                    CreateLinkRequest(
                        linkPath = defaultLinkToShare(existingShare),
                        linkTargetPath = existingShare.path
                    ),
                    userCloud
                ).orThrow()
            }

            val createdLink = linkCall.await()
            updateCall.await()
            val tokenExtension = extendCall.await().refreshToken
                ?: throw ShareException.InternalError("bad response from token extension. refreshToken == null")

            db.withTransaction { session ->
                shareDao.updateShare(
                    session,
                    auth,
                    shareId,
                    recipientToken = tokenExtension,
                    state = ShareState.ACCEPTED,
                    linkId = createdLink.fileId
                )
            }
        }
    }

    private suspend fun defaultLinkToShare(share: InternalShare): String {
        val homeFolder = FileDescriptions.findHomeFolder.call(
            FindHomeFolderRequest(share.sharedWith),
            serviceCloud
        ).orThrow().path
        return joinPath(homeFolder, share.path.fileName())
    }

    suspend fun deleteShare(
        user: String,
        shareId: ShareId
    ) {
        val existingShare = db.withTransaction { dbSession ->
            shareDao.findById(dbSession, AuthRequirements(user, ShareRole.PARTICIPANT), shareId)
        }

        deleteShare(existingShare)
    }

    private suspend fun deleteShare(
        existingShare: InternalShare
    ) {
        log.debug("Deleting share $existingShare")
        val ownerCloud = userCloudFactory(existingShare.ownerToken)

        if (existingShare.state == ShareState.ACCEPTED) {
            // Revoke permissions and remove link
            coroutineScope {
                val updateCall = async {
                    // This is allowed to fail under certain conditions, such as not existing.
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
                        ownerCloud
                    ).throwIfInternal()
                }

                val linkPath = findShareLink(existingShare)
                if (linkPath != null) {
                    log.debug("linkPath found $linkPath")
                    val recipientCloud = existingShare.recipientToken?.let { userCloudFactory(it) }
                        ?: throw ShareException.InternalError("recipient token not yet established when deleting share")

                    val stat = FileDescriptions.stat.call(FindByPath(linkPath), recipientCloud).throwIfInternal()
                    if ((stat as? RESTResponse.Ok)?.result?.link == true) {
                        log.debug("Found link!")
                        val deleteLinkCall =
                            launch {
                                // We choose not to throw if the call fails
                                FileDescriptions.deleteFile.call(
                                    DeleteFileRequest(linkPath),
                                    recipientCloud
                                )
                            }

                        deleteLinkCall.join()
                    }
                }

                updateCall.join()
            }
        }

        // Revoke tokens
        coroutineScope {
            val ownerTokenRevoke = launch {
                AuthDescriptions.logout.call(Unit, serviceCloud.parent.bearerAuth(existingShare.ownerToken))
            }

            val recipientTokenRevoke = if (existingShare.recipientToken != null) {
                launch {
                    AuthDescriptions.logout.call(Unit, serviceCloud.parent.bearerAuth(existingShare.recipientToken))
                }
            } else {
                null
            }

            ownerTokenRevoke.join()
            recipientTokenRevoke?.join()
        }

        // TODO Maybe do this async in Kafka?
        // Do this last to ensure that share isn't deleted before FS permissions are fixed.
        // If we want more guarantees we would some additional steps, but this will do for now.
        db.withTransaction { dbSession ->
            shareDao.deleteShare(dbSession, AuthRequirements(null), existingShare.id)
        }

        log.debug("Share deleted $existingShare")
    }

    suspend fun handleFilesDeletedOrInvalidated(events: List<StorageEvent>) {
        if (events.isEmpty()) return
        log.debug("Handling deleted or invalidated events $events")

        lateinit var deletedShares: List<InternalShare>
        db.withTransaction { session ->
            deletedShares =
                    shareDao.findAllByFileIds(session, events.map { it.id }, includeShares = true, includeLinks = false)
        }

        coroutineScope {
            // TODO Performance, we could bundle these by file ID and bulk updateAcl
            // TODO Confirm file was deleted, in this case we can skip the updateAcl call
            val deletedShareJobs = deletedShares.map { launch { deleteShare(it) } }

            // TODO Performance. This is not even slightly optimized for bulk.
            // The DB transfers are not in a single transaction (because we can't).
            // We cannot update multiple ACLs either.

            deletedShareJobs.joinAll()
        }
    }

    suspend fun handleFilesMoved(events: List<StorageEvent.Moved>) {
        if (events.isEmpty()) return
        log.debug("Handling moved events: $events")

        // We update shares first, just to be more efficient. We will retry later (due to Kafka) if updating links
        // fail hard.
        val shares = db.withTransaction { session ->
            shareDao.onFilesMoved(session, events)
        }

        coroutineScope {
            shares.mapNotNull { share ->
                val recipientCloud = share.recipientToken?.let(userCloudFactory) ?: return@mapNotNull null

                launch {
                    log.debug("Handling moved event for share: $share")

                    val path = findShareLink(share)
                    if (path != null) {
                        log.debug("Share path is $path")
                        val stat = FileDescriptions.stat.call(
                            FindByPath(path),
                            recipientCloud
                        ).throwIfInternal()

                        val isLink = (stat as? RESTResponse.Ok)?.result?.fileType == FileType.LINK
                        log.debug("Found link? $isLink")
                        if (isLink) {
                            FileDescriptions.deleteFile.call(
                                DeleteFileRequest(path),
                                recipientCloud
                            ).throwIfInternal()
                            log.debug("File deleted")
                        }
                    }

                    log.debug("Creating link for $share")
                    val createdLink = FileDescriptions.createLink.call(
                        CreateLinkRequest(
                            linkPath = defaultLinkToShare(share),
                            linkTargetPath = share.path
                        ),
                        recipientCloud
                    ).orThrow()

                    db.withTransaction { session ->
                        shareDao.updateShare(session, AuthRequirements(null), share.id, linkId = createdLink.fileId)
                    }
                    log.debug("$share updated from moved event")
                }
            }
        }.joinAll()
    }

    private suspend fun updateFSPermissions(
        existingShare: InternalShare,
        newRights: Set<AccessRight>,
        userCloud: AuthenticatedCloud
    ): RESTResponse<Unit, CommonErrorMessage> {
        return FileDescriptions.updateAcl.call(
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
        )
    }

    private suspend fun findShareLink(
        existingShare: InternalShare
    ): String? {
        val linkId = existingShare.linkId ?: return null
        val result = LookupDescriptions.reverseLookup.call(
            ReverseLookupRequest(linkId),
            serviceCloud
        ) as? RESTResponse.Ok ?: return null

        return result.result.canonicalPath.firstOrNull()
    }

    companion object : Loggable {
        override val log = logger()
    }
}
