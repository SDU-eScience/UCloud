package dk.sdu.cloud.file.favorite.services

import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindByPath
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.indexing.api.LookupDescriptions
import dk.sdu.cloud.indexing.api.ReverseLookupFilesRequest
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.orThrow

class FileFavoriteService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val dao: FileFavoriteDAO<DBSession>,
    private val serviceCloud: AuthenticatedCloud
) {
    suspend fun toggleFavorite(files: List<String>, user: String, userCloud: AuthenticatedCloud): List<String> {
        // Note: This function must ensure that the user has the correct privileges to the file!
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

    suspend fun listAll(
        pagination: NormalizedPaginationRequest,
        user: String
    ): Page<StorageFile> {
        val fileIds = db.withTransaction {
            dao.listAll(it, pagination, user)
        }

        val lookupResponse = LookupDescriptions.reverseLookupFiles.call(
            ReverseLookupFilesRequest(fileIds.items),
            serviceCloud
        ).orThrow()

        // TODO It might be necessary for us to verify knowledge of these files.
        // But given we need to do a stat to get it into the database it should be fine.
        return Page(
            fileIds.itemsInTotal,
            fileIds.itemsPerPage,
            fileIds.pageNumber,
            lookupResponse.files.filterNotNull()
        )
    }
}
