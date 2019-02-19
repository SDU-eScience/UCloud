package dk.sdu.cloud.auth.http

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.auth.api.AccessToken
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.LoginPageRequest
import dk.sdu.cloud.auth.api.RefreshTokenAndCsrf
import dk.sdu.cloud.auth.api.TokenExtensionAudit
import dk.sdu.cloud.auth.api.TwoFactorPageRequest
import dk.sdu.cloud.auth.services.OneTimeTokenDAO
import dk.sdu.cloud.auth.services.ServiceDAO
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.util.urlEncoded
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.calls.server.bearer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.calls.server.toSecurityToken
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CachingHeaders
import io.ktor.html.respondHtml
import io.ktor.http.CacheControl
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.CachingOptions
import io.ktor.http.content.files
import io.ktor.http.content.resource
import io.ktor.http.content.static
import io.ktor.request.header
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.route
import io.ktor.routing.routing
import kotlinx.html.ButtonType
import kotlinx.html.FORM
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.i
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.input
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.title
import org.slf4j.LoggerFactory
import java.io.File
import java.net.MalformedURLException
import java.net.URL
import kotlin.collections.set

// TODO FIXME Move the DAOs out of here and into services
// TODO FIXME Move the trustedOrigins out of here
// TODO FIXME Move WAYF into its own controller
// TODO FIXME Split this into multiple controllers
class CoreAuthController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val ottDao: OneTimeTokenDAO<DBSession>,
    private val tokenService: TokenService<DBSession>,
    private val enablePasswords: Boolean,
    private val enableWayf: Boolean,
    private val tokenValidation: TokenValidation<DecodedJWT>,
    private val trustedOrigins: Set<String> = setOf("localhost", "cloud.sdu.dk"),
    private val ktor: Application? = null
) : Controller {
    private val log = LoggerFactory.getLogger(CoreAuthController::class.java)

    private suspend fun CallHandler<*, *, CommonErrorMessage>.requestOriginIsTrusted(): Boolean {
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
                    error(CommonErrorMessage("Bad request"), HttpStatusCode.BadRequest)
                    return false
                }
            } catch (ex: MalformedURLException) {
                // Block request (OWASP recommendation)
                log.info("Bad URL from header: $referer $origin")
                error(CommonErrorMessage("Bad request"), HttpStatusCode.BadRequest)
                return false
            }

            if (!isValidHostname(hostnameFromHeaders)) {
                log.info(
                    "Origin from headers (referer=$referer, origin=$origin, " +
                            "hostnameFromHeaders=$hostnameFromHeaders) is not trusted."
                )
                error(CommonErrorMessage("Bad request"), HttpStatusCode.BadRequest)
                return false
            }

            return true
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

            routing {
                route("auth") {
                    static {
                        val staticFolder = listOf("./static", "/var/auth-static")
                            .asSequence()
                            .map { File(it) }
                            .find { it.exists() && it.isDirectory }

                        if (staticFolder != null) {
                            files(staticFolder)
                        }

                        resource("redirect.js", resourcePackage = "assets")
                    }
                }
            }
        }

        implement(AuthDescriptions.twoFactorPage) {
            with(ctx as HttpCall) {
                val challengeId = call.parameters["challengeId"]
                val isInvalid = call.parameters["invalid"] != null
                val message = call.parameters["message"] // TODO Is this a good idea?

                audit(TwoFactorPageRequest(challengeId, isInvalid, message))
                okContentAlreadyDelivered()

                if (challengeId == null) {
                    call.respondRedirect("/auth/login?invalid")
                    return@implement
                }

                call.respondHtml {
                    formPage(
                        title = "Two Factor Authentication Required",

                        beforeForm = {
                            h3 { +"2FA is Enabled for this Account" }

                            if (isInvalid) {
                                div(classes = "ui message warning") {
                                    +"The verification code you entered was incorrect"
                                }
                            }

                            if (message != null) {
                                div(classes = "ui message warning") {
                                    +message
                                }
                            }
                        },

                        action = "/auth/2fa/challenge/form",
                        method = FormMethod.post,

                        form = {
                            input {
                                type = InputType.hidden
                                value = challengeId
                                name = "challengeId"
                            }

                            formControlField(
                                name = "verificationCode",
                                text = "6-digit code",
                                iconType = "lock",
                                type = "password"
                            )

                            button(type = ButtonType.submit, classes = "ui fluid large blue submit button") {
                                +"Submit"
                            }
                        }
                    )
                }
            }
        }

        implement(AuthDescriptions.loginPage) {
            with(ctx as HttpCall) {
                val service = call.parameters["service"]?.let { ServiceDAO.findByName(it) }
                val isInvalid = call.parameters["invalid"] != null

                audit(LoginPageRequest(service?.name, isInvalid))
                okContentAlreadyDelivered()

                call.respondHtml {
                    formPage(
                        title = "Login",

                        beforeForm = {
                            if (service == null) {
                                div(classes = "ui message error") {
                                    +"An error has occurred. Try again later."
                                }
                            }

                            if (isInvalid) {
                                div(classes = "ui message warning") {
                                    +"Invalid username or password"
                                }
                            }
                        },

                        form = {
                            if (service == null) return@formPage

                            if (enablePasswords) {
                                input {
                                    type = InputType.hidden
                                    value = service.name
                                    name = "service"
                                }

                                formControlField(
                                    name = "username",
                                    iconType = "user",
                                    text = "Username"
                                )

                                formControlField(
                                    name = "password",
                                    text = "Password",
                                    iconType = "lock",
                                    type = "password"
                                )

                                button(type = ButtonType.submit, classes = "ui fluid large blue submit button") {
                                    +"Login"
                                }
                            }

                            if (enablePasswords && enableWayf) {
                                div(classes = "ui horizontal divider") {
                                    +"Or Using SSO"
                                }
                            }


                            if (enableWayf) {
                                div {
                                    a(
                                        href = "/auth/saml/login?service=${service.name.urlEncoded}",
                                        classes = "ui fluid button icon labeled"
                                    ) {
                                        +"Login using WAYF"
                                        img(
                                            alt = "WAYF Logo",
                                            src = "wayf_logo.png",
                                            classes = "wayf icon"
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }

        implement(AuthDescriptions.refresh) {
            with(ctx as HttpCall) {
                val refreshToken = call.request.bearer ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)

                val token = tokenService.refresh(refreshToken)
                ok(AccessToken(token.accessToken))
            }
        }

        implement(AuthDescriptions.webRefresh) {
            // Note: This is currently the only endpoint in the entire system that needs CSRF protection.
            // That is why this endpoint contains all of the protection stuff directly. If we _ever_ need more
            // endpoints with CSRF protection, this code should be moved out.

            if (!requestOriginIsTrusted()) return@implement

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
                val token =
                    tokenValidation.validateOrNull(request.validJWT)?.toSecurityToken() ?: return@implement error(
                        CommonErrorMessage("Unauthorized"),
                        HttpStatusCode.Unauthorized
                    )

                log.debug("Validating extender role versus input token")
                if (token.principal.role != Role.PROJECT_PROXY && securityPrincipal.role !in Roles.PRIVILEDGED) {
                    error(CommonErrorMessage("Unauthorized"), HttpStatusCode.Unauthorized)
                    return@implement
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
            with(ctx as HttpCall) {
                val bearerToken = call.request.bearer ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)

                val audiences = request.audience.split(",")
                    .asSequence()
                    .map { SecurityScope.parseFromString(it) }
                    .toList()

                val token = tokenService.requestOneTimeToken(bearerToken, audiences)
                ok(token)
            }
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
            with(ctx as HttpCall) {
                okContentAlreadyDelivered()

                val refreshToken = call.request.bearer ?: return@implement run {
                    call.respond(HttpStatusCode.Unauthorized)
                }

                tokenService.logout(refreshToken)
                call.respond(HttpStatusCode.NoContent)
            }
        }

        implement(AuthDescriptions.webLogout) {
            if (!requestOriginIsTrusted()) return@implement

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

                okContentAlreadyDelivered()
                tokenService.logout(refreshToken, csrfToken)
                call.response.cookies.appendExpired(REFRESH_WEB_REFRESH_TOKEN_COOKIE, path = "/")
                call.respond(HttpStatusCode.NoContent)
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

    private fun HTML.formPage(
        title: String,
        beforeForm: FlowContent.() -> Unit = {},
        action: String? = null,
        method: FormMethod? = FormMethod.post,
        form: FORM.() -> Unit
    ) {
        head {
            title("SDU Cloud | $title")

            meta(charset = "utf-8")
            meta(
                name = "viewport",
                content = "width=device-width, initial-scale=1, maximum-scale=1"
            )

            link(
                rel = "stylesheet",
                href = "https://cdn.jsdelivr.net/npm/semantic-ui@2.4.0/dist/semantic.min.css"
            )

            link(rel = "stylesheet", href = "/auth/css/app.css")
        }

        body {
            div(classes = "ui middle aligned center aligned grid") {
                div(classes = "column") {
                    h1(classes = "ui header") {
                        +"SDU Cloud"
                    }

                    beforeForm()

                    form(
                        classes = "ui large form",
                        action = action,
                        method = method
                    ) {
                        attributes["autocomplete"] = "off"

                        div(classes = "ui segment") {
                            this@form.form()
                        }
                    }

                    div(classes = "ui message warning") {
                        +"Under construction - Not yet available to the public."
                    }

                    img(
                        alt = "SDU Cloud Logo",
                        src = "sdu_plain_black.png",
                        classes = "ui tiny image floated right"
                    )
                }
            }
        }
    }

    private fun FlowContent.formControlField(
        name: String,
        text: String,
        iconType: String,
        type: String = "text"
    ) {
        div(classes = "field") {
            div(classes = "ui left icon input") {
                i(classes = "$iconType icon")
                input {
                    this.type = InputType.valueOf(type)
                    this.name = name
                    this.id = name
                    this.placeholder = text
                }
            }
        }
    }

    companion object {
        const val REFRESH_WEB_CSRF_TOKEN = "X-CSRFToken"
        const val REFRESH_WEB_REFRESH_TOKEN_COOKIE = "refreshToken"
        const val REFRESH_WEB_AUTH_STATE_COOKIE = "authState"

        const val MAX_EXTENSION_TIME_IN_MS = 1000 * 60 * 60 * 24
    }
}


