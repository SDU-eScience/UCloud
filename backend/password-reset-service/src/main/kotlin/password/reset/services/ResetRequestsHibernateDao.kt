package dk.sdu.cloud.password.reset.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.service.db.*
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.timestamp
import dk.sdu.cloud.service.db.async.withSession
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import java.util.*
import javax.persistence.*

object PasswordResetRequestTable : SQLTable("password_reset_requests") {
    val token = text("token", notNull = true)
    val userId = text("user_id", notNull = true)
    val expiresAt = timestamp("expires_at", notNull = true)
}

class ResetRequestsHibernateDao : ResetRequestsDao {
    override suspend fun create(db: DBContext, token: String, userId: String) {
        val timeSource = System.currentTimeMillis()

        // Set to expire in 30 minutes
        val expiry = timeSource + 30 * 60 * 1000

        db.withSession { session ->
            session.insert(PasswordResetRequestTable) {
                set(PasswordResetRequestTable.token, token)
                set(PasswordResetRequestTable.userId, userId)
                set(PasswordResetRequestTable.expiresAt, LocalDateTime(expiry, DateTimeZone.UTC))
            }
        }
    }

    override suspend fun get(db: DBContext, token: String): ResetRequest? {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("token", token)
                    },
                    """
                        SELECT * 
                        FROM password_reset_requests
                        WHERE token = ?token
                    """.trimIndent()
                ).rows.singleOrNull()
        }?.toResetRequest()
    }

    fun RowData.toResetRequest(): ResetRequest {
        return ResetRequest(
            getField(PasswordResetRequestTable.token),
            getField(PasswordResetRequestTable.userId),
            getField(PasswordResetRequestTable.expiresAt).toDate()
        )
    }
}
