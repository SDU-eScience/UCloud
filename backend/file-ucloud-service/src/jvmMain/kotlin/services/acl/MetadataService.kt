package dk.sdu.cloud.file.ucloud.services.acl

import dk.sdu.cloud.file.orchestrator.api.normalize
import dk.sdu.cloud.file.ucloud.services.RelativeInternalFile
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.usingSession
import dk.sdu.cloud.service.db.withTransaction
import kotlinx.serialization.Serializable

@Serializable
data class FindMetadataRequest(
    val path: String? = null,
    val type: String? = null,
    val username: String? = null,
) {
    init {
        require(path != null || type != null || username != null) { "At least one argument must be non-null!" }
    }
}

class MetadataService(
    private val db: AsyncDBSessionFactory,
    private val dao: MetadataDao
) {
    suspend fun <R> runMoveAction(
        oldPath: RelativeInternalFile,
        newPath: RelativeInternalFile,
        block: suspend () -> R
    ): R {
        return db.usingSession { session ->
            db.withTransaction(session) {
                dao.writeFileIsMoving(session, oldPath, newPath)
            }

            val result = runCatching { block() }

            if (result.isSuccess) {
                db.withTransaction(session) {
                    dao.handleFilesMoved(session, oldPath, newPath)
                }
            } else {
                db.withTransaction(session) {
                    dao.cancelMovement(session, listOf(oldPath))
                }
            }

            result.getOrThrow()
        }
    }

    suspend fun <R> runDeleteAction(
        paths: List<RelativeInternalFile>,
        block: suspend () -> R
    ): R {
        return db.usingSession { session ->
            db.withTransaction(session) {
                dao.writeFilesAreDeleting(session, paths)
            }

            val result = runCatching { block() }

            if (result.isSuccess) {
                db.withTransaction(session) {
                    dao.handleFilesDeleted(session, paths)
                }
            } else {
                db.withTransaction(session) {
                    dao.cancelMovement(session, paths)
                }
            }

            result.getOrThrow()
        }
    }
}
