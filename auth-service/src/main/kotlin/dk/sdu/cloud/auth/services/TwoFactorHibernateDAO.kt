package dk.sdu.cloud.auth.services

import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import org.hibernate.annotations.NaturalId
import java.util.Date
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
class TwoFactorHibernateDAO : TwoFactorDAO<HibernateSession> {
    override fun findEnforcedCredentialsOrNull(session: HibernateSession, username: String): TwoFactorCredentials? {
        return session.criteria<TwoFactorCredentialsEntity> {
            (entity[TwoFactorCredentialsEntity::enforced] equal true) and
                    (entity[TwoFactorCredentialsEntity::principal][PrincipalEntity::id] equal username)
        }.list().singleOrNull()?.toModel()
    }

    override fun findActiveChallengeOrNull(session: HibernateSession, challengeId: String): TwoFactorChallenge? {
        return TwoFactorChallengeEntity[session, challengeId]
            ?.takeIf { System.currentTimeMillis() < it.expiresAt.time }
            ?.toModel()
    }

    override fun createCredentials(session: HibernateSession, twoFactorCredentials: TwoFactorCredentials): Long {
        if (hasCredentials(session, twoFactorCredentials.principal.id)) throw TwoFactorException.AlreadyBound()
        val entity = twoFactorCredentials.toEntity()
        return session.save(entity) as Long
    }

    override fun createChallenge(session: HibernateSession, challenge: TwoFactorChallenge) {
        val entity = challenge.toEntity()
        session.save(entity)
    }

    private fun hasCredentials(session: HibernateSession, username: String): Boolean =
        findEnforcedCredentialsOrNull(session, username) != null

}

/**
 * A Hibernate entity which maps the [TwoFactorChallenge] class
 *
 * Updated in:
 *
 * - V4__2FA.sql
 */
@Entity
@Table(name = "two_factor_challenges")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
sealed class TwoFactorChallengeEntity {
    @get:Id
    @get:NaturalId
    abstract var challengeId: String

    abstract var expiresAt: Date

    @get:ManyToOne
    abstract var credentials: TwoFactorCredentialsEntity

    abstract fun toModel(): TwoFactorChallenge

    /**
     * @see [TwoFactorChallenge.Login]
     */
    @Entity
    data class Login(
        override var challengeId: String,
        override var expiresAt: Date,
        override var credentials: TwoFactorCredentialsEntity,
        var service: String
    ) : TwoFactorChallengeEntity() {
        override fun toModel(): TwoFactorChallenge = TwoFactorChallenge.Login(
            challengeId,
            expiresAt.time,
            credentials.toModel(),
            service
        )
    }

    /**
     * @see [TwoFactorChallenge.Setup]
     */
    @Entity
    data class Setup(
        override var challengeId: String,
        override var expiresAt: Date,
        override var credentials: TwoFactorCredentialsEntity
    ) : TwoFactorChallengeEntity() {
        override fun toModel(): TwoFactorChallenge = TwoFactorChallenge.Setup(
            challengeId,
            expiresAt.time,
            credentials.toModel()
        )
    }

    companion object : HibernateEntity<TwoFactorChallengeEntity>, WithId<String>
}

/**
 * A Hibernate entity which maps the [TwoFactorCredentials] class
 *
 * Updated in:
 *
 * - V4__2FA.sql
 */
@Entity
@Table(name = "two_factor_credentials")
data class TwoFactorCredentialsEntity(
    @ManyToOne
    var principal: PrincipalEntity,

    var sharedSecret: String,
    var enforced: Boolean,

    @Id
    @GeneratedValue
    var id: Long? = null
) {
    fun toModel(): TwoFactorCredentials = TwoFactorCredentials(
        principal.toModel(),
        sharedSecret,
        enforced,
        id
    )

    companion object : HibernateEntity<TwoFactorCredentialsEntity>, WithId<Long>
}

fun TwoFactorCredentials.toEntity(): TwoFactorCredentialsEntity = TwoFactorCredentialsEntity(
    principal.toEntity(),
    sharedSecret,
    enforced,
    id
)

fun TwoFactorChallenge.toEntity(): TwoFactorChallengeEntity = when (this) {
    is TwoFactorChallenge.Login -> TwoFactorChallengeEntity.Login(
        challengeId,
        Date(expiresAt),
        credentials.toEntity(),
        service
    )

    is TwoFactorChallenge.Setup -> TwoFactorChallengeEntity.Setup(
        challengeId,
        Date(expiresAt),
        credentials.toEntity()
    )
}
