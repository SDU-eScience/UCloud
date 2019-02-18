package dk.sdu.cloud.auth.http

import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.LoginRequest
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.services.PasswordHashingService
import dk.sdu.cloud.auth.services.UserDAO
import dk.sdu.cloud.auth.services.UserException
import dk.sdu.cloud.auth.util.urlEncoded
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.application.call
import io.ktor.request.receiveParameters
import io.ktor.response.respondRedirect
import io.ktor.util.toMap

class PasswordController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val passwordHashingService: PasswordHashingService,
    private val userDao: UserDAO<DBSession>,
    private val loginResponder: LoginResponder<DBSession>
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(AuthDescriptions.passwordLogin) {
            with(ctx as HttpCall) {
                audit(LoginRequest(null, null))

                val params = try {
                    call.receiveParameters().toMap()
                } catch (ex: Exception) {
                    log.debug(ex.stackTraceToString())
                    okContentAlreadyDelivered()
                    return@implement call.respondRedirect("/auth/login?invalid")
                }

                val username = params["username"]?.firstOrNull()
                val password = params["password"]?.firstOrNull()
                val service = params["service"]?.firstOrNull() ?: return@implement run {
                    okContentAlreadyDelivered()
                    log.info("Missing service")
                    call.respondRedirect("/auth/login?invalid")
                }

                audit(LoginRequest(username, service))
                okContentAlreadyDelivered()

                if (username == null || password == null) {
                    log.info("Missing username or password")
                    return@implement call.respondRedirect("/auth/login?service=${service.urlEncoded}&invalid")
                }

                val user = db.withTransaction {
                    try {
                        userDao.findById(it, username) as? Person.ByPassword ?: throw UserException.NotFound()
                    } catch (ex: UserException.NotFound) {
                        log.info("User not found or is not a password authenticated person")
                        loginResponder.handleUnsuccessfulLogin(call, service)
                        return@implement
                    }
                }

                val validPassword = passwordHashingService.checkPassword(user.password, user.salt, password)
                if (!validPassword) return@implement loginResponder.handleUnsuccessfulLogin(call, service)

                loginResponder.handleSuccessfulLogin(call, service, user)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
