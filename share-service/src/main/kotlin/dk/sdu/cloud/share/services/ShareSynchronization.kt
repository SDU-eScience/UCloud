package dk.sdu.cloud.share.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.file.api.ACLEntryRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.UpdateAclRequest
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import kotlinx.coroutines.delay
import kotlin.system.exitProcess

/**
 * A class for synchronizing the state of this DB with the storage.
 *
 * TODO Currently this only creates new entries in the storage. It does _not_ remove already existing entries.
 */
class ShareSynchronization<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val shareDao: ShareDAO<DBSession>,
    private val userClientFactory: (String) -> AuthenticatedClient
) {
    suspend fun synchronize() {
        db.withTransaction { session ->
            shareDao.listAll(session).forEach { share ->
                log.info("Synchronizing share: $share")
                var retries = 0
                while (retries < maxRetries) {
                    val result = FileDescriptions.updateAcl.call(
                        UpdateAclRequest(
                            share.path,
                            listOf(ACLEntryRequest(share.sharedWith, share.rights)),
                            automaticRollback = false
                        ),
                        userClientFactory(share.ownerToken)
                    )

                    if (result is IngoingCallResponse.Ok) {
                        break
                    } else {
                        delay(1000)
                    }

                    retries++
                }

                if (retries >= maxRetries) {
                    log.warn("Was unable to synchronize share: $share")
                }
            }
        }
        log.info("Synchronization complete!")
        exitProcess(0)
    }

    companion object : Loggable {
        override val log = logger()
        private const val maxRetries = 5
    }
}
