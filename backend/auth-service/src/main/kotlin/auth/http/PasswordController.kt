package dk.sdu.cloud.auth.http

import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.LoginRequest
import dk.sdu.cloud.auth.services.LoginService
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveParameters
import io.ktor.util.toMap

class PasswordController(
    private val loginService: LoginService<*>
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(AuthDescriptions.passwordLogin) {
            audit(LoginRequest(null, null))

            with(ctx as HttpCall) {
                val params = try {
                    call.receiveParameters().toMap()
                } catch (ex: Exception) {
                    log.debug(ex.stackTraceToString())
                    throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                }

                val username = params["username"]?.firstOrNull()
                val password = params["password"]?.firstOrNull()
                val service = params["service"]?.firstOrNull()
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)

                audit(LoginRequest(username, service))

                if (username == null || password == null) {
                    log.info("Missing username or password")
                    throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                }

                okContentAlreadyDelivered()
                loginService.login(call, username, password, service)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
