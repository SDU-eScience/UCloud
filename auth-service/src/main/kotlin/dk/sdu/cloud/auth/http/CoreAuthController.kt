package dk.sdu.cloud.auth.http

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.auth.api.AccessToken
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.LoginPageRequest
import dk.sdu.cloud.auth.api.TokenExtensionAudit
import dk.sdu.cloud.auth.api.TwoFactorPageRequest
import dk.sdu.cloud.auth.services.OneTimeTokenDAO
import dk.sdu.cloud.auth.services.ServiceDAO
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.util.urlEncoded
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.RESTHandler
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.bearer
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.error
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.service.ok
import dk.sdu.cloud.service.securityPrincipal
import dk.sdu.cloud.service.toSecurityToken
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
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.escapeHTML
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
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.title
import org.slf4j.LoggerFactory
import java.io.File
import java.net.MalformedURLException
import java.net.URL
import java.time.ZonedDateTime

class CoreAuthController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val ottDao: OneTimeTokenDAO<DBSession>,
    private val tokenService: TokenService<DBSession>,
    private val enablePasswords: Boolean,
    private val enableWayf: Boolean,
    private val tokenValidation: TokenValidation<DecodedJWT>,
    private val trustedOrigins: Set<String> = setOf("localhost", "cloud.sdu.dk")
) : Controller {
    override val baseContext = "/auth"
    private val log = LoggerFactory.getLogger(CoreAuthController::class.java)

    private suspend fun RESTHandler<*, *, CommonErrorMessage, *>.requestOriginIsTrusted(): Boolean {
        fun isValidHostname(hostname: String): Boolean = hostname in trustedOrigins

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

    override fun configure(routing: Route): Unit = with(routing) {
        install(CachingHeaders) {
            options {
                // For some reason there is no other way to specify which version we want.
                // Likely working around a bug.
                @Suppress("CAST_NEVER_SUCCEEDS")
                CachingOptions(CacheControl.NoStore(CacheControl.Visibility.Private), null as? ZonedDateTime)
            }
        }

        intercept(ApplicationCallPipeline.Features) {
            call.response.header(HttpHeaders.Pragma, "no-cache")
        }

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

        implement(AuthDescriptions.twoFactorPage) { _ ->
            val challengeId = call.parameters["challengeId"]
            val isInvalid = call.parameters["invalid"] != null
            val message = call.parameters["message"] // TODO Is this a good idea?

            audit(TwoFactorPageRequest(challengeId, isInvalid, message))
            okContentDeliveredExternally()

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

        implement(AuthDescriptions.loginPage) { _ ->
            val service = call.parameters["service"]?.let { ServiceDAO.findByName(it) }
            val isInvalid = call.parameters["invalid"] != null

            audit(LoginPageRequest(service?.name, isInvalid))
            okContentDeliveredExternally()

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

        implement(AuthDescriptions.refresh) {
            val refreshToken = call.request.bearer ?: return@implement run {
                error(HttpStatusCode.Unauthorized)
            }

            val token = tokenService.refresh(refreshToken)
            ok(AccessToken(token.accessToken))
        }

        implement(AuthDescriptions.webRefresh) {
            // Note: This is currently the only endpoint in the entire system that needs CSRF protection.
            // That is why this endpoint contains all of the protection stuff directly. If we _ever_ need more
            // endpoints with CSRF protection, this code should be moved out.

            if (!requestOriginIsTrusted()) return@implement

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

        implement(AuthDescriptions.tokenExtension) { req ->
            val auditMessage = TokenExtensionAudit(
                call.securityPrincipal.username, null, null, req.requestedScopes,
                req.expiresIn
            )

            audit(auditMessage)

            val token = tokenValidation.validateOrNull(req.validJWT)?.toSecurityToken() ?: return@implement error(
                CommonErrorMessage("Unauthorized"),
                HttpStatusCode.Unauthorized
            )

            audit(auditMessage.copy(username = token.principal.username, role = token.principal.role))

            ok(tokenService.extendToken(token, req.expiresIn, req.requestedScopes, call.securityPrincipal.username))
        }

        implement(AuthDescriptions.requestOneTimeTokenWithAudience) { req ->
            val bearerToken = call.request.bearer ?: return@implement run {
                error(HttpStatusCode.Unauthorized)
            }

            val audiences = req.audience.split(",")
                .asSequence()
                .mapNotNull {
                    // Backwards compatible transformation of audiences
                    // Can be deleted when clients no longer use it. Progress tracked in #286
                    when (it) {
                        "downloadFile" -> SecurityScope.construct(
                            listOf("files", "download"),
                            AccessRight.READ_WRITE
                        ).toString()
                        "irods" -> null
                        else -> it
                    }
                }
                .map { SecurityScope.parseFromString(it) }
                .toList()

            val token = tokenService.requestOneTimeToken(bearerToken, audiences)
            ok(token)
        }

        implement(AuthDescriptions.claim) { req ->
            val tokenWasClaimed = db.withTransaction {
                ottDao.claim(it, req.jti, call.securityPrincipal.username)
            }

            if (tokenWasClaimed) {
                ok(HttpStatusCode.NoContent)
            } else {
                error(HttpStatusCode.Conflict)
            }
        }

        // TODO This stuff won't work with cookie based auth
        implement(AuthDescriptions.logout) {
            okContentDeliveredExternally()

            val refreshToken = call.request.bearer ?: return@implement run {
                call.respond(HttpStatusCode.Unauthorized)
            }

            tokenService.logout(refreshToken)
            call.respond(HttpStatusCode.NoContent)
        }

        implement(AuthDescriptions.webLogout) {
            if (!requestOriginIsTrusted()) return@implement

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

            okContentDeliveredExternally()
            tokenService.logout(refreshToken, csrfToken)
            call.response.cookies.appendExpired(REFRESH_WEB_REFRESH_TOKEN_COOKIE, path = "/")
            call.respond(HttpStatusCode.NoContent)
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

        const val MAX_EXTENSION_TIME_IN_MS = 1000 * 60 * 60 * 24
    }
}


