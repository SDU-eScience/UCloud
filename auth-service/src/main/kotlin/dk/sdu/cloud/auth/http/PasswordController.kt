package dk.sdu.cloud.auth.http

import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.services.UserDAO
import dk.sdu.cloud.auth.services.checkPassword
import dk.sdu.cloud.auth.util.urlEncoded
import io.ktor.application.call
import io.ktor.request.receiveParameters
import io.ktor.response.respondRedirect
import io.ktor.routing.Routing
import io.ktor.routing.post
import io.ktor.routing.route

class PasswordController(private val tokenService: TokenService) {
    fun configure(routing: Routing): Unit = with(routing) {
        route("auth") {
            post("login") {
                // TODO We end up throwing away the service arg if invalid pass
                // Endpoint for handling basic password logins
                val params = try {
                    call.receiveParameters()
                } catch (ex: Exception) {
                    return@post call.respondRedirect("/auth/login?invalid")
                }

                val username = params["username"]
                val password = params["password"]
                val service = params["service"] ?: return@post run {
                    call.respondRedirect("/auth/login?invalid")
                }

                if (username == null || password == null) {
                    return@post call.respondRedirect("/auth/login?service=${service.urlEncoded}&invalid")
                }

                val user = UserDAO.findById(username) as? Person.ByPassword ?: return@post run {
                    call.respondRedirect("/auth/login?service=${service.urlEncoded}&invalid")
                }

                val validPassword = user.checkPassword(password)
                if (!validPassword) return@post run {
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
}