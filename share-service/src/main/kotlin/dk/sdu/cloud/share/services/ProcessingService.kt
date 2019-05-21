package dk.sdu.cloud.share.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.throwIfInternal
import dk.sdu.cloud.file.api.CreateLinkRequest
import dk.sdu.cloud.file.api.DeleteFileRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.FindHomeFolderRequest
import dk.sdu.cloud.file.api.StatRequest
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.fileId
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.fileType
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class ProcessingService<Session>(
    private val db: DBSessionFactory<Session>,
    private val shareDao: ShareDAO<Session>,
    private val userCloudFactory: (refreshToken: String) -> AuthenticatedClient,
    private val serviceClient: AuthenticatedClient,
    private val shareService: ShareService<*>
) {
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

                    val path = findShareLink(share, serviceClient)
                    if (path != null) {
                        log.debug("Share path is $path")
                        val stat = FileDescriptions.stat.call(
                            StatRequest(path),
                            recipientCloud
                        ).throwIfInternal()

                        val isLink = stat.orNull()?.fileType == FileType.LINK
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
                            linkPath = defaultLinkToShare(share, serviceClient),
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

    suspend fun handleFilesDeletedOrInvalidated(events: List<StorageEvent.Deleted>) {
        if (events.isEmpty()) return
        log.debug("Handling deleted or invalidated events $events")

        lateinit var deletedShares: List<InternalShare>
        db.withTransaction { session ->
            deletedShares =
                shareDao.findAllByFileIds(
                    session,
                    events.map { it.file.fileId },
                    includeShares = true,
                    includeLinks = false
                )
        }

        coroutineScope {
            // TODO Performance, we could bundle these by file ID and bulk updateAcl
            // TODO Confirm file was deleted, in this case we can skip the updateAcl call
            val deletedShareJobs = deletedShares.map { launch { shareService.deleteShare(it) } }

            // TODO Performance. This is not even slightly optimized for bulk.
            // The DB transfers are not in a single transaction (because we can't).
            // We cannot update multiple ACLs either.

            deletedShareJobs.joinAll()
        }
    }



   companion object : Loggable {
        override val log = logger()
    }
}
