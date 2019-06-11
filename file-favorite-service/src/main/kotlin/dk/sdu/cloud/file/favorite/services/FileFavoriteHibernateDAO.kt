package dk.sdu.cloud.file.favorite.services

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.fileId
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.deleteCriteria
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.paginatedCriteria
import dk.sdu.cloud.service.mapItems
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table

@Entity
@Table(
    name = "favorites",
    indexes = [Index(columnList = "fileId")]
)
class FavoriteEntity(
    @Column(name = "fileId")
    var fileId: String,

    var username: String,

    var project: String? = null,

    @Id
    @GeneratedValue
    var id: Long = 0
) {
    companion object : HibernateEntity<FavoriteEntity>, WithId<Long>
}


class FileFavoriteHibernateDAO : FileFavoriteDAO<HibernateSession> {
    override fun isFavorite(
        session: HibernateSession,
        user: SecurityPrincipalToken,
        fileId: String
    ): Boolean {
        return session.criteria<FavoriteEntity> {
            (entity[FavoriteEntity::fileId] equal fileId) and
                    (entity[FavoriteEntity::username] equal user.principal.username)
        }.uniqueResult() != null
    }

    override fun bulkIsFavorite(
        session: HibernateSession,
        files: List<StorageFile>,
        user: SecurityPrincipalToken
    ): Map<String, Boolean> {
        val allFileIds = files.map { it.fileId }
        val chunkedFileIds = allFileIds.chunked(250)
        val result = HashMap<String, Boolean>()
        allFileIds.forEach { result[it] = false }

        chunkedFileIds
            .flatMap { fileIds ->
                session
                    .criteria<FavoriteEntity>(
                        orderBy = { listOf(descending(entity[FavoriteEntity::id])) },
                        predicate = {
                            (entity[FavoriteEntity::username] equal user.principal.username) and
                                    (entity[FavoriteEntity::fileId] isInCollection fileIds)
                        }
                    )
                    .list()
            }
            .forEach {
                result[it.fileId] = true
            }

        return result
    }

    override fun insert(session: HibernateSession, user: SecurityPrincipalToken, fileId: String) {
        val entity = FavoriteEntity(fileId, user.principal.username)
        session.save(entity)
    }

    override fun delete(session: HibernateSession, user: SecurityPrincipalToken, fileId: String) {
        val entity = session.criteria<FavoriteEntity> {
            (entity[FavoriteEntity::fileId] equal fileId) and
                    (entity[FavoriteEntity::username] equal user.principal.username)
        }.uniqueResult()

        session.delete(entity)
    }

    override fun listAll(
        session: HibernateSession,
        pagination: NormalizedPaginationRequest,
        user: SecurityPrincipalToken
    ): Page<String> {
        return session.paginatedCriteria<FavoriteEntity>(pagination) {
            entity[FavoriteEntity::username] equal user.principal.username
        }.mapItems { it.fileId }
    }

    override fun deleteById(session: HibernateSession, fileIds: Set<String>) {
        if (fileIds.isEmpty()) return

        log.debug("Deleting the following files: $fileIds")
        session.deleteCriteria<FavoriteEntity> {
            entity[FavoriteEntity::fileId] isInCollection fileIds
        }.executeUpdate()
    }

    companion object : Loggable {
        override val log = logger()
    }
}
