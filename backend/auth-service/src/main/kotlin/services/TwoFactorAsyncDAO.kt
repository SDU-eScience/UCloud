package dk.sdu.cloud.auth.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.allocateId
import dk.sdu.cloud.service.db.async.bool
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.long
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.timestamp
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import java.util.*

class TwoFactorAsyncDAO {
    /**
     * Retrieves enforced two factor credentials associated with a [username]
     *
     * @return `null` if the enforced credentials for the [username] does not exist
     */
    suspend fun findEnforcedCredentialsOrNull(db: DBContext, username: String): TwoFactorCredentials? {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("user", username)
                    },
                    """
                        SELECT *
                        FROM two_factor_credentials
                        WHERE 
                            enforced = true AND
                            principal_id = :user
                    """
                )
                .rows
                .singleOrNull()
                ?.toTwoFactorCredentials(session)
        }
    }

    /**
     * Finds the currently active challenge by a [challengeId]
     *
     * @return `null` if the active challenge does not exist
     */
    suspend fun findActiveChallengeOrNull(db: DBContext, challengeId: String): TwoFactorChallenge? {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", challengeId)
                        setParameter("time", LocalDateTime(Time.now(), DateTimeZone.UTC).toDateTime().millis / 1000)
                    },
                    """
                        SELECT *
                        FROM two_factor_challenges
                        WHERE challenge_id = :id AND expires_at > to_timestamp(:time)
                    """
                )
                .rows
                .firstOrNull()
                ?.toTwoFactorChallenge(session)
        }
    }

    /**
     * Creates a set of credentials and inserts them into the database
     *
     * @throws TwoFactorException.AlreadyBound If the user already has an attached set of credentials
     * @throws UserException.NotFound If the associated user does not exist
     * @return the ID associated with the credentials
     */
    suspend fun createCredentials(db: DBContext, twoFactorCredentials: TwoFactorCredentials): Long {
        if (hasCredentials(db, twoFactorCredentials.principal.id)) throw TwoFactorException.AlreadyBound()
        return db.withSession { session ->
            val id = session.allocateId()
            try {
                session.insert(TwoFactorCredentialsTable) {
                    set(TwoFactorCredentialsTable.principal, twoFactorCredentials.principal.id)
                    set(TwoFactorCredentialsTable.sharedSecret, twoFactorCredentials.sharedSecret)
                    set(TwoFactorCredentialsTable.enforced, twoFactorCredentials.enforced)
                    set(TwoFactorCredentialsTable.id, id)
                }
            } catch (ex: Exception) {
                throw UserException.NotFound()
            }
            id
        }
    }

    /**
     * Creates and inserts a two factor challenge
     *
     * @throws RPCException [HttpStatusCode.Conflict] If a challenge with this ID already exists
     */
    suspend fun createChallenge(db: DBContext, challenge: TwoFactorChallenge) {
        db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", challenge.credentials.id)
                    },
                    """
                        SELECT *
                        FROM two_factor_credentials
                        WHERE id = :id
                    """
                ).rows
                .singleOrNull()
                ?: throw RPCException.fromStatusCode(HttpStatusCode.Conflict)
            session.insert(TwoFactorChallengeTable) {
                set(TwoFactorChallengeTable.type, challenge.type)
                set(TwoFactorChallengeTable.challengeId, challenge.challengeId)
                set(TwoFactorChallengeTable.expiresAt, LocalDateTime(challenge.expiresAt, DateTimeZone.UTC))
                set(TwoFactorChallengeTable.service, challenge.service)
                set(TwoFactorChallengeTable.credentials, challenge.credentials.id)
            }
        }
    }

    /**
     * Retrieves the enforced status for a batch of user [ids]
     *
     * An entry for every [ids] will always be in the output [Map]
     */
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
                        WHERE enforced = :enforced AND principal_id IN (select unnest(:ids::text[]))
                    """
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
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", credentialsID)
                    },
                    """
                        SELECT *
                        FROM two_factor_credentials
                        WHERE id = :id
                    """
                ).rows
                .singleOrNull()
                ?.toTwoFactorCredentials(db)
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

fun RowData.toTwoFactorCredentials(db: DBContext): TwoFactorCredentials {
    val principalID = getField(TwoFactorCredentialsTable.principal)
    val principal = runBlocking {
        db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", principalID)
                    },
                    """
                        SELECT * 
                        FROM principals
                        WHERE id = ?id
                    """.trimIndent()
                ).rows
                .singleOrNull()
                ?.toPrincipal(getField(TwoFactorCredentialsTable.enforced))
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
