package dk.sdu.cloud.auth.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.auth.services.*
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import io.ktor.application.install
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import org.slf4j.LoggerFactory

class UserController(
    private val userCreationService: UserCreationService
) {
    fun configure(routing: Route): Unit = with(routing) {
        install(JWTProtection)

        implement(UserDescriptions.createNewUser) {
            logEntry(log, it)
            if (!protect(PRIVILEGED_ROLES)) return@implement

            val person = PersonUtils.createUserByPassword(
                firstNames = it.username,
                lastName = "N/A",
                email = it.username,
                role = it.role ?: Role.USER,
                password = it.password
            )

            try {
                userCreationService.createUser(person)
            } catch (ex: UserException) {
                return@implement when (ex) {
                    is UserException.AlreadyExists -> {
                        error(CommonErrorMessage(ex.why), HttpStatusCode.Conflict)
                    }
                }
            }

            ok(Unit)
        }

        implement(UserDescriptions.changePassword) {
            logEntry(log, it)
            if (!protect()) return@implement

            val username = call.request.currentUsername
            val user = UserDAO.findById(username) as? Person.ByPassword ?: return@implement run {
                error(
                    CommonErrorMessage("Not authenticated via password"),
                    HttpStatusCode.Unauthorized
                )
            }

            if (!user.checkPassword(it.currentPassword)) return@implement run {
                error(
                    CommonErrorMessage("Invalid password"),
                    HttpStatusCode.Forbidden
                )
            }

            val (hashedPassword, salt) = PersonUtils.hashPassword(it.newPassword)

            val userWithNewPassword = user.copy(
                password = hashedPassword,
                salt = salt
            )

            if (!UserDAO.update(userWithNewPassword)) return@implement run {
                log.warn("Unable to update user. Updated rows was not 1")

                error(
                    CommonErrorMessage("Internal server error"),
                    HttpStatusCode.InternalServerError
                )
            }

            ok(Unit)
        }

        implement(UserDescriptions.lookupUsers) {
            logEntry(log, it)
            if (!protect(PRIVILEGED_ROLES)) return@implement

            ok(
                LookupUsersResponse(
                    UserDAO.findAllByIds(it.users).mapValues {
                        it.value?.let { UserLookup(it.id, it.role) }
                    }
                )
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(UserController::class.java)
    }
}