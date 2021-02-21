package dk.sdu.cloud.avatar.services

import dk.sdu.cloud.avatar.api.Avatar
import dk.sdu.cloud.avatar.api.SerializedAvatar
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession

class AvatarService(
    private val db: DBContext,
    private val dao: AvatarAsyncDao
) {
    suspend fun upsert(user: String, avatar: Avatar) {
        db.withSession{ dao.upsert(it, user, avatar) }
    }

    suspend fun findByUser(user: String): Avatar =
        db.withSession { dao.findByUser(it, user) }

    suspend fun bulkFind(users: List<String>): Map<String, SerializedAvatar> =
        db.withSession { dao.bulkFind(it, users)}
}
