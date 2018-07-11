package dk.sdu.cloud.auth.http

import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.auth.services.PersonUtils
import dk.sdu.cloud.auth.services.UserCreationService
import dk.sdu.cloud.auth.services.UserDAO
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import io.ktor.application.install
import io.ktor.routing.Route
import org.slf4j.LoggerFactory

class UserController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val userDAO: UserDAO<DBSession>,
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

            userCreationService.createUser(person)
            ok(Unit)
        }

        implement(UserDescriptions.changePassword) {
            logEntry(log, it)
            if (!protect()) return@implement

            db.withTransaction { session ->
                userDAO.updatePassword(session, call.request.currentUsername, it.newPassword, it.currentPassword)
                ok(Unit)
            }
        }

        implement(UserDescriptions.lookupUsers) { req ->
            logEntry(log, req)
            if (!protect(PRIVILEGED_ROLES)) return@implement

            ok(
                LookupUsersResponse(
                    db.withTransaction {
                        userDAO.findAllByIds(it, req.users).mapValues {
                            it.value?.let { UserLookup(it.id, it.role) }
                        }
                    }
                )
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(UserController::class.java)
    }
}