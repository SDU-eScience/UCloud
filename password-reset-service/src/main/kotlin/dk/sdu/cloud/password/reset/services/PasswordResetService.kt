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
import kotlinx.coroutines.delay
import org.slf4j.Logger
import java.security.SecureRandom
import java.util.*
import kotlin.random.Random.Default.nextLong

data class ResetRequest(
    val token: String,
    val userId: String,
    val expiresAt: Date
)

class PasswordResetService<Session>(
    private val db: DBSessionFactory<Session>,
    private val authenticatedClient: AuthenticatedClient,
    private val resetRequestsDao: ResetRequestsDao<Session>,
    private val secureRandom: SecureRandom
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
            log.debug("Failed to find user with email $email, returned status ${lookupWithEmail.statusCode}")
            delay(200 + nextLong(50, 100))
            return
        }

        val token = Base64.getUrlEncoder().encodeToString(ByteArray(64).also { secureRandom.nextBytes(it) })

        // Save in request
        db.withTransaction { session ->
            resetRequestsDao.create(session, token, lookup.userId)
        }

        MailDescriptions.send.call(
            SendRequest(
                lookup.userId,
                "[UCloud] Reset of Password",
                """<p>Hello ${lookup.firstNames},</p>
                |
                |<p>We have received a request to reset your UCloud account password. To proceed, follow the link below.</p>
                |
                |<p><a href="https://cloud.sdu.dk/app/login?password-reset=true&token=${token}">https://cloud.sdu.dk/app/login?password-reset=true&token=${token}</a></p>
                |
                |<p>If you did not initiate this request, feel free to disregard this email, or reply to this email for support.</p>
                """.trimMargin()
            ), authenticatedClient
        ).orThrow()
    }

    suspend fun newPassword(token: String, newPassword: String) {
        val resetRequest = db.withTransaction { session ->
            resetRequestsDao.get(session, token)
        } ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound, "Unable to reset password")

        if (resetRequest.expiresAt.time < System.currentTimeMillis()) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden, "Unable to reset password (token expired)")
        }

        UserDescriptions.changePasswordWithReset.call(
            ChangePasswordWithResetRequest(
                resetRequest.userId,
                newPassword
            ), authenticatedClient
        ).orThrow()
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
