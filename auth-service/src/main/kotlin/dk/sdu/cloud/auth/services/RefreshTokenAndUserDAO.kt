package dk.sdu.cloud.auth.services

import dk.sdu.cloud.service.db.*
import org.hibernate.annotations.NaturalId
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.ManyToOne
import javax.persistence.Table

data class RefreshTokenAndUser(val associatedUser: String, val token: String)

interface RefreshTokenDAO<Session> {
    fun findById(session: Session, token: String): RefreshTokenAndUser?
    fun insert(session: Session, tokenAndUser: RefreshTokenAndUser)
    fun delete(session: Session, token: String): Boolean
}

/**
 * Updated in:
 *
 * - V1__Initial.sql
 */
@Entity
@Table(name = "refresh_tokens")
data class RefreshTokenEntity(
    @Id
    @NaturalId
    var token: String,

    @ManyToOne
    var associatedUser: PrincipalEntity
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
        val principal = PrincipalEntity[session, tokenAndUser.associatedUser] ?: TODO()
        session.save(RefreshTokenEntity(tokenAndUser.token, principal))
    }

    override fun delete(session: HibernateSession, token: String): Boolean {
        session.delete(RefreshTokenEntity[session, token] ?: return false)
        return true
    }
}

fun RefreshTokenEntity.toModel(): RefreshTokenAndUser {
    return RefreshTokenAndUser(associatedUser.id, token)
}
