package dk.sdu.cloud.auth.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.allocateId
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.int
import dk.sdu.cloud.service.db.async.long
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.timestamp
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.HttpStatusCode
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object LoginAttemptTable : SQLTable("login_attempts") {
    val username = text("username", notNull = true)
    val createdAt = timestamp("created_at", notNull = true)
    val id = long("id", notNull = true)
}

object LoginCooldownTable : SQLTable("login_cooldown") {
    val username = text("username", notNull = true)
    val expiresAt = timestamp("expires_at", notNull = true)
    val allowLoginsAfter = timestamp("allow_logins_after", notNull = true)
    val severity = int("severity", notNull = true)
    val id = long("id", notNull = true)
}

data class LoginCooldown(
    val username: String,
    val expiresAt: Long,
    val allowLoginsAfter: Long,
    val severity: Int,
    val id: Long
)

fun RowData.toLoginCoolDown(): LoginCooldown {
    return LoginCooldown(
        getField(LoginCooldownTable.username),
        getField(LoginCooldownTable.expiresAt).toDateTime(DateTimeZone.UTC).millis,
        getField(LoginCooldownTable.allowLoginsAfter).toDateTime(DateTimeZone.UTC).millis,
        getField(LoginCooldownTable.severity),
        getField(LoginCooldownTable.id)
    )
}

class LoginAttemptAsyncDao(
    private val timeSource: () -> Long = { LocalDateTime.now(DateTimeZone.UTC).toDate().time / 1000 }
) {
    suspend fun logAttempt(db: DBContext, username: String) {
        db.withSession { session ->
            val id = session.allocateId()
            session.insert(LoginAttemptTable) {
                set(LoginAttemptTable.id, id)
                set(LoginAttemptTable.username, username)
                set(LoginAttemptTable.createdAt, LocalDateTime(timeSource(), DateTimeZone.UTC))
            }
        }

        timeUntilNextAllowedLogin(db, username) // Write cooldown entry if needed
    }

    suspend fun timeUntilNextAllowedLogin(db: DBContext, username: String): Long? {
        val currentCooldown = findLastCooldown(db, username)
        println(currentCooldown)
        if (currentCooldown != null && currentCooldown.allowLoginsAfter >= timeSource()) {
            return currentCooldown.allowLoginsAfter - timeSource()
        }
        val recentLoginAttempts = db.withSession { session ->
            val time = max(
                LocalDateTime(timeSource() - OBSERVATION_WINDOW, DateTimeZone.UTC).toDateTime().millis,
                currentCooldown?.allowLoginsAfter ?: -1
            )
            session
                .sendPreparedStatement(
                    {
                        setParameter("username", username)
                        setParameter("time", LocalDateTime(time).toDateTime().millis / 1000)
                    },
                    """
                        SELECT COUNT(*)
                        FROM login_attempts
                        WHERE (username = ?username) AND
                                (created_at >= to_timestamp(?time))
                    """.trimIndent()
                ).rows.singleOrNull()?.getLong(0)
                ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "SQL did not return a count")
        }
        if (recentLoginAttempts >= LOCKOUT_THRESHOLD) {
            val newSeverity = min(MAX_SEVERITY, findLastCooldown(db, username)?.severity?.plus(1) ?: 1)
            val allowLoginsAfter =
                timeSource() + (LOCKOUT_DURATION_BASE_SECONDS.toDouble().pow(newSeverity).toLong() * 1000)
            println(allowLoginsAfter)
            db.withSession { session ->
                val id = session.allocateId()
                session.insert(LoginCooldownTable) {
                    set(LoginCooldownTable.id, id)
                    set(LoginCooldownTable.username, username)
                    set(LoginCooldownTable.severity, newSeverity)
                    set(LoginCooldownTable.expiresAt, LocalDateTime(allowLoginsAfter + COOLDOWN_EXPIRY, DateTimeZone.UTC))
                    set(LoginCooldownTable.allowLoginsAfter, LocalDateTime(allowLoginsAfter, DateTimeZone.UTC))
                }
            }
            return LOCKOUT_DURATION_BASE_SECONDS.toDouble().pow(newSeverity).toLong() * 1000
        }
        return null
    }

    private suspend fun findLastCooldown(db: DBContext, username: String): LoginCooldown? {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("username", username)
                        setParameter("expire", timeSource() / 1000)
                    },
                    """
                        SELECT *
                        FROM login_cooldown
                        WHERE username = ?username AND expires_at >= to_timestamp(?expire)
                        ORDER BY severity DESC
                    """.trimIndent()
                ).rows.firstOrNull()?.toLoginCoolDown()
        }
    }

    companion object : Loggable {
        const val LOCKOUT_THRESHOLD = 5
        const val LOCKOUT_DURATION_BASE_SECONDS = 5
        const val OBSERVATION_WINDOW =  1000L * 60 * 5
        const val COOLDOWN_EXPIRY =  1000L * 60 * 60
        const val MAX_SEVERITY = 5 // This should lead to a lockout period of roughly one hour

        override val log = logger()
    }
}
