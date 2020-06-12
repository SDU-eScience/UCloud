package dk.sdu.cloud.password.reset.services

import dk.sdu.cloud.service.db.async.DBContext

interface ResetRequestsDao {
    suspend fun create(db: DBContext, token: String, userId: String)
    suspend fun get(db: DBContext, token: String): ResetRequest?
}
