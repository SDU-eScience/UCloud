package dk.sdu.cloud.auth.http

import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.LoginRequest
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.services.PasswordHashingService
import dk.sdu.cloud.auth.services.UserDAO
import dk.sdu.cloud.auth.services.UserException
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveParameters
import io.ktor.util.toMap

class PasswordController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val passwordHashingService: PasswordHashingService,
    private val userDao: UserDAO<DBSession>,
    private val loginResponder: LoginResponder<DBSession>
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
                val user = db.withTransaction {
                    try {
                        userDao.findById(it, username) as? Person.ByPassword ?: loginResponder.handleUnsuccessfulLogin()
                    } catch (ex: UserException) {
                        loginResponder.handleUnsuccessfulLogin()
                    }
                }

                val validPassword = passwordHashingService.checkPassword(user.password, user.salt, password)
                if (validPassword) {
                    loginResponder.handleSuccessfulLogin(call, service, user)
                } else {
                    loginResponder.handleUnsuccessfulLogin()
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
