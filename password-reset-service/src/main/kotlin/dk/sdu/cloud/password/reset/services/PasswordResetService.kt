package dk.sdu.cloud.password.reset.services

import dk.sdu.cloud.auth.api.ChangePasswordWithResetRequest
import dk.sdu.cloud.auth.api.LookupUserWithEmailRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.mail.api.MailDescriptions
import dk.sdu.cloud.mail.api.SendRequest
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode
import org.slf4j.Logger
import java.security.SecureRandom
import java.util.*

data class ResetRequest(
    val token: String,
    val userId: String,
    val expiresAt: Date
)

class PasswordResetService<Session>(
    private val db: DBSessionFactory<Session>,
    private val authenticatedClient: AuthenticatedClient,
    private val resetRequestsDao: ResetRequestsDao<Session>
) {
    suspend fun createResetRequest(email: String) {
        // Check if user exists, and get userId
        val lookupWithEmail = UserDescriptions.lookupUserWithEmail.call(
            LookupUserWithEmailRequest(email),
            authenticatedClient
        )
        val lookupWithEmailOrNull = lookupWithEmail.orNull()

        val lookup = if (lookupWithEmailOrNull != null) {
            lookupWithEmailOrNull
        } else {
            log.error("Failed to find user with email $email, returned status ${lookupWithEmail.statusCode}")
            return
        }

        // Generate token
        val secureRandom = SecureRandom()
        val token = Base64.getUrlEncoder().encodeToString(ByteArray(64).also { secureRandom.nextBytes(it) })

        // Save in request
        db.withTransaction { session ->
            resetRequestsDao.create(session, token, lookup.userId)
        }

        try {
            val mailRequest = MailDescriptions.send.call(
                SendRequest(
                    lookup.userId,
                    "[UCloud] Reset of Password",
                    """Hello ${lookup.firstNames},
                    |
                    |We received a request to reset your UCloud account password. To proceed, follow the link below.
                    |
                    |https://cloud.sdu.dk/app/reset-password?token=${token}
                    |
                    |If you did not initiate this request, feel free to disregard this email, or reply to this email for support.
                    |
                    |Best regards 
                    |SDU eScience Center
                    """.trimMargin()
                ), authenticatedClient
            ).orNull()

            // Send email to user
            if (mailRequest == null) {
                log.error("Failed to send email to $email")
                return
            }
        } catch (e: Exception) {
            log.error(e.message)
        }
    }

    suspend fun newPassword(token: String, newPassword: String) {
        val resetRequest = db.withTransaction { session ->
            resetRequestsDao.get(session, token)
        } ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        if (resetRequest.expiresAt.time < System.currentTimeMillis()) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        UserDescriptions.changePasswordWithReset.call(
            ChangePasswordWithResetRequest(
                newPassword
            ), authenticatedClient
        ).orThrow()

        db.withTransaction { session ->
            resetRequestsDao.invalidate(session, token)
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
