package dk.sdu.cloud.share.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.throwIfInternal
import dk.sdu.cloud.file.api.CreateLinkRequest
import dk.sdu.cloud.file.api.DeleteFileRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.fileId
import dk.sdu.cloud.file.api.link
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
                val recipientClient = share.recipientToken?.let(userCloudFactory) ?: return@mapNotNull null

                launch {
                    log.debug("Handling moved event for share: $share")

                    val linkStat = findShareLink(share, serviceClient, recipientClient)
                    if (linkStat != null) {
                        log.debug("Share path is $linkStat")

                        val isLink = linkStat.link
                        log.debug("Found link? $isLink")
                        if (isLink) {
                            FileDescriptions.deleteFile.call(
                                DeleteFileRequest(linkStat.path),
                                recipientClient
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
                        recipientClient
                    ).orThrow()

                    db.withTransaction { session ->
                        shareDao.updateShare(
                            session,
                            AuthRequirements(null),
                            share.id,
                            linkId = createdLink.fileId,
                            linkPath = createdLink.path
                        )
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
            // TODO We don't need to call updateAcl or anything like this. We just need to delete symlinks at the
            //  recipient.
            //
            // Or even better. We don't use symlinks at all. This would solve so many problems.

            val deletedShareJobs = deletedShares.map {
                launch {
                    var attempts = 0
                    while (attempts++ < 10) {
                        try {
                            shareService.deleteShare(it)
                            break
                        } catch (ex: Exception) {
                            delay(6_000)
                        }
                    }
                }
            }

            deletedShareJobs.joinAll()
        }
    }


    companion object : Loggable {
        override val log = logger()
    }
}
