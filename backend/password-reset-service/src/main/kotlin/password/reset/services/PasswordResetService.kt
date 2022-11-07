package dk.sdu.cloud.password.reset.services

import dk.sdu.cloud.auth.api.ChangePasswordWithResetRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.mail.api.Mail
import dk.sdu.cloud.mail.api.MailDescriptions
import dk.sdu.cloud.mail.api.SendRequestItem
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.timestampToLocalDateTime
import org.slf4j.Logger
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.*

data class ResetRequest(
    val token: String,
    val userId: String,
    val expiresAt: Date
)

class PasswordResetService(
    private val db: DBContext,
    private val authenticatedClient: AuthenticatedClient,
    private val resetRequestsDao: ResetRequestsAsyncDao,
    private val secureRandom: SecureRandom
) {
    suspend fun createResetRequest(email: String) {
        // Check if user exists, and get userId
        val userBasedOnEmail = db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("email", email)
                    },
                    """
                        SELECT id
                        FROM "auth".principals
                        WHERE email = :email
                    """
                )
                .rows
                .singleOrNull()
                ?.getString(0) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }

        val token = Base64.getUrlEncoder().encodeToString(ByteArray(64).also { secureRandom.nextBytes(it) })

        // Save in request
        resetRequestsDao.create(db, token, userBasedOnEmail)


        MailDescriptions.sendToUser.call(
            bulkRequestOf(
                SendRequestItem(
                    userBasedOnEmail,
                    Mail.ResetPasswordMail(
                        token
                    ),
                    true
                )
            ),
            authenticatedClient
        ).orThrow()
    }

    suspend fun newPassword(token: String, newPassword: String) {
        val resetRequest = resetRequestsDao.get(db, token)
            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound, "Unable to reset password")

        if (timestampToLocalDateTime(resetRequest.expiresAt.time) < LocalDateTime.now()) {
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
