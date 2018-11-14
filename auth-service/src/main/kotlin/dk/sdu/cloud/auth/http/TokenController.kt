package dk.sdu.cloud.auth.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.ServicePrincipal
import dk.sdu.cloud.auth.services.OpaqueTokenService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.implement
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route

class TokenController(
    private val opaqueTokenService: OpaqueTokenService<*>
) : Controller {
    override val baseContext = AuthDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(AuthDescriptions.verifyToken) { req ->
            audit(Unit)

            val token = opaqueTokenService.find(req.token) ?: return@implement error(
                CommonErrorMessage("Not found"),
                HttpStatusCode.NotFound
            )

            ok(
                SecurityPrincipalToken(
                    token.user.toSecurityPrincipal(),
                    token.scopes,
                    token.createdAt,
                    token.expiresAt ?: Long.MAX_VALUE,
                    token.sessionReference,
                    token.extendedBy
                )
            )
        }

        implement(AuthDescriptions.revokeToken) { req ->
            audit(Unit)

            opaqueTokenService.revoke(req.token)
            ok(Unit)
        }
    }

    private fun Principal.toSecurityPrincipal(): SecurityPrincipal = when (this) {
        is Person -> SecurityPrincipal(
            id,
            role,
            firstNames,
            lastName
        )

        is ServicePrincipal -> SecurityPrincipal(
            id,
            role,
            "N/A",
            "N/A"
        )
    }
}
