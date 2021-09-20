package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.orchestrator.api.FileCollection
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*

class LimitChecker(
    private val db: DBContext,
) {
    private val isCollectionLocked = SimpleCache<String, Boolean>(
        maxAge = 60_00 * 5L,
        lookup = { collectionId ->
            db.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("id", collectionId.toLongOrNull() ?: -1L)
                    },
                    """
                        select collection
                        from file_ucloud.quota_locked
                        where collection = :id
                    """
                ).rows.isNotEmpty()
            }
        }
    )
    suspend fun checkLimit(collection: FileCollection) {
        if (isCollectionLocked.get(collection.id) == true) {
            throw RPCException(
                "Quota has been exceeded. Delete some files and try again later.",
                HttpStatusCode.PaymentRequired
            )
        }
    }
}
