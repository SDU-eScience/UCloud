package dk.sdu.cloud.password.reset.services

import dk.sdu.cloud.auth.api.LookupUserWithEmailRequest
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.mail.api.MailDescriptions
import dk.sdu.cloud.mail.api.SendRequest
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode
import io.ktor.util.encodeBase64
import java.security.SecureRandom

class PasswordResetService<Session>(
    private val db: DBSessionFactory<Session>,
    private val authenticatedClient: AuthenticatedClient,
    private val resetRequestsDao: ResetRequestsDao<Session>
) {

    @io.ktor.util.InternalAPI
    suspend fun createReset(email: String) {
        println("Request send from $authenticatedClient")

        // Check if user exists, and get userId
        val lookup = UserDescriptions.lookupUserWithEmail.call(
            LookupUserWithEmailRequest(email),
            authenticatedClient
        ).orNull()
            ?: return

        // Generate token
        val secureRandom = SecureRandom()
        val token =
            ByteArray(64).also { secureRandom.nextBytes(it) }.encodeBase64().filterNot { it == '&' && it == '/' }

        // Save in request
        db.withTransaction { session ->
            resetRequestsDao.create(session, token, lookup.userId)
        }

        // Send email to user
        MailDescriptions.send.call(
            SendRequest(
                lookup.userId,
                "[UCloud] Reset of Password",
                """Hello ${lookup.firstNames},
                    
                    |We received a request to reset your UCloud account password. To proceed, follow the link below.
                    
                    |https://cloud.sdu.dk/app/reset-password?token=${token}
                    
                    |If you did not initiate this request, feel free to disregard this email, or reply to this email for support.
                    
                    |Best regards 
                    |SDU eScience Center
                    """.trimMargin()
            ), authenticatedClient
        ).orNull()
            ?: return
    }

    fun renewPassword(newPassword: String) {
        // TODO Ensure that the reset-token exists in the database,
        // TODO Check if the `createdAt` value is more than x minutes ago, and if so, mark the `state` of the reset token as `EXPIRED`,
        // TODO send a new-password request to the `auth-service`,
        // TODO if successful, mark the reset-token as `CLOSED`.
    }
}
