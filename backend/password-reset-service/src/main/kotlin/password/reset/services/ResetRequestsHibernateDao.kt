package dk.sdu.cloud.password.reset.services

import dk.sdu.cloud.service.db.*
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "password_reset_requests")
data class PasswordResetRequestEntity(
    @Id
    var token: String,

    @Column(name = "user_id", nullable = false)
    var userId: String,

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Date
) {
    companion object : HibernateEntity<PasswordResetRequestEntity>, WithId<String>

    fun toModel(): ResetRequest = ResetRequest(
        token,
        userId,
        expiresAt
    )
}


class ResetRequestsHibernateDao : ResetRequestsDao<HibernateSession> {
    override fun create(session: HibernateSession, token: String, userId: String) {
        val timeSource = System.currentTimeMillis()

        // Set to expire in 30 minutes
        val expiry = timeSource + 30 * 60 * 1000

        val passwordResetRequest = PasswordResetRequestEntity(
            token,
            userId,
            Date(expiry)
        )

        session.save(passwordResetRequest)
    }

    override fun get(session: HibernateSession, token: String): ResetRequest? {
        val result = session.criteria<PasswordResetRequestEntity> {
            entity[PasswordResetRequestEntity::token] equal token
        }.uniqueResult()

        return result.toModel()
    }
}
