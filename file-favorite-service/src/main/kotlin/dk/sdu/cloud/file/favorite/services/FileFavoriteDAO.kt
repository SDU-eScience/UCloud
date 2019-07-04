package dk.sdu.cloud.file.favorite.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

interface FileFavoriteDAO<Session> {
    fun insert(
        session: Session,
        user: SecurityPrincipalToken,
        fileId: String
    )

    fun delete(
        session: Session,
        user: SecurityPrincipalToken,
        fileId: String
    )

    fun isFavorite(
        session: Session,
        user: SecurityPrincipalToken,
        fileId: String
    ): Boolean

    fun bulkIsFavorite(
        session: Session,
        files: List<StorageFile>,
        user: SecurityPrincipalToken
    ): Map<String, Boolean>

    fun listAll(
        session: Session,
        pagination: NormalizedPaginationRequest,
        user: SecurityPrincipalToken
    ): Page<String>

    fun deleteById(
        session: Session,
        fileIds: Set<String>
    )
}
