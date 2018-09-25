package dk.sdu.cloud.auth.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.auth.api.Create2FACredentialsResponse
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.TwoFactorAuthDescriptions
import dk.sdu.cloud.auth.services.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import org.slf4j.Logger

class TwoFactorAuthController<DBSession>(
    private val totpService: TOTPService,
    private val qrService: QRService,

    private val db: DBSessionFactory<DBSession>,
    private val userDAO: UserDAO<DBSession>
) : Controller {
    override val baseContext: String = TwoFactorAuthDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(TwoFactorAuthDescriptions.createCredentials) { req ->
            logEntry(log, req)

            // TODO Check if we already have created some
            val credentials = totpService.createSharedSecret()

            val user = db.withTransaction {
                userDAO.findByIdOrNull(it, call.securityPrincipal.username) as? Person
            } ?: return@implement run {
                log.warn("Could not look up user with a valid JWT: ${call.securityPrincipal.username}")
                error(CommonErrorMessage("Internal Server Error"), HttpStatusCode.InternalServerError)
            }

            val uri = credentials.toOTPAuthURI(user.displayName, "SDUCloud").toASCIIString()
            val qrDataUri = qrService.encode(uri, 300, 200).toDataURI()

            ok(Create2FACredentialsResponse(uri, qrDataUri))
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}