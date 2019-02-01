package dk.sdu.cloud.file.favorite.services

import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindByPath
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.indexing.api.LookupDescriptions
import dk.sdu.cloud.indexing.api.ReverseLookupFilesRequest
import dk.sdu.cloud.service.Loggable
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

        if (fileIds.items.isEmpty()) return Page(0, 0, 0, emptyList())

        val lookupResponse = LookupDescriptions.reverseLookupFiles.call(
            ReverseLookupFilesRequest(fileIds.items),
            serviceCloud
        ).orThrow()

        run {
            // Delete unknown files (files that did not appear in the reverse lookup)
            val unknownFiles = HashSet<String>()
            for ((index, file) in lookupResponse.files.withIndex()) {
                val fileId = fileIds.items[index]
                if (file == null) {
                    unknownFiles.add(fileId)
                }
            }

            if (unknownFiles.isNotEmpty()) {
                log.info("The following files no longer exist: $unknownFiles")
                db.withTransaction { session -> dao.deleteById(session, unknownFiles) }
            }
        }

        // TODO It might be necessary for us to verify knowledge of these files.
        // But given we need to do a stat to get it into the database it should be fine.
        return Page(
            fileIds.itemsInTotal,
            fileIds.itemsPerPage,
            fileIds.pageNumber,
            lookupResponse.files.filterNotNull()
        )
    }

    fun deleteById(fileIds: Set<String>) {
        db.withTransaction {
            dao.deleteById(it, fileIds)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
