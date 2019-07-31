package dk.sdu.cloud.share.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.fileId
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
    fun handleFilesMoved(events: List<StorageEvent.Moved>) {
        if (events.isEmpty()) return

        db.withTransaction { session ->
            shareDao.onFilesMoved(session, events)
        }
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
                    includeShares = true
                )
        }

        coroutineScope {
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
