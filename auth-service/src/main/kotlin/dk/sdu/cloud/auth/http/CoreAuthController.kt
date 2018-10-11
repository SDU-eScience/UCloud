package dk.sdu.cloud.auth.http

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.auth.api.AccessToken
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.TokenExtensionAudit
import dk.sdu.cloud.auth.services.OneTimeTokenDAO
import dk.sdu.cloud.auth.services.ServiceDAO
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.util.urlEncoded
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
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.route
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

class CoreAuthController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val ottDao: OneTimeTokenDAO<DBSession>,
    private val tokenService: TokenService<DBSession>,
    private val enablePasswords: Boolean,
    private val enableWayf: Boolean,
    private val trustedOrigins: Set<String> = setOf("localhost", "cloud.sdu.dk")
) {
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

    fun configure(routing: Routing): Unit = with(routing) {
        route("auth") {
            install(CachingHeaders) {
                options {
                    CachingOptions(CacheControl.NoStore(CacheControl.Visibility.Private))
                }
            }

            intercept(ApplicationCallPipeline.Infrastructure) {
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

            get("2fa") { _ ->
                val challengeId = call.parameters["challengeId"]
                val isInvalid = call.parameters["invalid"] != null
                val message = call.parameters["message"] // TODO Is this a good idea?

                logEntry(log, mapOf("challengeId" to challengeId))

                if (challengeId == null) {
                    call.respondRedirect("/auth/login?invalid")
                    return@get
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

            get("login") { _ ->
                val service = call.parameters["service"]?.let { ServiceDAO.findByName(it) }
                val isInvalid = call.parameters["invalid"] != null

                logEntry(log, mapOf("service" to service, "isInvalid" to isInvalid))


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

            get("login-redirect") { _ ->
                logEntry(log, parameterIncludeFilter = {
                    it == "service" || it == "accessToken" || it == "refreshToken"
                })

                val service = call.parameters["service"]?.let { ServiceDAO.findByName(it) } ?: return@get run {
                    log.info("Missing service")
                    call.respondRedirect("/auth/login")
                }

                val token = call.parameters["accessToken"] ?: return@get run {
                    log.info("Missing access token")
                    call.respondRedirect("/auth/login?invalid&service=${service.name.urlEncoded}")
                }

                val refreshToken = call.parameters["refreshToken"]
                // We don't validate the refresh token for performance. It might also not yet have been serialized into
                // the database. This is not really a problem since it most likely does reach the database before it
                // will be invoked (when the access token expires). Purposefully passing a bad token here might cause
                // the user to be logged out though, and it will require a valid access token. This makes the attack
                // a bit useless.

                val csrfToken = call.parameters["csrfToken"]

                if (TokenValidation.validateOrNull(token) == null) {
                    log.info("Invalid access token")
                    call.respondRedirect("/auth/login?invalid&service=${service.name.urlEncoded}")
                    return@get
                }

                call.respondHtml {
                    head {
                        meta("charset", "UTF-8")
                        title("SDU Login Redirection")
                    }

                    body {
                        p {
                            +("If your browser does not automatically redirect you, then please " +
                                    "click submit.")
                        }

                        form {
                            method = FormMethod.post
                            action = service.endpoint
                            id = "form"

                            input(InputType.hidden) {
                                name = "accessToken"
                                value = token.escapeHTML()
                            }

                            if (refreshToken != null) {
                                input(InputType.hidden) {
                                    name = "refreshToken"
                                    value = refreshToken.escapeHTML()
                                }
                            }

                            if (csrfToken != null) {
                                input(InputType.hidden) {
                                    name = "csrfToken"
                                    value = csrfToken.escapeHTML()
                                }
                            }

                            input(InputType.submit) {
                                value = "Submit"
                            }
                        }

                        script(src = "/auth/redirect.js") {}
                    }
                }
            }


            implement(AuthDescriptions.refresh) { _ ->
                logEntry(log, Unit) { "refreshToken=${call.request.headers[HttpHeaders.Authorization]}" }

                val refreshToken = call.request.bearer ?: return@implement run {
                    error(HttpStatusCode.Unauthorized)
                }

                val token = tokenService.refresh(refreshToken)
                ok(AccessToken(token.accessToken))
            }

            implement(AuthDescriptions.webRefresh) { _ ->
                logEntry(log, Unit)

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
                logEntry(log, req)

                val auditMessage = TokenExtensionAudit(
                    call.securityPrincipal.username, null, null, req.requestedScopes,
                    req.expiresIn
                )

                audit(auditMessage)

                val token = TokenValidation.validateOrNull(req.validJWT)?.toSecurityToken() ?: return@implement error(
                    CommonErrorMessage("Unauthorized"),
                    HttpStatusCode.Unauthorized
                )

                audit(auditMessage.copy(username = token.principal.username, role = token.principal.role))

                ok(tokenService.extendToken(token, req.expiresIn, req.requestedScopes, call.securityPrincipal.username))
            }

            implement(AuthDescriptions.requestOneTimeTokenWithAudience) { req ->
                logEntry(log, req)

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
                logEntry(log, req)
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
            implement(AuthDescriptions.logout) { _ ->
                logEntry(log, Unit) { "refresh = ${call.request.bearer}" }
                okContentDeliveredExternally()

                val refreshToken = call.request.bearer ?: return@implement run {
                    call.respond(HttpStatusCode.Unauthorized)
                }

                tokenService.logout(refreshToken)
                call.respond(HttpStatusCode.NoContent)
            }

            implement(AuthDescriptions.webLogout) { _ ->
                logEntry(log, Unit)

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


