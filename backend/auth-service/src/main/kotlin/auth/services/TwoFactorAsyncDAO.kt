package dk.sdu.cloud.auth.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLField
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.allocateId
import dk.sdu.cloud.service.db.async.bool
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.int
import dk.sdu.cloud.service.db.async.long
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.timestamp
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.hibernate.annotations.NaturalId
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import java.nio.file.attribute.UserPrincipalNotFoundException
import java.util.*
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Inheritance
import javax.persistence.InheritanceType
import javax.persistence.ManyToOne
import javax.persistence.Table

/**
 * A Hibernate implementation of [TwoFactorDAO]
 */
class TwoFactorAsyncDAO {
    suspend fun findEnforcedCredentialsOrNull(db: DBContext, username: String): TwoFactorCredentials? {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("enforced", true)
                        setParameter("user", username)
                    },
                    """
                        SELECT *
                        FROM two_factor_credentials
                        WHERE enforced = ?enforced AND
                            principal_id = ?user
                    """.trimIndent()
                ).rows.singleOrNull()?.toTwoFactorCredentials(session)
        }
    }

    suspend fun findActiveChallengeOrNull(db: DBContext, challengeId: String): TwoFactorChallenge? {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", challengeId)
                        setParameter("time", LocalDateTime.now(DateTimeZone.UTC).toDateTime().millis / 1000)
                    },
                    """
                        SELECT *
                        FROM two_factor_challenges
                        WHERE challenge_id = ?id AND expires_at > to_timestamp(?time)
                    """.trimIndent()
                ).rows.singleOrNull()?.toTwoFactorChallenge(session)
        }
    }

    suspend fun createCredentials(db: DBContext, twoFactorCredentials: TwoFactorCredentials): Long {
        if (hasCredentials(db, twoFactorCredentials.principal.id)) throw TwoFactorException.AlreadyBound()
        return db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("id", twoFactorCredentials.principal.id)
                },
                """
                    SELECT * 
                    FROM principals 
                    WHERE id = ?id
                """.trimIndent()
            ).rows.singleOrNull() ?: UserException.NotFound()

            val id = session.allocateId()
            session.insert(TwoFactorCredentialsTable) {
                set(TwoFactorCredentialsTable.principal, twoFactorCredentials.principal.id)
                set(TwoFactorCredentialsTable.sharedSecret, twoFactorCredentials.sharedSecret)
                set(TwoFactorCredentialsTable.enforced, twoFactorCredentials.enforced)
                set(TwoFactorCredentialsTable.id, id)
            }
            id
        }
    }

    suspend fun createChallenge(db: DBContext, challenge: TwoFactorChallenge) {
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("id", challenge.credentials.id)
                },
                """
                    SELECT *
                    FROM two_factor_credentials
                    WHERE id = ?id
                """.trimIndent()
            ).rows.singleOrNull() ?: RPCException.fromStatusCode(HttpStatusCode.BadRequest)
            session.insert(TwoFactorChallengeTable) {
                set(TwoFactorChallengeTable.type, challenge.type)
                set(TwoFactorChallengeTable.challengeId, challenge.challengeId)
                set(TwoFactorChallengeTable.expiresAt, LocalDateTime(challenge.expiresAt, DateTimeZone.UTC))
                set(TwoFactorChallengeTable.service, challenge.service)
                set(TwoFactorChallengeTable.credentials, challenge.credentials.id)
            }
        }
    }

    suspend fun findStatusBatched(db: DBContext, ids: Collection<String>): Map<String, Boolean> {
        val result = HashMap<String, Boolean>()
        ids.forEach { result[it] = false }
        db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("enforced", true)
                        setParameter("ids", ids.toList())
                    },
                    """
                        SELECT *
                        FROM two_factor_credentials
                        WHERE enforced = ?enforced AND principal_id IN (select unnest(?ids::text[]))
                    """.trimIndent()
                ).rows
                .forEach { row ->
                    result[row.getField(TwoFactorCredentialsTable.principal)] = true
                }
        }
        return result
    }

    private suspend fun hasCredentials(db: DBContext, username: String): Boolean =
        findEnforcedCredentialsOrNull(db, username) != null
}

/**
 * A Hibernate entity which maps the [TwoFactorChallenge] class
 *
 * Updated in:
 *
 * - V4__2FA.sql
 */
object TwoFactorChallengeTable : SQLTable("two_factor_challenges") {
    val type = text("dtype", notNull = true)
    val challengeId = text("challenge_id", notNull = true)
    val expiresAt = timestamp("expires_at", notNull = true)
    val credentials = long("credentials_id", notNull = true)
    val service = text("service")
}

/**
 * A Hibernate entity which maps the [TwoFactorCredentials] class
 *
 * Updated in:
 *
 * - V4__2FA.sql
 */
object TwoFactorCredentialsTable : SQLTable("two_factor_credentials") {
    val principal = text("principal_id", notNull = true)
    val sharedSecret = text("shared_secret", notNull = true)
    val enforced = bool("enforced", notNull = true)
    val id = long("id")
}

fun RowData.toTwoFactorChallenge(db: DBContext): TwoFactorChallenge {
    val credentialsID = getField(TwoFactorChallengeTable.credentials)
    val twoFactorCredentials = runBlocking {
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("id", credentialsID)
                },
                """
                    SELECT *
                    FROM two_factor_credentials
                    WHERE id = ?id
                """.trimIndent()
            ).rows.singleOrNull()?.toTwoFactorCredentials(db)
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }
    return TwoFactorChallenge(
        getField(TwoFactorChallengeTable.type),
        getField(TwoFactorChallengeTable.challengeId),
        getField(TwoFactorChallengeTable.expiresAt).toDateTime().millis,
        twoFactorCredentials,
        getField(TwoFactorChallengeTable.service)
    )
}

fun RowData.toTwoFactorCredentials(db: DBContext) : TwoFactorCredentials {
    val principalID = getField(TwoFactorCredentialsTable.principal)
    val principal = runBlocking {
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("id", principalID)
                },
                """
                    SELECT * 
                    FROM principals
                    WHERE id = ?id
                """.trimIndent()
            ).rows.singleOrNull()?.toPrincipal(getField(TwoFactorCredentialsTable.enforced))
                ?: throw UserException.NotFound()
        }
    }
    return TwoFactorCredentials(
        principal,
        getField(TwoFactorCredentialsTable.sharedSecret),
        getField(TwoFactorCredentialsTable.enforced),
        getField(TwoFactorCredentialsTable.id)
    )
}
