package dk.sdu.cloud.auth.http

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.auth.services.PersonService
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.services.UserAsyncDAO
import dk.sdu.cloud.auth.services.UserCreationService
import dk.sdu.cloud.auth.services.UserIterationService
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.application.call
import io.ktor.features.origin
import io.ktor.http.HttpStatusCode
import io.ktor.request.userAgent

class UserController(
    private val db: AsyncDBSessionFactory,
    private val personService: PersonService,
    private val userDAO: UserAsyncDAO,
    private val userCreationService: UserCreationService,
    private val userIterationService: UserIterationService,
    private val tokenService: TokenService,
    private val unconditionalPasswordResetWhitelist: List<String>,
    private val developmentMode: Boolean = false
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(UserDescriptions.createNewUser) {
            withContext<HttpCall> {
                audit(request.map { CreateSingleUserAudit(it.username, it.role) })

                val principals: List<Principal> = request.map { user ->
                    when (user.role) {
                        Role.SERVICE -> ServicePrincipal(user.username, Role.SERVICE)

                        null, Role.ADMIN, Role.USER -> {
                            val email = user.email
                            if (!email.isNullOrBlank() && email.contains("@")) {
                                personService.createUserByPassword(
                                    firstNames = user.username,
                                    lastName = "N/A",
                                    username = user.username,
                                    role = user.role ?: Role.USER,
                                    password = user.password
                                        ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest),
                                    email = email
                                )
                            }
                            else {
                                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Valid email required")
                            }
                        }

                        else -> {
                            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                        }
                    }
                }

                userCreationService.createUsers(principals)
                ok(principals.map { user ->
                    tokenService.createAndRegisterTokenFor(
                        user,
                        ip = ctx.call.request.origin.remoteHost,
                        userAgent = ctx.call.request.userAgent()
                    )
                })
            }
        }

        implement(UserDescriptions.updateUserInfo) {
            val username = ctx.securityPrincipal.username
            userCreationService.updateUserInfo(
                username,
                request.firstNames,
                request.lastName,
                request.email
            )
            ok(Unit)
        }
        implement(UserDescriptions.getUserInfo) {
            val username = ctx.securityPrincipal.username
            val information = userCreationService.getUserInfo(username)
            ok(GetUserInfoResponse(
                information.email,
                information.firstNames,
                information.lastName
            ))
        }

        implement(UserDescriptions.changePassword) {
            audit(ChangePasswordAudit())

            db.withTransaction { session ->
                userDAO.updatePassword(
                    session,
                    ctx.securityPrincipal.username,
                    request.newPassword,
                    true,
                    request.currentPassword
                )
                ok(Unit)
            }
        }

        implement(UserDescriptions.changePasswordWithReset) {
            audit(ChangePasswordAudit())

            if (ctx.securityPrincipal.username !in unconditionalPasswordResetWhitelist) {
                throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            }

            db.withTransaction { session ->
                userDAO.updatePassword(
                    session,
                    request.userId,
                    request.newPassword,
                    conditionalChange = false,
                    currentPasswordForVerification = null
                )
                ok(Unit)
            }
        }

        implement(UserDescriptions.lookupUsers) {
            ok(
                LookupUsersResponse(
                    db.withTransaction { session ->
                        userDAO.findAllByIds(session, request.users).mapValues { (_, principal) ->
                            principal?.let { UserLookup(it.id, it.uid, it.role) }
                        }
                    }
                )
            )
        }

        implement(UserDescriptions.lookupEmail) {
            ok(
                LookupEmailResponse(
                    db.withTransaction { session ->
                        userDAO.findEmail(session, request.userId)
                            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound, "Email address not found")
                    }
                )
            )
        }

        implement(UserDescriptions.lookupUserWithEmail) {
            ok(
                db.withTransaction { session ->
                    val user = userDAO.findByEmail(session, request.email)
                    LookupUserWithEmailResponse(
                        user.userId,
                        user.firstNames
                    )
                }
            )
        }

        implement(UserDescriptions.toggleEmailSubscription) {
            db.withTransaction { session ->
                userDAO.toggleEmail(session, ctx.securityPrincipal.username)
            }
            ok(Unit)
        }

        implement(UserDescriptions.wantsEmails) {
            val user =
                if (ctx.securityPrincipal.role == Role.SERVICE) {
                    request.username ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Missing username")
                } else {
                    ctx.securityPrincipal.username
                }

            ok(
                db.withTransaction { session ->
                    userDAO.wantEmails(session, user)
                }
            )

        }

        implement(UserDescriptions.lookupUID) {
            ok(
                db.withTransaction { session ->
                    LookupUIDResponse(
                        userDAO.findAllByUIDs(session, request.uids).mapValues { (_, principal) ->
                            principal?.let { UserLookup(it.id, it.uid, it.role) }
                        }
                    )
                }
            )
        }

        implement(UserDescriptions.openUserIterator) {
            checkUserAccessToIterator(ctx.securityPrincipal)
            ok(FindByStringId(userIterationService.create()))
        }

        implement(UserDescriptions.fetchNextIterator) {
            checkUserAccessToIterator(ctx.securityPrincipal)
            ok(userIterationService.fetchNext(request.id))
        }

        implement(UserDescriptions.closeIterator) {
            checkUserAccessToIterator(ctx.securityPrincipal)
            ok(userIterationService.close(request.id))
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
