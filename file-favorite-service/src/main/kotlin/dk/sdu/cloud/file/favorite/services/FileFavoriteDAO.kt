package dk.sdu.cloud.file.favorite.services

import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

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

    fun listAll(
        session: Session,
        pagination: NormalizedPaginationRequest,
        user: String
    ): Page<String>

    fun deleteById(
        session: Session,
        fileIds: Set<String>
    )
}
