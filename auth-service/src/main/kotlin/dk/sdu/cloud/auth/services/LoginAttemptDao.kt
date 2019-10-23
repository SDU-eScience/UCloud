package dk.sdu.cloud.auth.services

import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.*
import java.util.*
import javax.persistence.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

interface LoginAttemptDao<Session> {
    fun logAttempt(session: Session, username: String)
    fun timeUntilNextAllowedLogin(session: Session, username: String): Long?
}

@Entity
@Table(name = "login_attempts")
data class LoginAttemptEntity(
    var username: String,

    @Temporal(TemporalType.TIMESTAMP)
    var createdAt: Date,

    @Id
    @GeneratedValue
    var id: Long = 0
) {
    companion object : HibernateEntity<LoginAttemptEntity>, WithId<Long>
}

@Entity
@Table(name = "login_cooldown")
data class LoginCooldown(
    // Note: We purposefully do not create any sort of foreign key to principals. We _WANT_ to keep login attempts
    // even for users we don't know.
    var username: String,

    @Temporal(TemporalType.TIMESTAMP)
    var expiresAt: Date,

    @Temporal(TemporalType.TIMESTAMP)
    var allowLoginsAfter: Date,

    var severity: Int,

    @Id
    @GeneratedValue
    var id: Long = 0
) {
    companion object : HibernateEntity<LoginCooldown>, WithId<Long>
}

class LoginAttemptHibernateDao(
    private val timeSource: () -> Long = { System.currentTimeMillis() }
) : LoginAttemptDao<HibernateSession> {
    override fun logAttempt(session: HibernateSession, username: String) {
        session.save(LoginAttemptEntity(username, Date(timeSource())))
        session.flush()

        timeUntilNextAllowedLogin(session, username) // Write cooldown entry if needed
    }

    override fun timeUntilNextAllowedLogin(session: HibernateSession, username: String): Long? {
        val currentCooldown = findLastCooldown(session, username)
        if (currentCooldown != null && currentCooldown.allowLoginsAfter.time >= timeSource()) {
            return currentCooldown.allowLoginsAfter.time - timeSource()
        }

        val recentLoginAttempts = session.createCriteriaBuilder<Long, LoginAttemptEntity>().run {
            criteria.where(
                (entity[LoginAttemptEntity::username] equal username) and
                        (entity[LoginAttemptEntity::createdAt] greaterThanEquals
                                Date(
                                    max(
                                        timeSource() - OBSERVATION_WINDOW,
                                        currentCooldown?.allowLoginsAfter?.time ?: -1
                                    )
                                ))
            )
            criteria.select(count(entity))
        }.createQuery(session).uniqueResult()

        if (recentLoginAttempts >= LOCKOUT_THRESHOLD) {
            val newSeverity = min(MAX_SEVERITY, findLastCooldown(session, username)?.severity?.plus(1) ?: 1)
            val allowLoginsAfter =
                timeSource() + (LOCKOUT_DURATION_BASE_SECONDS.toDouble().pow(newSeverity).toLong() * 1000L)

            session.save(
                LoginCooldown(
                    username,
                    Date(allowLoginsAfter + COOLDOWN_EXPIRY),
                    Date(allowLoginsAfter),
                    newSeverity
                )
            )

            return LOCKOUT_DURATION_BASE_SECONDS.toDouble().pow(newSeverity).toLong() * 1000L
        }

        return null
    }

    private fun findLastCooldown(session: HibernateSession, username: String): LoginCooldown? {
        return session.criteria<LoginCooldown>(orderBy = { listOf(descending(entity[LoginCooldown::severity])) }) {
            (entity[LoginCooldown::username] equal username) and
                    (entity[LoginCooldown::expiresAt] greaterThanEquals Date(timeSource()))
        }.resultList.firstOrNull()
    }

    companion object : Loggable {
        const val LOCKOUT_THRESHOLD = 5
        const val LOCKOUT_DURATION_BASE_SECONDS = 5
        const val OBSERVATION_WINDOW = 1000L * 60 * 5
        const val COOLDOWN_EXPIRY = 1000L * 60 * 60
        const val MAX_SEVERITY = 5 // This should lead to a lockout period of roughly one hour

        override val log = logger()
    }
}
