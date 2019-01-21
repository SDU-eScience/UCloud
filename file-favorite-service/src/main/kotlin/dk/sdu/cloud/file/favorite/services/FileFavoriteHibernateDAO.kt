package dk.sdu.cloud.file.favorite.services

import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
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

    @Id
    @GeneratedValue
    var id: Long = 0
) {
    companion object : HibernateEntity<FavoriteEntity>, WithId<Long>
}


class FileFavoriteHibernateDAO : FileFavoriteDAO<HibernateSession> {
    override fun isFavorite(
        session: HibernateSession,
        user: String,
        fileId: String
    ): Boolean {
        return session.criteria<FavoriteEntity> {
            (entity[FavoriteEntity::fileId] equal fileId) and
                    (entity[FavoriteEntity::username] equal user)
        }.uniqueResult() != null
    }

    override fun insert(session: HibernateSession, user: String, fileId: String) {
        val entity = FavoriteEntity(fileId, user)
        session.save(entity)
    }

    override fun delete(session: HibernateSession, user: String, fileId: String) {
        val entity = session.criteria<FavoriteEntity> {
            (entity[FavoriteEntity::fileId] equal fileId) and
                    (entity[FavoriteEntity::username] equal user)
        }.uniqueResult()

        session.delete(entity)
    }
}
