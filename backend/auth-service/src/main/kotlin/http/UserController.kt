package dk.sdu.cloud.auth.http

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.auth.services.PasswordHashingService
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.services.PrincipalService
import dk.sdu.cloud.auth.services.UserType
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.calls.server.remoteHost
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*

class UserController(
    private val db: AsyncDBSessionFactory,
    private val principalService: PrincipalService,
    private val tokenService: TokenService,
    private val unconditionalPasswordResetWhitelist: List<String>,
    private val passwordHashingService: PasswordHashingService,
    private val devMode: Boolean,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(UserDescriptions.createNewUser) {
            withContext<HttpCall> {
                audit(request.map { CreateSingleUserAudit(it.username, it.role) })

                val principals: List<Principal> = request.map { user ->
                    when (user.role) {
                        Role.SERVICE -> {
                            principalService.insert(user.username, Role.SERVICE, UserType.SERVICE)
                            principalService.findByUsername(user.username)
                        }

                        null, Role.ADMIN, Role.USER -> {
                            val email = user.email
                            if (email.isNullOrBlank() || !email.contains("@")) {
                                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Valid email required")
                            }

                            val (hashedPassword, salt) = passwordHashingService.hashPassword(
                                user.password ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                            )

                            principalService.insert(
                                user.username,
                                user.role ?: Role.USER,
                                UserType.PERSON,
                                firstNames = user.firstnames!!,
                                lastName = user.lastname!!,
                                hashedPassword = hashedPassword,
                                salt = salt,
                                email = email,
                                organizationId = user.orgId
                            )

                            principalService.findByUsername(user.username)
                        }
                        else -> {
                            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                        }
                    }
                }

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
            principalService.updateUserInfo(
                ctx.remoteHost ?: throw RPCException("No remote host info?", HttpStatusCode.InternalServerError),
                username,
                request.firstNames,
                request.lastName,
                request.email
            )
            ok(Unit)
        }

        implement(UserDescriptions.getUserInfo) {
            val username = ctx.securityPrincipal.username
            val information = principalService.getUserInfo(username)
            ok(GetUserInfoResponse(
                information.email,
                information.firstNames,
                information.lastName
            ))
        }

        implement(UserDescriptions.verifyUserInfo) {
            val success = principalService.verifyUserInfoUpdate(request.id)
            (ctx as HttpCall).call.respondRedirect("/app/verifyResult?success=$success")
            okContentAlreadyDelivered()
        }

        implement(UserDescriptions.retrievePrincipal) {
            val principal = db.withTransaction {
                principalService.findByUsername(request.username, it)
            }
            ok(
                principal
            )
        }

        implement(UserDescriptions.changePassword) {
            audit(ChangePasswordAudit())

            db.withTransaction { session ->
                principalService.updatePassword(
                    ctx.securityPrincipal.username,
                    request.newPassword,
                    true,
                    request.currentPassword,
                    session
                )
                ok(Unit)
            }
        }

        implement(UserDescriptions.changePasswordWithReset) {
            audit(ChangePasswordAudit())

            if (!devMode && ctx.securityPrincipal.username !in unconditionalPasswordResetWhitelist) {
                throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            }

            db.withTransaction { session ->
                principalService.updatePassword(
                    request.userId,
                    request.newPassword,
                    conditionalChange = false,
                    currentPasswordForVerification = null,
                    session
                )
                ok(Unit)
            }
        }

        implement(UserDescriptions.lookupUsers) {
            ok(
                LookupUsersResponse(
                    db.withTransaction { session ->
                        principalService.findAllByUsername(request.users, session).mapValues { (_, principal) ->
                            principal?.let { UserLookup(it.id, it.role) }
                        }
                    }
                )
            )
        }

        implement(UserDescriptions.lookupEmail) {
            ok(
                LookupEmailResponse(
                    db.withTransaction { session ->
                        principalService.findEmailByUsernameOrNull(request.userId, session)
                            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound, "Email address not found")
                    }
                )
            )
        }

        implement(UserDescriptions.lookupUserWithEmail) {
            ok(
                db.withTransaction { session ->
                    val user = principalService.findByEmail(request.email, session)
                    LookupUserWithEmailResponse(
                        user.userId,
                        user.firstNames,
                        user.lastName
                    )
                }
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
