package dk.sdu.cloud.auth.services

import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.withSession
import java.lang.Exception

object OTTBlackListTable : SQLTable("ott_black_list") {
    val jti = text("jti", notNull = true)
    val claimedBy = text("claimed_by", notNull = true)
}

class OneTimeTokenAsyncDAO {
    /**
     * Claims a one time token
     *
     * @return `true` if the token was successfully claimed otherwise `false`
     */
    suspend fun claim(db: DBContext, jti: String, claimedBy: String): Boolean {
        val value = try {
            db.withSession { session ->
                session.insert(OTTBlackListTable) {
                    set(OTTBlackListTable.jti, jti)
                    set(OTTBlackListTable.claimedBy, claimedBy)
                }
            }
        } catch (ex: Exception) {
            null
        }

        return value != null
    }
}
