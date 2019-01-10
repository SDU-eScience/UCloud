package dk.sdu.cloud.avatar.services

import dk.sdu.cloud.avatar.api.Avatar
import dk.sdu.cloud.avatar.api.UpdateRequest
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

class AvatarService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val dao: AvatarDAO<DBSession>
) {

    fun insert(user: String, avatar: Avatar): Long =
        db.withTransaction { dao.insert(it, user, avatar) }

    fun update(user: String, avatar: Avatar) {
        db.withTransaction { dao.update(it, user, avatar) }
    }

    fun findByUser(user: String): Avatar? =
        db.withTransaction { dao.findByUser(it, user) }
}



interface AvatarDAO<Session> {
    fun insert(
        session: Session,
        user: String,
        avatar: Avatar
    ) : Long

    fun update(
        session: Session,
        user: String,
        avatar: Avatar
    )

    fun findByUser(
        session: Session,
        user: String
    ) : Avatar?
}
