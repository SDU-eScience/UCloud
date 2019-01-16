package dk.sdu.cloud.avatar.services

import dk.sdu.cloud.avatar.api.Avatar
import dk.sdu.cloud.avatar.api.SerializedAvatar
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

class AvatarService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val dao: AvatarDAO<DBSession>
) {

    fun upsert(user: String, avatar: Avatar) {
        db.withTransaction { dao.upsert(it, user, avatar) }
    }

    fun findByUser(user: String): Avatar =
        db.withTransaction { dao.findByUser(it, user) }

    fun bulkFind(users: List<String>): Map<String, SerializedAvatar> =
        db.withTransaction { dao.bulkFind(it, users)}
}


interface AvatarDAO<Session> {

    fun upsert(
        session: Session,
        user: String,
        avatar: Avatar
    )

    fun findByUser(
        session: Session,
        user: String
    ): Avatar

    fun bulkFind(
        session: Session,
        list: List<String>
    ): Map<String, SerializedAvatar>
}
