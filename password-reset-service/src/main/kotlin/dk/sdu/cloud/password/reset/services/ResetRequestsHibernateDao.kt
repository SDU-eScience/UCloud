package dk.sdu.cloud.password.reset.services

import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
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
    var expiresAt: Date,

    @Column(name = "state", nullable = false)
    var valid: Boolean
) {
    companion object : HibernateEntity<PasswordResetRequestEntity>, WithId<String>
}


class ResetRequestsHibernateDao : ResetRequestsDao<HibernateSession> {
    override fun create(session: HibernateSession, token: String, userId: String) {
        val timeSource = System.currentTimeMillis()

        val passwordResetRequest = PasswordResetRequestEntity(
            token,
            userId,
            Date(timeSource),
            true
        )

        session.save(passwordResetRequest)
    }
}
