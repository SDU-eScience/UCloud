package dk.sdu.cloud.file.services.acl

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.api.FindMetadataRequest
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.withSession
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode

class MetadataService(
    private val db: AsyncDBSessionFactory,
    private val dao: MetadataDao
) {
    suspend fun findMetadata(
        path: String,
        user: String?,
        type: String?
    ): Metadata? {
        return db.withTransaction { session -> dao.findMetadata(session, path.normalize(), user, type) }
    }

    suspend fun updateMetadata(metadata: Metadata) {
        db.withTransaction { session -> dao.updateMetadata(session, metadata) }
    }

    suspend fun createMetadata(metadata: Metadata) {
        db.withTransaction { session -> dao.createMetadata(session, metadata) }
    }

    suspend fun updateMetadataBulk(metadata: List<Metadata>) {
        db.withTransaction { session ->
            metadata.forEach { dao.updateMetadata(session, it) }
        }
    }

    suspend fun createMetadataBulk(metadata: List<Metadata>) {
        db.withTransaction { session ->
            metadata.forEach { dao.createMetadata(session, it) }
        }
    }

    suspend fun listMetadata(
        paths: List<String>?,
        user: String?,
        type: String?
    ): Map<String, List<Metadata>> {
        return db.withTransaction { session ->
            dao.listMetadata(session, paths?.map { it.normalize() }, user, type)
        }
    }

    suspend fun removeEntries(updates: List<FindMetadataRequest>) {
        if (updates.any { it.path == null }) throw RPCException("Path must be non-null", HttpStatusCode.BadRequest)

        db.withTransaction { session ->
            updates.forEach { dao.removeEntry(session, it.path!!, it.username, it.type) }
        }
    }

    suspend fun removeAllMetadataOfType(rootPath: String, type: String) {
        db.withTransaction { session ->
            dao.deleteByPrefix(session, rootPath, type = type)
        }
    }

    suspend fun <R> runMoveAction(
        oldPath: String,
        newPath: String,
        block: suspend () -> R
    ): R {
        val normalizedOld = oldPath.normalize()
        val normalizedNew = newPath.normalize()
        return db.withSession { session ->
            db.withTransaction(session) {
                dao.writeFileIsMoving(session, normalizedOld, normalizedNew)
            }

            val result = runCatching { block() }

            if (result.isSuccess) {
                db.withTransaction(session) {
                    dao.handleFilesMoved(session, normalizedOld, normalizedNew)
                }
            } else {
                db.withTransaction(session) {
                    dao.cancelMovement(session, listOf(normalizedOld))
                }
            }

            result.getOrThrow()
        }
    }

    suspend fun <R> runDeleteAction(
        paths: List<String>,
        block: suspend () -> R
    ): R {
        val normalized = paths.map { it.normalize() }
        return db.withSession { session ->
            db.withTransaction(session) {
                dao.writeFilesAreDeleting(session, normalized)
            }

            val result = runCatching { block() }

            if (result.isSuccess) {
                db.withTransaction(session) {
                    dao.handleFilesDeleted(session, normalized)
                }
            } else {
                db.withTransaction(session) {
                    dao.cancelMovement(session, normalized)
                }
            }

            result.getOrThrow()
        }
    }

    suspend fun findByPrefix(
        pathPrefix: String,
        username: String?,
        type: String?
    ): List<Metadata> {
        return db.withTransaction { session ->
            dao.findByPrefix(session, pathPrefix, type, username)
        }
    }
}
