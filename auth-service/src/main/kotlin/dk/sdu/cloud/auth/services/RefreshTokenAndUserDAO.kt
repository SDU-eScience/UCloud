package dk.sdu.cloud.auth.services

import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.JSONB_TYPE
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import org.hibernate.annotations.NaturalId
import org.hibernate.annotations.Type
import java.util.*
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.ManyToOne
import javax.persistence.Table

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

    val extendedByChain: List<String> = emptyList()
)

interface RefreshTokenDAO<Session> {
    fun findById(session: Session, token: String): RefreshTokenAndUser?
    fun insert(session: Session, tokenAndUser: RefreshTokenAndUser)
    fun updateCsrf(session: Session, token: String, newCsrf: String)
    fun delete(session: Session, token: String): Boolean
}

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
@Entity
@Table(name = "refresh_tokens")
data class RefreshTokenEntity(
    @Id
    @NaturalId
    var token: String,

    @ManyToOne
    var associatedUser: PrincipalEntity,

    var csrf: String,

    var refreshTokenExpiry: Long?,

    var publicSessionReference: String?,

    var expiresAfter: Long,

    @Type(type = JSONB_TYPE)
    var scopes: List<String>,

    var extendedBy: String?,

    @Type(type = JSONB_TYPE)
    var extendedByChain: List<String>
) {
    companion object : HibernateEntity<RefreshTokenEntity>, WithId<String>
}

class RefreshTokenHibernateDAO : RefreshTokenDAO<HibernateSession> {
    override fun findById(session: HibernateSession, token: String): RefreshTokenAndUser? {
        return session
            .criteria<RefreshTokenEntity> {
                (entity[RefreshTokenEntity::token] equal token) and (anyOf(
                    entity[RefreshTokenEntity::refreshTokenExpiry] equal nullLiteral(),
                    builder.greaterThan<Long>(
                        entity[RefreshTokenEntity::refreshTokenExpiry],
                        System.currentTimeMillis()
                    )
                ))
            }
            .uniqueResult()
            ?.toModel()
    }

    override fun insert(session: HibernateSession, tokenAndUser: RefreshTokenAndUser) {
        val principal = PrincipalEntity[session, tokenAndUser.associatedUser] ?: throw UserException.NotFound()
        session.save(
            RefreshTokenEntity(
                token = tokenAndUser.token,
                associatedUser = principal,
                csrf = tokenAndUser.csrf,
                refreshTokenExpiry = tokenAndUser.refreshTokenExpiry,
                publicSessionReference = tokenAndUser.publicSessionReference,
                expiresAfter = tokenAndUser.expiresAfter,
                scopes = tokenAndUser.scopes.map { it.toString() },
                extendedBy = tokenAndUser.extendedBy,
                extendedByChain = tokenAndUser.extendedByChain
            )
        )
    }

    override fun updateCsrf(session: HibernateSession, token: String, newCsrf: String) {
        val tokenEntity = RefreshTokenEntity[session, token] ?: throw UserException.NotFound()
        tokenEntity.csrf = newCsrf
        session.save(tokenEntity)
    }

    override fun delete(session: HibernateSession, token: String): Boolean {
        session.delete(RefreshTokenEntity[session, token] ?: return false)
        return true
    }
}

fun RefreshTokenEntity.toModel(): RefreshTokenAndUser {
    return RefreshTokenAndUser(
        associatedUser = associatedUser.id,
        token = token,
        csrf = csrf,
        refreshTokenExpiry = refreshTokenExpiry,
        publicSessionReference = publicSessionReference,
        expiresAfter = expiresAfter,
        scopes = scopes.map { SecurityScope.parseFromString(it) },
        extendedBy = extendedBy,
        extendedByChain = extendedByChain
    )
}
