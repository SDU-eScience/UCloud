package dk.sdu.cloud.auth.http

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.auth.services.PersonUtils
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.services.UserCreationService
import dk.sdu.cloud.auth.services.UserDAO
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.routing.Route

class UserController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val userDAO: UserDAO<DBSession>,
    private val userCreationService: UserCreationService<DBSession>,
    private val tokenService: TokenService<DBSession>
) : Controller {
    override val baseContext = UserDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(UserDescriptions.createNewUser) { req ->
            logEntry(log, req)
            audit(CreateUserAudit(req.username, req.role))

            if (req.role != Role.SERVICE) {
                val person = PersonUtils.createUserByPassword(
                    firstNames = req.username,
                    lastName = "N/A",
                    email = req.username,
                    role = req.role ?: Role.USER,
                    password = req.password
                )

                userCreationService.createUser(person)
                val tokens = tokenService.createAndRegisterTokenFor(person)
                ok(tokens)
            } else {
                val user = ServicePrincipal(req.username, Role.SERVICE)
                userCreationService.createUser(user)

                ok(tokenService.createAndRegisterTokenFor(user))
            }
        }

        implement(UserDescriptions.changePassword) {
            logEntry(log, it)
            audit(ChangePasswordAudit())

            db.withTransaction { session ->
                userDAO.updatePassword(session, call.securityPrincipal.username, it.newPassword, it.currentPassword)
                ok(Unit)
            }
        }

        implement(UserDescriptions.lookupUsers) { req ->
            logEntry(log, req)

            ok(
                LookupUsersResponse(
                    db.withTransaction { session ->
                        userDAO.findAllByIds(session, req.users).mapValues { (_, principal) ->
                            principal?.let { UserLookup(it.id, it.role) }
                        }
                    }
                )
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}