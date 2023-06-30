package dk.sdu.cloud.auth.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.mapItems
import dk.sdu.cloud.paginate
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.*
import kotlinx.serialization.encodeToString
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.*

data class RefreshTokenAndUser(
    val associatedUser: String,

    val token: String,

    val csrf: String,

    val refreshTokenExpiry: Long? = null,

    /**
     * An opaque token that uniquely identifies a refresh token.
     *
     * This session reference __must not__ be used by any client. This session reference will be embedded in JWTs.
     * This makes them readable by the end-user. It is __very__ important that we do not leak refresh tokens into
     * the JWT. This reference is added solely for the purpose of auditing.
     */
    val publicSessionReference: String? = UUID.randomUUID().toString(),

    val expiresAfter: Long = 1000L * 60 * 10,

    val scopes: List<SecurityScope> = listOf(SecurityScope.ALL_WRITE),

    val extendedBy: String? = null,

    val extendedByChain: List<String> = emptyList(),

    val userAgent: String? = null,

    val ip: String? = null,

    val createdAt: Long = Time.now()
)

/**
 * Updated in:
 *
 * - V1__Initial.sql
 * - V2__CSRF.sql
 * - V3__Session_id.sql
 * - V5__Refresh_Templates.sql
 * - V6__Refresh_Expiry.sql
 * - V9__Extended_by_chain.sql
 */
object RefreshTokenTable : SQLTable("refresh_tokens") {
    val token = text("token", notNull = true)
    val associatedUser = text("associated_user_id", notNull = true)
    val csrf = text("csrf", notNull = true)
    val refreshTokenExpiry = long("refresh_token_expiry")
    val publicSessionReference = text("public_session_reference")
    val expiresAfter = long("expires_after", notNull = true)
    val scopes = jsonb("scopes", notNull = true)
    val extendedBy = text("extended_by")
    val extendedByChain = jsonb("extended_by_chain", notNull = true)
    val userAgent = text("user_agent")
    val ip = text("ip")
    val createdAt = timestamp("created_at")
}

class RefreshTokenAsyncDAO {
    /**
     * Finds a refresh token associated with a [user]
     */
    suspend fun findTokenForUser(db: DBContext, user: String): RefreshTokenAndUser? {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", user)
                    },
                    """
                        select *
                        from auth.refresh_tokens
                        where associated_user_id = :id
                    """
                )
                .rows
                .firstOrNull()
                ?.toRefreshTokenAndUser()
        }
    }

    /**
     * Finds a user associated with a [token] (refresh)
     */
    suspend fun findById(db: DBContext, token: String): RefreshTokenAndUser? {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("token", token)
                        setParameter("time", Time.now())
                    },
                    """
                        select *
                        from auth.refresh_tokens
                        where
                            token = :token
                            and (
                                refresh_token_expiry is null
                                or refresh_token_expiry > :time
                            )
                    """
                )
                .rows
                .singleOrNull()
                ?.toRefreshTokenAndUser()
        }
    }

    /**
     * Inserts a refresh [tokenAndUser] into the database
     *
     * This will verify that the user exists.
     */
    suspend fun insert(db: DBContext, tokenAndUser: RefreshTokenAndUser) {
        db.withSession { session ->
            val username = session
                .sendPreparedStatement(
                    { setParameter("user", tokenAndUser.associatedUser) },
                    """
                        select id
                        from auth.principals
                        where id = :user
                    """
                )
                .rows
                .singleOrNull()
                ?.getString(0) ?: throw UserException.NotFound()

            session.insert(RefreshTokenTable) {
                set(RefreshTokenTable.associatedUser, username)
                set(RefreshTokenTable.token, tokenAndUser.token)
                set(RefreshTokenTable.csrf, tokenAndUser.csrf)
                set(RefreshTokenTable.refreshTokenExpiry, tokenAndUser.refreshTokenExpiry)
                set(RefreshTokenTable.publicSessionReference, tokenAndUser.publicSessionReference)
                set(RefreshTokenTable.expiresAfter, tokenAndUser.expiresAfter)
                set(
                    RefreshTokenTable.scopes,
                    defaultMapper.encodeToString(tokenAndUser.scopes.map { it.toString() })
                )
                set(RefreshTokenTable.extendedBy, tokenAndUser.extendedBy)
                set(RefreshTokenTable.extendedByChain, defaultMapper.encodeToString(tokenAndUser.extendedByChain))
                set(RefreshTokenTable.userAgent, tokenAndUser.userAgent)
                set(RefreshTokenTable.ip, tokenAndUser.ip)
                set(RefreshTokenTable.createdAt, LocalDateTime.ofInstant(Date(tokenAndUser.createdAt).toInstant(), ZoneId.from(ZoneOffset.UTC)))
            }
        }
    }

    /**
     * Updates the CSRF token associated with a [token] (refresh)
     *
     * This throws a [UserException.NotFound] exception if the token does not exist.
     */
    suspend fun updateCsrf(db: DBContext, token: String, newCsrf: String) {
        val affected = db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("token", token)
                    setParameter("csrf", newCsrf)
                },
                """
                    update auth.refresh_tokens
                    set csrf = :csrf
                    where token = :token
                """
            ).rowsAffected
        }
        if (affected == 0L) {
            throw UserException.NotFound()
        }
    }

    /**
     * Invalidates (deletes) a refresh token from the database
     *
     * @return `true` if the token exists otherwise `false`
     */
    suspend fun delete(db: DBContext, token: String): Boolean {
        return db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("token", token)
                },
                """
                    delete from auth.refresh_tokens
                    where token = :token
                """
            ).rowsAffected > 0
        }
    }

    /**
     * Deletes all expires tokens from the database
     */
    suspend fun deleteExpired(db: DBContext) {
        db.withSession { session ->
            session
                .sendPreparedStatement(
                    { setParameter("time", Time.now()) },
                    """
                        delete from auth.refresh_tokens
                        where refresh_token_expiry < :time
                    """
                )
        }
    }

    /**
     * Finds all refresh tokens associated with a user
     */
    suspend fun findUserSessions(
        db: DBContext,
        username: String,
        pagination: NormalizedPaginationRequest
    ): Page<RefreshTokenAndUser> {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    { setParameter("user", username) },
                    """
                        select *
                        from auth.refresh_tokens
                        where
                            associated_user_id = :user
                            and extended_by is null
                    """
                )
                .rows
                .paginate(pagination)
                .mapItems { it.toRefreshTokenAndUser() }
        }
    }

    /**
     * Invalidates all refresh tokens associated with a given user
     */
    suspend fun invalidateUserSessions(db: DBContext, username: String) {
        db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("user", username)
                    },
                    """
                        delete from auth.refresh_tokens
                        where
                            associated_user_id = :user
                            and extended_by is null
                    """
                )
        }
    }
}

fun RowData.toRefreshTokenAndUser(): RefreshTokenAndUser {
    val scopes = defaultMapper.decodeFromString<List<String>>(getField(RefreshTokenTable.scopes))
    val extendedByChain = defaultMapper.decodeFromString<List<String>>(getField(RefreshTokenTable.extendedByChain))
    return RefreshTokenAndUser(
        associatedUser = getField(RefreshTokenTable.associatedUser),
        token = getField(RefreshTokenTable.token),
        csrf = getField(RefreshTokenTable.csrf),
        refreshTokenExpiry = getFieldNullable(RefreshTokenTable.refreshTokenExpiry),
        publicSessionReference = getField(RefreshTokenTable.publicSessionReference),
        expiresAfter = getField(RefreshTokenTable.expiresAfter),
        scopes = scopes.map { SecurityScope.parseFromString(it) },
        extendedBy = getField(RefreshTokenTable.extendedBy),
        extendedByChain = extendedByChain,
        ip = getField(RefreshTokenTable.ip),
        userAgent = getField(RefreshTokenTable.userAgent),
        createdAt = getField(RefreshTokenTable.createdAt).toInstant(ZoneOffset.UTC).toEpochMilli()
    )
}
