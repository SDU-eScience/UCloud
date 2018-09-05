package dk.sdu.cloud.auth.http

import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.auth.services.PersonUtils
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.services.UserCreationService
import dk.sdu.cloud.auth.services.UserDAO
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import io.ktor.application.install
import io.ktor.response.respond
import io.ktor.routing.Route
import org.slf4j.LoggerFactory

class UserController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val userDAO: UserDAO<DBSession>,
    private val userCreationService: UserCreationService<DBSession>,
    private val tokenService: TokenService<DBSession>
): Controller {
    override val baseContext = UserDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        install(JWTProtection)

        implement(UserDescriptions.createNewUser) { req ->
            logEntry(log, req)
            if (!protect(PRIVILEGED_ROLES)) return@implement

            if (req.role != Role.SERVICE) {
                val person = PersonUtils.createUserByPassword(
                    firstNames = req.username,
                    lastName = "N/A",
                    email = req.username,
                    role = req.role ?: Role.USER,
                    password = req.password
                )

                userCreationService.createUser(person)
                audit(CreateUserAudit(req.username, req.role))
                ok(Unit)
            } else {
                val user = ServicePrincipal(req.username, Role.SERVICE)
                userCreationService.createUser(user)

                call.respond(tokenService.createAndRegisterTokenFor(user))
            }
        }

        implement(UserDescriptions.changePassword) {
            logEntry(log, it)
            if (!protect()) return@implement
            audit(ChangePasswordAudit())

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