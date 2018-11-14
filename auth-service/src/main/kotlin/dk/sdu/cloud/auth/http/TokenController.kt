package dk.sdu.cloud.auth.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.AuthDescriptions
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

            ok(token)
        }

        implement(AuthDescriptions.revokeToken) { req ->
            audit(Unit)

            opaqueTokenService.revoke(req.token)
            ok(Unit)
        }
    }
}
