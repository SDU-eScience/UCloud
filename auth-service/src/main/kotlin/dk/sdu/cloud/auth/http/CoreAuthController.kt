package dk.sdu.cloud.auth.http

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.auth.api.AccessToken
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.RefreshTokenAndCsrf
import dk.sdu.cloud.auth.api.TokenExtensionAudit
import dk.sdu.cloud.auth.services.OneTimeTokenDAO
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.calls.server.bearer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.calls.server.toSecurityToken
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CachingHeaders
import io.ktor.http.CacheControl
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.CachingOptions
import io.ktor.request.header
import io.ktor.response.header
import org.slf4j.LoggerFactory
import java.net.MalformedURLException
import java.net.URL

class CoreAuthController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val ottDao: OneTimeTokenDAO<DBSession>,
    private val tokenService: TokenService<DBSession>,
    private val tokenValidation: TokenValidation<DecodedJWT>,
    private val trustedOrigins: Set<String> = setOf("localhost", "cloud.sdu.dk"),
    private val ktor: Application? = null
) : Controller {
    private val log = LoggerFactory.getLogger(CoreAuthController::class.java)

    private suspend fun CallHandler<*, *, CommonErrorMessage>.verifyRequestOrigin() {
        fun isValidHostname(hostname: String): Boolean = hostname in trustedOrigins

        with(ctx as HttpCall) {
            // First validate referer/origin headers (according to recommendations from OWASP)
            val referer = call.request.header(HttpHeaders.Referrer)
            val origin = call.request.header(HttpHeaders.Origin)

            val hostnameFromHeaders = try {
                if (!origin.isNullOrBlank()) {
                    URL(origin).host
                } else if (!referer.isNullOrBlank()) {
                    URL(referer).host
                } else {
                    // Block request (OWASP recommendation)
                    log.info("Missing referer and origin header")
                    throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                }
            } catch (ex: MalformedURLException) {
                // Block request (OWASP recommendation)
                log.info("Bad URL from header: $referer $origin")
                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
            }

            if (!isValidHostname(hostnameFromHeaders)) {
                log.info(
                    "Origin from headers (referer=$referer, origin=$origin, " +
                            "hostnameFromHeaders=$hostnameFromHeaders) is not trusted."
                )
                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
            }
        }
    }

    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        ktor?.apply {
            install(CachingHeaders) {
                options {
                    // For some reason there is no other way to specify which version we want.
                    // Likely working around a bug.
                    CachingOptions(CacheControl.NoStore(CacheControl.Visibility.Private), null)
                }
            }

            intercept(ApplicationCallPipeline.Features) {
                call.response.header(HttpHeaders.Pragma, "no-cache")
            }
        }

        implement(AuthDescriptions.refresh) {
            withContext<HttpCall> {
                @Suppress("DEPRECATION") // We need to use the bearer token from the header
                val refreshToken = ctx.call.request.bearer ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)

                val token = tokenService.refresh(refreshToken)
                ok(AccessToken(token.accessToken))
            }
        }

        implement(AuthDescriptions.webRefresh) {
            // Note: This is currently the only endpoint in the entire system that needs CSRF protection.
            // That is why this endpoint contains all of the protection stuff directly. If we _ever_ need more
            // endpoints with CSRF protection, this code should be moved out.

            verifyRequestOrigin()

            with(ctx as HttpCall) {
                // Then validate CSRF and refresh token
                val csrfToken = call.request.header(REFRESH_WEB_CSRF_TOKEN)
                    ?: throw RPCException("Missing CSRF token", HttpStatusCode.BadRequest)

                val refreshToken = call.request.cookies[REFRESH_WEB_REFRESH_TOKEN_COOKIE]
                    ?: throw RPCException("Missing refresh token", HttpStatusCode.BadRequest)

                ok(tokenService.refresh(refreshToken, csrfToken))
            }
        }

        implement(AuthDescriptions.tokenExtension) {
            with(ctx as HttpCall) {
                val auditMessage = TokenExtensionAudit(
                    requestedBy = securityPrincipal.username,
                    username = null,
                    role = null,
                    requestedScopes = request.requestedScopes,
                    expiresIn = request.expiresIn,
                    allowRefreshes = request.allowRefreshes
                )

                audit(auditMessage)

                log.debug("Validating input token")
                val token = tokenValidation.validateOrNull(request.validJWT)?.toSecurityToken()
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)

                log.debug("Validating extender role versus input token")
                if (token.principal.role != Role.PROJECT_PROXY && securityPrincipal.role !in Roles.PRIVILEDGED) {
                    throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
                }

                audit(auditMessage.copy(username = token.principal.username, role = token.principal.role))

                log.debug("Extension is OK to proceed")
                ok(
                    tokenService.extendToken(
                        token,
                        request.expiresIn,
                        request.requestedScopes,
                        securityPrincipal.username,
                        request.allowRefreshes
                    )
                )
            }
        }

        implement(AuthDescriptions.requestOneTimeTokenWithAudience) {
            val bearerToken = ctx.bearer ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)

            val audiences = request.audience.split(",")
                .asSequence()
                .map { SecurityScope.parseFromString(it) }
                .toList()

            val token = tokenService.requestOneTimeToken(bearerToken, audiences)
            ok(token)
        }

        implement(AuthDescriptions.claim) {
            val tokenWasClaimed = db.withTransaction {
                ottDao.claim(it, request.jti, ctx.securityPrincipal.username)
            }

            if (tokenWasClaimed) {
                ok(Unit)
            } else {
                error(Unit, statusCode = HttpStatusCode.Conflict)
            }
        }

        implement(AuthDescriptions.logout) {
            withContext<HttpCall> {
                @Suppress("DEPRECATION") // We need to use the bearer token from the request
                val refreshToken =
                    ctx.call.request.bearer ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)

                tokenService.logout(refreshToken)
                ok(Unit)
            }
        }

        implement(AuthDescriptions.webLogout) {
            verifyRequestOrigin()

            with(ctx as HttpCall) {
                // Then validate CSRF and refresh token
                val csrfToken = call.request.header(REFRESH_WEB_CSRF_TOKEN) ?: run {
                    log.info("No CSRF token included")
                    error(CommonErrorMessage("Bad request"), HttpStatusCode.BadRequest)
                    return@implement
                }

                val refreshToken = call.request.cookies[REFRESH_WEB_REFRESH_TOKEN_COOKIE] ?: run {
                    log.info("Missing refresh token")
                    error(CommonErrorMessage("Bad request"), HttpStatusCode.BadRequest)
                    return@implement
                }

                tokenService.logout(refreshToken, csrfToken)
                call.response.cookies.appendExpired(REFRESH_WEB_REFRESH_TOKEN_COOKIE, path = "/")
                ok(Unit)
            }
        }

        implement(AuthDescriptions.bulkInvalidate) {
            // We suppress all exceptions when invalidating in bulk. This is mostly done by services to ensure that all
            // of their tokens are invalidated. This will also ensure that a single invalid token won't cause remaining
            // tokens to not be invalidated.

            audit(Unit)
            val tokens = request.tokens.map { RefreshTokenAndCsrf(it, null) }
            tokenService.bulkLogout(tokens, suppressExceptions = true)
            ok(Unit)
        }
    }


    companion object {
        const val REFRESH_WEB_CSRF_TOKEN = "X-CSRFToken"
        const val REFRESH_WEB_REFRESH_TOKEN_COOKIE = "refreshToken"
        const val REFRESH_WEB_AUTH_STATE_COOKIE = "authState"

        const val MAX_EXTENSION_TIME_IN_MS = 1000 * 60 * 60 * 24
    }
}


