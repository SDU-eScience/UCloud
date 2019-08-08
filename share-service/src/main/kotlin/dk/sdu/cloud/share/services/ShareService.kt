package dk.sdu.cloud.share.services

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.server.requiredAuthScope
import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStreamContainer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.file.api.ACLEntryRequest
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.BackgroundJobs
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.StatRequest
import dk.sdu.cloud.file.api.UpdateAclRequest
import dk.sdu.cloud.file.api.fileId
import dk.sdu.cloud.file.api.ownerName
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.TYPE_PROPERTY
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.share.api.ShareId
import dk.sdu.cloud.share.api.ShareState
import dk.sdu.cloud.share.api.Shares
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class ShareService<DBSession>(
    private val serviceClient: AuthenticatedClient,
    private val db: DBSessionFactory<DBSession>,
    private val shareDao: ShareDAO<DBSession>,
    private val userClientFactory: (refreshToken: String) -> AuthenticatedClient,
    private val eventStreamService: EventStreamService,
    private val devMode: Boolean = false
) {
    private val workQueue = eventStreamService.createProducer(ShareACLJobStream.stream)

    // Work queue. Add handlers here and place function near the correct workflow. It makes it easier to read the code.
    fun initializeJobQueue() {
        eventStreamService.subscribe(ShareACLJobStream.stream, EventConsumer.Immediate { job ->
            val share = try {
                db.withTransaction { shareDao.findById(it, AuthRequirements(), job.shareId) }
            } catch (ex: ShareException.NotFound) {
                log.info("Could not find share in $job")
                return@Immediate
            }

            when (job) {
                is ShareJob.ReadyToAccept -> handleReadyToAccept(share, job)
                is ShareJob.Deleting -> handleDeleting(share, job)
                is ShareJob.Failing -> handleFailing(share, job)

                else -> {}
            }
        })
    }

    // Share creation
    suspend fun create(
        user: String,
        share: Shares.Create.Request,
        userToken: String,
        userCloud: AuthenticatedClient
    ): ShareId {
        log.debug("Creating share for $user $share")
        lateinit var fileId: String

        coroutineScope {
            log.debug("Verifying file exists")
            val statJob = async {
                FileDescriptions.stat
                    .call(StatRequest(share.path), userCloud)
                    .orNull()
            }

            log.debug("Verifying that user exists")
            val lookupJob = launch {
                val lookup = UserDescriptions.lookupUsers.call(
                    LookupUsersRequest(listOf(share.sharedWith)),
                    serviceClient
                ).orRethrowAs { throw ShareException.InternalError("Could not look up user") }
                lookup.results[share.sharedWith]
                    ?: throw ShareException.BadRequest("The user you are attempting to share with does not exist")
                if (lookup.results[share.sharedWith]?.role == Role.SERVICE) {
                    throw ShareException.BadRequest("The user you are attempting to share with does not exist")
                }
            }

            // Join tasks
            lookupJob.join()
            val file = statJob.await() ?: throw ShareException.NotFound()

            // Verify results. We allow invalid shares in dev mode.
            if (!devMode && file.ownerName != user) {
                throw ShareException.NotAllowed()
            }

            fileId = file.fileId
        }

        val ownerToken = createToken(
            serviceClient, userToken, listOf(
                FileDescriptions.updateAcl.requiredAuthScope,
                BackgroundJobs.query.requiredAuthScope
            )
        )

        try {
            val result = db.withTransaction { session ->
                shareDao.create(
                    session,
                    owner = user,
                    sharedWith = share.sharedWith,
                    path = share.path,
                    initialRights = share.rights,
                    fileId = fileId,
                    ownerToken = ownerToken
                )
            }

            aSendCreatedNotification(serviceClient, result, user, share)
            return result
        } catch (ex: Throwable) {
            revokeToken(serviceClient, ownerToken)
            throw ex
        }
    }

    // Share accepts
    suspend fun acceptShare(
        user: String,
        shareId: ShareId,
        userToken: String,
        createLink: Boolean = true
    ) {
        val auth = AuthRequirements(user, ShareRole.RECIPIENT)

        val existingShare = db.withTransaction { session ->
            shareDao.findById(session, auth, shareId)
        }

        if (existingShare.state != ShareState.REQUEST_SENT) {
            throw RPCException("Share has already been accepted.", HttpStatusCode.BadRequest)
        }

        val tokenExtension = createToken(
            serviceClient, userToken, listOf(
                FileDescriptions.stat.requiredAuthScope,
                FileDescriptions.deleteFile.requiredAuthScope
            )
        )

        db.withTransaction { session ->
            shareDao.updateShare(
                session,
                auth,
                shareId,
                state = ShareState.UPDATING,
                recipientToken = tokenExtension
            )
        }

        workQueue.produce(ShareJob.ReadyToAccept(shareId, createLink))
    }

    private suspend fun handleReadyToAccept(share: InternalShare, job: ShareJob.ReadyToAccept) {
        try {
            updateFSPermissions(share)

            db.withTransaction { session ->
                shareDao.updateShare(
                    session,
                    AuthRequirements(),
                    job.shareId,
                    state = ShareState.ACCEPTED
                )
            }

        } catch (ex: Throwable) {
            workQueue.produce(ShareJob.Failing(share.id))
        }
    }

    // Share updates
    suspend fun updateRights(
        user: String,
        shareId: ShareId,
        newRights: Set<AccessRight>
    ) {
        val auth = AuthRequirements(user, ShareRole.OWNER)
        val existingShare = db.withTransaction {
            shareDao.findById(it, auth, shareId)
        }

        when (existingShare.state) {
            ShareState.REQUEST_SENT -> {
                db.withTransaction { shareDao.updateShare(it, auth, shareId, rights = newRights) }
            }

            ShareState.ACCEPTED -> {
                db.withTransaction {
                    shareDao.updateShare(
                        it,
                        auth,
                        shareId,
                        state = ShareState.UPDATING
                    )
                }

                try {
                    updateFSPermissions(existingShare, newRights = newRights)

                    db.withTransaction {
                        shareDao.updateShare(
                            it,
                            AuthRequirements(),
                            shareId,
                            rights = newRights,
                            state = ShareState.ACCEPTED
                        )
                    }
                } catch (ex: Throwable) {
                    workQueue.produce(ShareJob.Failing(shareId))
                }
            }

            ShareState.FAILURE, ShareState.UPDATING -> {
                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
            }
        }
    }

    // Share deletions
    suspend fun deleteShare(user: String, shareId: ShareId) {
        val existingShare = db.withTransaction { dbSession ->
            shareDao.findById(dbSession, AuthRequirements(user, ShareRole.PARTICIPANT), shareId)
        }

        deleteShare(existingShare)
    }

    suspend fun deleteShare(existingShare: InternalShare) {
        log.debug("Deleting share $existingShare")
        if (existingShare.state == ShareState.UPDATING) {
            throw ShareException.BadRequest("Cannot delete share while it is updating!")
        }

        if (existingShare.state == ShareState.FAILURE) {
            invalidateShare(existingShare)
            db.withTransaction { dbSession ->
                shareDao.deleteShare(dbSession, AuthRequirements(null), existingShare.id)
            }
        } else {
            try {
                updateFSPermissions(existingShare, revoke = true)
                workQueue.produce(ShareJob.Deleting(existingShare.id))
            } catch (ex: RPCException) {
                if (ex.httpStatusCode == HttpStatusCode.NotFound) {
                    invalidateShare(existingShare)
                    db.withTransaction { dbSession ->
                        shareDao.deleteShare(dbSession, AuthRequirements(null), existingShare.id)
                    }
                    return
                } else {
                    throw ex
                }
            }

            try {
                db.withTransaction {
                    shareDao.updateShare(it, AuthRequirements(), existingShare.id, state = ShareState.UPDATING)
                }
            } catch (ignored: ShareException.NotFound) {
                // Ignored
            }
        }
    }

    private suspend fun handleDeleting(share: InternalShare, job: ShareJob.Deleting) {
        invalidateShare(share)
        db.withTransaction { dbSession ->
            shareDao.deleteShare(dbSession, AuthRequirements(null), share.id)
        }
    }

    private suspend fun handleFailing(share: InternalShare, job: ShareJob.Failing) {
        try {
            delay(job.failureCount * 1000L)
            try {
                updateFSPermissions(share, revoke = true)
                if (job.failureCount > 100) log.warn("Failure count > 100 $job")
                markShareAsFailed(share)
            } catch (ex: RPCException) {
                if (ex.httpStatusCode == HttpStatusCode.NotFound) {
                    markShareAsFailed(share)
                } else {
                    throw ex
                }
            }
        } catch (ex: Throwable) {
            workQueue.produce(ShareJob.Failing(job.shareId, failureCount = job.failureCount + 1))
        }
    }

    // Utility Code
    private suspend fun invalidateShare(share: InternalShare) {
        // Revoke tokens
        coroutineScope {
            listOf(
                launch { revokeToken(serviceClient, share.ownerToken) },
                launch { revokeToken(serviceClient, share.recipientToken) }
            ).joinAll()
        }
    }

    private suspend fun markShareAsFailed(share: InternalShare) {
        // Changes to ACL should be rolled back automatically.
        invalidateShare(share)

        db.withTransaction { session ->
            shareDao.updateShare(
                session,
                AuthRequirements(),
                share.id,
                ownerToken = "",
                recipientToken = "",
                state = ShareState.FAILURE
            )
        }
    }

    private suspend fun updateFSPermissions(
        existingShare: InternalShare,
        revoke: Boolean = false,
        newRights: Set<AccessRight> = existingShare.rights
    ) {
        val userCloud = userClientFactory(existingShare.ownerToken)
        FileDescriptions.updateAcl.call(
            UpdateAclRequest(
                existingShare.path,
                listOf(
                    ACLEntryRequest(
                        existingShare.sharedWith,
                        newRights,
                        revoke = revoke
                    )
                )
            ), userCloud
        ).orThrow()
    }

    companion object : Loggable {
        override val log = logger()
    }
}

private object ShareACLJobStream : EventStreamContainer() {
    val stream = stream<ShareJob>("share-acl-jobs", { it.shareId.toString() })
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ShareJob.ReadyToAccept::class, name = "readyToAccept"),
    JsonSubTypes.Type(value = ShareJob.Accepting::class, name = "accepting"),
    JsonSubTypes.Type(value = ShareJob.Updating::class, name = "updating"),
    JsonSubTypes.Type(value = ShareJob.Failing::class, name = "failing"),
    JsonSubTypes.Type(value = ShareJob.Deleting::class, name = "deleting")
)
private sealed class ShareJob {
    abstract val shareId: ShareId

    data class ReadyToAccept(
        override val shareId: ShareId,
        val createLink: Boolean
    ) : ShareJob()

    @Deprecated("No longer in use")
    data class Accepting(
        override val shareId: ShareId,
        val createLink: Boolean
    ) : ShareJob()

    @Deprecated("No longer in use")
    data class Updating(
        override val shareId: ShareId,
        val newRights: Set<AccessRight>
    ) : ShareJob()

    data class Deleting(
        override val shareId: ShareId
    ) : ShareJob()

    data class Failing(
        override val shareId: ShareId,
        val failureCount: Int = 0
    ) : ShareJob()
}
