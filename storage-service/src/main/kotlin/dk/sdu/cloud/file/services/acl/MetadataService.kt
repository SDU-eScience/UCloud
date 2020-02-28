package dk.sdu.cloud.file.services.acl

import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

class MetadataService(
    private val db: AsyncDBSessionFactory,
    private val dao: MetadataDao
) {
    suspend fun findMetadata(
        path: String,
        user: String?,
        type: String?
    ): Metadata? {
        return db.withTransaction { session -> dao.findMetadata(session, path, user, type) }
    }

    suspend fun updateMetadata(metadata: Metadata) {
        db.withTransaction { session -> dao.updateMetadata(session, metadata) }
    }

    suspend fun listMetadata(
        paths: List<String>,
        user: String?,
        type: String?
    ): Map<String, List<Metadata>> {
        return db.withTransaction { session ->
            dao.listMetadata(session, paths, user, type)
        }
    }

    suspend fun removeEntry(
        path: String,
        user: String?,
        type: String?
    ) {
        db.withTransaction { session ->
            dao.removeEntry(session, path, user, type)
        }
    }

    suspend fun handleFilesMoved(
        oldPath: String,
        newPath: String
    ) {
        db.withTransaction { session ->
            dao.handleFilesMoved(session, oldPath, newPath)
        }
    }

    suspend fun handleFilesDeleted(
        paths: List<String>
    ) {
        db.withTransaction { session ->
            dao.handleFilesDeleted(session, paths)
        }
    }
}
