package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.http.LoginResponder
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.application.ApplicationCall

private enum class LoginResponse {
    SUCCESS,
    BAD_CREDENTIALS,
    TOO_MANY_REQUESTS
}

class LoginService<Session>(
    private val db: DBSessionFactory<Session>,
    private val passwordService: PasswordHashingService,
    private val users: UserDAO<Session>,
    private val loginAttempts: LoginAttemptDao<Session>,
    private val loginResponder: LoginResponder<Session>
) {
    private suspend fun attemptLogin(username: String, password: String): Pair<Principal?, LoginResponse> {
        return db.withTransaction { session ->
            if (loginAttempts.timeUntilNextAllowedLogin(session, username) != null) {
                Pair(null, LoginResponse.TOO_MANY_REQUESTS)
            } else {
                val user = try {
                    users.findById(session, username) as? Person.ByPassword
                } catch (ex: UserException) {
                    null
                }

                if (user == null) {
                    // Hashing is rather expensive. We always run hashing to avoid giving away the information that
                    // the user does not exist. If we didn't do this an attacker could determine if the user exists by
                    // comparing response times.
                    loginAttempts.logAttempt(session, username)

                    passwordService.hashPassword(password)
                    Pair(null, LoginResponse.BAD_CREDENTIALS)
                } else {
                    val validPassword = passwordService.checkPassword(user.password, user.salt, password)
                    if (validPassword) {
                        Pair(user, LoginResponse.SUCCESS)
                    } else {
                        loginAttempts.logAttempt(session, username)
                        Pair(null, LoginResponse.BAD_CREDENTIALS)
                    }
                }
            }
        }
    }

    suspend fun login(call: ApplicationCall, username: String, password: String, service: String) {
        val (user, status) = attemptLogin(username, password)
        when (status) {
            LoginResponse.SUCCESS -> loginResponder.handleSuccessfulLogin(call, service, user!!)
            LoginResponse.TOO_MANY_REQUESTS -> loginResponder.handleTooManyAttempts()
            LoginResponse.BAD_CREDENTIALS -> {
                db.withTransaction { session ->
                    loginAttempts.logAttempt(session, username)
                }

                loginResponder.handleUnsuccessfulLogin()
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
