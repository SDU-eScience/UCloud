package dk.sdu.cloud.password.reset.services

import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.util.encodeBase64
import java.security.SecureRandom

class PasswordResetService<Session>(
    private val db: DBSessionFactory<Session>,
    private val resetRequestsDao: ResetRequestsDao<Session>
) {

    @io.ktor.util.InternalAPI
    fun createReset(email: String) {
        // TODO Check if user exists (missing end-point)

        // Generate token
        val secureRandom = SecureRandom()
        val token = ByteArray(128).also { secureRandom.nextBytes(it) }.encodeBase64()

        // TODO Save in DB
        db.withTransaction {session ->
            resetRequestsDao.create(session, token, userId)
        }

        // TODO Send email to user

    }

    fun renewPassword(newPassword: String) {
        // TODO Ensure that the reset-token exists in the database,
        // TODO Check if the `createdAt` value is more than x minutes ago, and if so, mark the `state` of the reset token as `EXPIRED`,
        // TODO send a new-password request to the `auth-service`,
        // TODO if successful, mark the reset-token as `CLOSED`.
    }
}
