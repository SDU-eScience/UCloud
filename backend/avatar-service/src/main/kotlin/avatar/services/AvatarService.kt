package dk.sdu.cloud.avatar.services

import dk.sdu.cloud.avatar.api.Avatar
import dk.sdu.cloud.avatar.api.SerializedAvatar
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.db.withTransaction

class AvatarService(
    private val db: DBContext,
    private val dao: AvatarHibernateDAO
) {
    suspend fun upsert(user: String, avatar: Avatar) {
        db.withSession{ dao.upsert(it, user, avatar) }
    }

    suspend fun findByUser(user: String): Avatar =
        db.withSession { dao.findByUser(it, user) }

    suspend fun bulkFind(users: List<String>): Map<String, SerializedAvatar> =
        db.withSession { dao.bulkFind(it, users)}
}


interface AvatarDAO {
    suspend fun upsert(
        ctx: DBContext,
        user: String,
        avatar: Avatar
    )

    suspend fun findByUser(
        ctx: DBContext,
        user: String
    ): Avatar

    suspend fun bulkFind(
        ctx: DBContext,
        users: List<String>
    ): Map<String, SerializedAvatar>
}
