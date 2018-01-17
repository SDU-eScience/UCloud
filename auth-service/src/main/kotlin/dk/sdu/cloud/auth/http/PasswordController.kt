package dk.sdu.cloud.auth.http

import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.services.UserDAO
import dk.sdu.cloud.auth.services.checkPassword
import dk.sdu.cloud.auth.util.urlEncoded
import dk.sdu.cloud.service.logEntry
import io.ktor.application.call
import io.ktor.request.receiveParameters
import io.ktor.response.respondRedirect
import io.ktor.routing.Routing
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.util.toMap
import org.slf4j.LoggerFactory

class PasswordController(private val tokenService: TokenService) {
    fun configure(routing: Routing): Unit = with(routing) {
        route("auth") {
            post("login") {
                val params = try {
                    call.receiveParameters().toMap()
                } catch (ex: Exception) {
                    logEntry(log, additionalParameters = mapOf("message" to "Missing parameters"))
                    return@post call.respondRedirect("/auth/login?invalid")
                }

                logEntry(log, additionalParameters = mapOf(
                        "username" to params["username"]?.firstOrNull(),
                        "service" to params["service"]?.firstOrNull())
                )

                val username = params["username"]?.firstOrNull()
                val password = params["password"]?.firstOrNull()
                val service = params["service"]?.firstOrNull() ?: return@post run {
                    log.info("Missing service")
                    call.respondRedirect("/auth/login?invalid")
                }

                if (username == null || password == null) {
                    log.info("Missing username or password")
                    return@post call.respondRedirect("/auth/login?service=${service.urlEncoded}&invalid")
                }

                val user = UserDAO.findById(username) as? Person.ByPassword ?: return@post run {
                    log.info("User not found or is not a password authenticated person")
                    call.respondRedirect("/auth/login?service=${service.urlEncoded}&invalid")
                }

                val validPassword = user.checkPassword(password)
                if (!validPassword) return@post run {
                    log.info("Password is not valid")
                    call.respondRedirect("/auth/login?service=${service.urlEncoded}&invalid")
                }

                val token = tokenService.createAndRegisterTokenFor(user)
                call.respondRedirect("/auth/login-redirect?" +
                        "service=${service.urlEncoded}" +
                        "&accessToken=${token.accessToken.urlEncoded}" +
                        "&refreshToken=${token.refreshToken.urlEncoded}"
                )
            }

        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(PasswordController::class.java)
    }
}