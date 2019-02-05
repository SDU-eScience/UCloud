package dk.sdu.cloud.auth.http

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.auth.api.ChangePasswordAudit
import dk.sdu.cloud.auth.api.CreateSingleUserAudit
import dk.sdu.cloud.auth.api.LookupUsersResponse
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.ProjectProxy
import dk.sdu.cloud.auth.api.ServicePrincipal
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.auth.api.UserLookup
import dk.sdu.cloud.auth.services.PersonService
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.services.UserCreationService
import dk.sdu.cloud.auth.services.UserDAO
import dk.sdu.cloud.auth.services.UserIterationService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.securityPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route

class UserController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val personService: PersonService,
    private val userDAO: UserDAO<DBSession>,
    private val userCreationService: UserCreationService<DBSession>,
    private val userIterationService: UserIterationService,
    private val tokenService: TokenService<DBSession>,
    private val developmentMode: Boolean = false
) : Controller {
    override val baseContext = UserDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(UserDescriptions.createNewUser) { req ->
            audit(req.map { CreateSingleUserAudit(it.username, it.role) })

            val principals: List<Principal> = req.map { user ->
                when (user.role) {
                    Role.SERVICE -> ServicePrincipal(user.username, Role.SERVICE)
                    Role.PROJECT_PROXY -> ProjectProxy(user.username, Role.PROJECT_PROXY)

                    null, Role.ADMIN, Role.USER -> {
                        personService.createUserByPassword(
                            firstNames = user.username,
                            lastName = "N/A",
                            email = user.username,
                            role = user.role ?: Role.USER,
                            password = user.password ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                        )
                    }

                    else -> {
                        throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                    }
                }
            }

            userCreationService.createUsers(principals)
            ok(principals.map { user -> tokenService.createAndRegisterTokenFor(user) })
        }

        implement(UserDescriptions.changePassword) {
            audit(ChangePasswordAudit())

            db.withTransaction { session ->
                userDAO.updatePassword(session, call.securityPrincipal.username, it.newPassword, it.currentPassword)
                ok(Unit)
            }
        }

        implement(UserDescriptions.lookupUsers) { req ->
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

        implement(UserDescriptions.openUserIterator) {
            checkUserAccessToIterator(call.securityPrincipal)
            ok(FindByStringId(userIterationService.create()))
        }

        implement(UserDescriptions.fetchNextIterator) { req ->
            checkUserAccessToIterator(call.securityPrincipal)
            ok(userIterationService.fetchNext(req.id))
        }

        implement(UserDescriptions.closeIterator) { req ->
            checkUserAccessToIterator(call.securityPrincipal)
            ok(userIterationService.close(req.id))
        }
    }

    private fun checkUserAccessToIterator(principal: SecurityPrincipal) {
        if (developmentMode) return
        val allowed = principal.role in allowedRoles && principal.username in allowedUsernames
        if (!allowed) throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
    }

    private val allowedRoles = setOf(Role.SERVICE, Role.ADMIN)
    private val allowedUsernames = setOf("_auth", "_accounting", "_accounting-storage", "admin@dev")

    companion object : Loggable {
        override val log = logger()
    }
}
