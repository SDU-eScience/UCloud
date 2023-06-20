package dk.sdu.cloud.auth.services

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.allocateId
import dk.sdu.cloud.service.db.async.bool
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.long
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.timestamp
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.timestampToLocalDateTime

class TwoFactorAsyncDAO(
    private val principals: PrincipalService,
) {
    /**
     * Retrieves enforced two factor credentials associated with a [username]
     *
     * @return `null` if the enforced credentials for the [username] does not exist
     */
    suspend fun findEnforcedCredentialsOrNull(
        db: DBContext,
        username: String
    ): TwoFactorCredentials? {
        return db.withSession { session ->
            val id = session.sendPreparedStatement(
                { setParameter("user", username) },
                """
                    select id
                    from auth.two_factor_credentials
                    where 
                        enforced = true and
                        principal_id = :user
                """
            ).rows.firstOrNull()?.let { it.getLong(0)!! } ?: return@withSession null

            findCredentialsById(id, session)
        }
    }

    private suspend fun findCredentialsById(
        id: Long,
        ctx: DBContext,
    ): TwoFactorCredentials? {
        return ctx.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("id", id) },
                """
                    select enforced, shared_secret, principal_id
                    from auth.two_factor_credentials
                    where id = :id
                """
            ).rows.firstOrNull()?.let {
                TwoFactorCredentials(
                    principals.findByUsername(it.getString(2)!!),
                    it.getString(1)!!,
                    it.getBoolean(0)!!,
                    id
                )
            }
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
                    },
                    """
                        select dtype, challenge_id, credentials_id, floor(extract(epoch from expires_at) * 1000)
                        from auth.two_factor_challenges
                        where challenge_id = :id and expires_at > now()
                    """
                )
                .rows
                .firstOrNull()
                ?.let { row ->
                    TwoFactorChallenge(
                        row.getString(0)!!,
                        row.getString(1)!!,
                        row.getLong(3)!!,
                        findCredentialsById(row.getLong(2)!!, session) ?: error("corrupt db"),
                    )
                }
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
                        select *
                        from auth.two_factor_credentials
                        where id = :id
                    """
                ).rows
                .singleOrNull()
                ?: throw RPCException.fromStatusCode(HttpStatusCode.Conflict)
            session.insert(TwoFactorChallengeTable) {
                set(TwoFactorChallengeTable.type, challenge.type)
                set(TwoFactorChallengeTable.challengeId, challenge.challengeId)
                set(TwoFactorChallengeTable.expiresAt, timestampToLocalDateTime(challenge.expiresAt))
                set(TwoFactorChallengeTable.service, challenge.service)
                set(TwoFactorChallengeTable.credentials, challenge.credentials.id)
            }
        }
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
