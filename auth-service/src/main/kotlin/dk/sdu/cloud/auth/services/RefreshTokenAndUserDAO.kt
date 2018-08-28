package dk.sdu.cloud.auth.services

import dk.sdu.cloud.service.db.*
import org.hibernate.annotations.NaturalId
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.ManyToOne
import javax.persistence.Table

data class RefreshTokenAndUser(
    val associatedUser: String,
    val token: String,
    val csrf: String
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
 */
@Entity
@Table(name = "refresh_tokens")
data class RefreshTokenEntity(
    @Id
    @NaturalId
    var token: String,

    @ManyToOne
    var associatedUser: PrincipalEntity,

    var csrf: String
) {
    companion object : HibernateEntity<RefreshTokenEntity>, WithId<String>
}

class RefreshTokenHibernateDAO : RefreshTokenDAO<HibernateSession> {
    override fun findById(session: HibernateSession, token: String): RefreshTokenAndUser? {
        return session
            .criteria<RefreshTokenEntity> { entity[RefreshTokenEntity::token] equal token }
            .uniqueResult()
            ?.toModel()
    }

    override fun insert(session: HibernateSession, tokenAndUser: RefreshTokenAndUser) {
        val principal = PrincipalEntity[session, tokenAndUser.associatedUser] ?: throw UserException.NotFound()
        session.save(RefreshTokenEntity(tokenAndUser.token, principal, tokenAndUser.csrf))
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
    return RefreshTokenAndUser(associatedUser.id, token, csrf)
}
