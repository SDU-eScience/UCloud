package dk.sdu.cloud.share.services

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.throwIfInternal
import dk.sdu.cloud.calls.server.requiredAuthScope
import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStreamContainer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.file.api.ACLEntryRequest
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.BackgroundJobs
import dk.sdu.cloud.file.api.CreateLinkRequest
import dk.sdu.cloud.file.api.DeleteFileRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.StatRequest
import dk.sdu.cloud.file.api.UpdateAclRequest
import dk.sdu.cloud.file.api.fileId
import dk.sdu.cloud.file.api.link
import dk.sdu.cloud.file.api.ownerName
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.TYPE_PROPERTY
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.share.api.CreateShareRequest
import dk.sdu.cloud.share.api.ShareId
import dk.sdu.cloud.share.api.ShareState
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.math.max

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
            val share = db.withTransaction { shareDao.findById(it, AuthRequirements(), job.shareId) }
            when (job) {
                is ShareJob.ReadyToAccept -> handleReadyToAccept(share, job)
                is ShareJob.Accepting -> handleAccepting(share, job)
                is ShareJob.Updating -> handleUpdating(share, job)
                is ShareJob.Deleting -> handleDeleting(share, job)
                is ShareJob.Failing -> handleFailing(share, job)
            }
        })
    }

    // Share creation
    suspend fun create(
        user: String,
        share: CreateShareRequest,
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
            }

            // Join tasks
            lookupJob.join()
            val file = statJob.await() ?: throw ShareException.NotFound()

            // Verify results. We allow invalid shares in dev mode.
            if (!devMode && (file.ownerName != user || file.link)) {
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
                FileDescriptions.createLink.requiredAuthScope,
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
            workQueue.produce(
                ShareJob.Accepting(
                    updateFSPermissions(share),
                    share.id,
                    job.createLink
                )
            )
        } catch (ex: Throwable) {
            workQueue.produce(ShareJob.Failing(share.id))
        }
    }

    private suspend fun handleAccepting(share: InternalShare, job: ShareJob.Accepting) {
        awaitUpdateACL(
            share,
            job,

            onSuccess = {
                val userCloud = userClientFactory(share.recipientToken!!)

                try {
                    val createdLink = if (job.createLink) {
                        FileDescriptions.createLink.call(
                            CreateLinkRequest(
                                linkPath = defaultLinkToShare(share, serviceClient),
                                linkTargetPath = share.path
                            ),
                            userCloud
                        ).orThrow()
                    } else null

                    db.withTransaction { session ->
                        shareDao.updateShare(
                            session,
                            AuthRequirements(),
                            job.shareId,
                            state = ShareState.ACCEPTED,
                            linkId = createdLink?.fileId
                        )
                    }
                } catch (ex: Exception) {
                    workQueue.produce(ShareJob.Failing(share.id))
                }
            },

            onFailure = {
                markShareAsFailed(share)
            }
        )
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
                    workQueue.produce(
                        ShareJob.Updating(
                            updateFSPermissions(
                                existingShare,
                                newRights = newRights
                            ),
                            shareId,
                            newRights
                        )
                    )
                } catch (ex: Throwable) {
                    workQueue.produce(ShareJob.Failing(shareId))
                }
            }

            ShareState.FAILURE, ShareState.UPDATING -> {
                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
            }
        }
    }

    private suspend fun handleUpdating(share: InternalShare, job: ShareJob.Updating) {
        awaitUpdateACL(
            share,
            job,

            onSuccess = {
                db.withTransaction {
                    shareDao.updateShare(
                        it,
                        AuthRequirements(),
                        job.shareId,
                        rights = job.newRights,
                        state = ShareState.ACCEPTED
                    )
                }
            },

            onFailure = {
                // Fail hard. We don't trust that it is capable of correctly restoring permissions.
                workQueue.produce(ShareJob.Failing(job.shareId))
            }
        )
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
            workQueue.produce(
                ShareJob.Deleting(
                    updateFSPermissions(existingShare, revoke = true),
                    existingShare.id
                )
            )
        }
    }

    private suspend fun handleDeleting(share: InternalShare, job: ShareJob.Deleting) {
        awaitUpdateACL(
            share,
            job,
            onSuccess = {
                invalidateShare(share)
                db.withTransaction { dbSession ->
                    shareDao.deleteShare(dbSession, AuthRequirements(null), share.id)
                }
            },

            onFailure = {
                // Do nothing. Leave the share in a state where it can be revoked by the user (again).
            }
        )
    }

    private suspend fun handleFailing(share: InternalShare, job: ShareJob.Failing) {
        try {
            delay(job.failureCount * 1000L)
            val jobId = updateFSPermissions(share, revoke = true)
            if (job.failureCount > 100) log.warn("Failure count > 100 $job")

            awaitUpdateACL(
                share,
                jobId,

                onSuccess = { markShareAsFailed(share) },
                onFailure = { workQueue.produce(ShareJob.Failing(job.shareId, job.failureCount + 1)) }
            )
        } catch (ex: Throwable) {
            workQueue.produce(ShareJob.Failing(job.shareId, failureCount = job.failureCount + 1))
        }
    }

    // Utility Code
    private suspend fun invalidateShare(share: InternalShare) {
        if (!share.recipientToken.isNullOrEmpty()) {
            val linkPath = findShareLink(share, serviceClient)

            if (linkPath != null) {
                log.debug("linkPath found $linkPath")
                val recipientCloud = userClientFactory(share.recipientToken)

                val stat =
                    FileDescriptions.stat.call(StatRequest(linkPath), recipientCloud).throwIfInternal()

                if (stat.orNull()?.link == true) {
                    log.debug("Found link!")
                    // We choose not to throw if the call fails
                    FileDescriptions.deleteFile.call(
                        DeleteFileRequest(linkPath),
                        recipientCloud
                    )
                }
            }
        }

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
    ): String {
        val userCloud = userClientFactory(existingShare.ownerToken)
        return FileDescriptions.updateAcl.call(
            UpdateAclRequest(
                existingShare.path,
                true,
                listOf(
                    ACLEntryRequest(
                        existingShare.sharedWith,
                        newRights,
                        isUser = true,
                        revoke = revoke
                    )
                )
            ), userCloud
        ).orThrow().id
    }

    private suspend fun <E> awaitUpdateACL(
        share: InternalShare,
        job: E,
        onSuccess: suspend (result: BackgroundJobs.Query.Response) -> Unit,
        onFailure: suspend (result: BackgroundJobs.Query.Response) -> Unit
    ) where E : ShareJob, E : UpdateACLJob {
        awaitUpdateACL(share, job.updateAclJobId, onSuccess, onFailure)
    }

    private suspend fun awaitUpdateACL(
        share: InternalShare,
        updateAclJobId: String,
        onSuccess: suspend (result: BackgroundJobs.Query.Response) -> Unit,
        onFailure: suspend (result: BackgroundJobs.Query.Response) -> Unit
    ) {
        var currentDelay = 0L
        fun increaseAndGetDelay(): Long {
            currentDelay = max(5_000, currentDelay + 100)
            return currentDelay
        }

        while (true) {
            try {
                val userClient = userClientFactory(share.ownerToken)

                val backgroundResult =
                    BackgroundJobs.query.call(BackgroundJobs.Query.Request(updateAclJobId), userClient).orThrow()

                if (backgroundResult.statusCode > 0) {
                    try {
                        if (backgroundResult.statusCode < 300) {
                            onSuccess(backgroundResult)
                        } else {
                            onFailure(backgroundResult)
                        }
                    } finally {
                        break
                    }
                } else {
                    delay(increaseAndGetDelay())
                }
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                delay(increaseAndGetDelay())
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

private object ShareACLJobStream : EventStreamContainer() {
    val stream = stream<ShareJob>("share-acl-jobs", { it.shareId.toString() })
}

private interface UpdateACLJob {
    val updateAclJobId: String
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

    data class Accepting(
        override val updateAclJobId: String,
        override val shareId: ShareId,
        val createLink: Boolean
    ) : ShareJob(), UpdateACLJob

    data class Updating(
        override val updateAclJobId: String,
        override val shareId: ShareId,
        val newRights: Set<AccessRight>
    ) : ShareJob(), UpdateACLJob

    data class Deleting(
        override val updateAclJobId: String,
        override val shareId: ShareId
    ) : ShareJob(), UpdateACLJob

    data class Failing(
        override val shareId: ShareId,
        val failureCount: Int = 0
    ) : ShareJob()
}
