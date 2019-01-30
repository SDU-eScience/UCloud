package dk.sdu.cloud.file.favorite.services

import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindByPath
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.orThrow

class FileFavoriteService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val dao: FileFavoriteDAO<DBSession>
) {
    suspend fun toggleFavorite(files: List<String>, user: String, userCloud: AuthenticatedCloud): List<String> {
        val failures = ArrayList<String>()
        db.withTransaction { session ->
            files.forEach { path ->
                try {
                    val fileId = FileDescriptions.stat.call(FindByPath(path), userCloud).orThrow().fileId

                    if (dao.isFavorite(session, user, fileId)) {
                        dao.delete(session, user, fileId)
                    } else {
                        dao.insert(session, user, fileId)
                    }
                } catch (e: Exception) {
                    failures.add(path)
                }
            }
        }
        return failures
    }

    fun getFavoriteStatus(files: List<StorageFile>, user: String): Map<String, Boolean> =
        db.withTransaction { dao.bulkIsFavorite(it, files, user) }
}

interface FileFavoriteDAO<Session> {

    fun insert(
        session: Session,
        user: String,
        fileId: String
    )

    fun delete(
        session: Session,
        user: String,
        fileId: String
    )

    fun isFavorite(
        session: Session,
        user: String,
        fileId: String
    ): Boolean

    fun bulkIsFavorite(
        session: Session,
        files: List<StorageFile>,
        user: String
    ): Map<String, Boolean>
}
