package dk.sdu.cloud.auth.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.auth.services.OneTimeTokenDAO
import dk.sdu.cloud.auth.services.ServiceDAO
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.util.urlEncoded
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
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
import kotlinx.html.*
import org.slf4j.LoggerFactory
import java.io.File
import java.net.MalformedURLException
import java.net.URL

// TODO Bad name
class CoreAuthController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val ottDao: OneTimeTokenDAO<DBSession>,
    private val tokenService: TokenService<DBSession>,
    private val enablePasswords: Boolean,
    private val enableWayf: Boolean
) {
    private val log = LoggerFactory.getLogger(CoreAuthController::class.java)

    private suspend fun RESTHandler<*, *, CommonErrorMessage>.requestOriginIsTrusted(): Boolean {
        // TODO Don't hardcode this
        fun isValidHostname(hostname: String): Boolean = hostname in setOf("localhost", "cloud.sdu.dk")

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
                val staticFolder =
                    listOf("./static", "/var/auth-static").map { File(it) }.find { it.exists() && it.isDirectory }

                if (staticFolder != null) {
                    files(staticFolder)
                }

                resource("redirect.js", resourcePackage = "assets")
            }

            get("login") { _ ->
                val service = call.parameters["service"]?.let { ServiceDAO.findByName(it) }
                val isInvalid = call.parameters["invalid"] != null

                logEntry(log, mapOf("service" to service, "isInvalid" to isInvalid))

                fun FlowContent.formControlField(
                    name: String, text: String, iconType: String,
                    type: String = "text"
                ) {
                    div(classes = "input-group") {
                        span(classes = "input-group-addon") {
                            i(classes = "glyphicon glyphicon-$iconType")
                        }
                        input(classes = "form-control") {
                            this.type = InputType.valueOf(type)
                            this.name = name
                            this.id = name
                            this.placeholder = text
                        }
                    }
                }

                call.respondHtml {
                    head {
                        title("SDU Cloud | Login")

                        meta(charset = "utf-8")
                        meta(
                            name = "viewport",
                            content = "width=device-width, initial-scale=1, maximum-scale=1"
                        )

                        link(
                            rel = "stylesheet",
                            href = "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css"
                        )
                        link(rel = "stylesheet", href = "/auth/css/ionicons.css")
                        link(rel = "stylesheet", href = "/auth/css/colors.css")
                        link(rel = "stylesheet", href = "/auth/css/app.css")
                    }

                    body {
                        div(classes = "layout-container") {
                            div(classes = "page-container bg-blue-grey-900") {
                                div(classes = "container-full") {
                                    div(classes = "container container-xs") {
                                        h1(classes = "text-center") {
                                            +"SDUCloud"
                                        }
                                        if (service == null) {
                                            div(classes = "alert alert-danger") {
                                                +"An error has occurred. Try again later."
                                            }
                                        } else {
                                            if (isInvalid) {
                                                div(classes = "alert alert-danger") {
                                                    +"Invalid username or password"
                                                }
                                            }
                                            form(classes = "card b0 form-validate") {
                                                attributes["autocomplete"] = "off"

                                                if (enablePasswords) {
                                                    method = FormMethod.post
                                                    action = "/auth/login"

                                                    div(classes = "card-offset pb0")
                                                    div(classes = "card-heading") {
                                                        div(classes = "card-title text-center") {
                                                            +"Login"
                                                        }
                                                    }
                                                    div(classes = "card-body form-horizontal") {
                                                        input {
                                                            type = InputType.hidden
                                                            value = service.name
                                                            name = "service"
                                                        }

                                                        formControlField(
                                                            name = "username",
                                                            text = "Username",
                                                            iconType = "user"
                                                        )

                                                        formControlField(
                                                            name = "password",
                                                            text = "Password",
                                                            iconType = "lock",
                                                            type = "password"
                                                        )
                                                    }
                                                    button(type = ButtonType.submit) {
                                                        classes = setOf("btn", "btn-primary", "btn-flat")
                                                        +"Authenticate"
                                                    }
                                                }

                                                if (enableWayf) {
                                                    div {
                                                        a(
                                                            href = "/auth/saml/login?service=${service.name.urlEncoded}",
                                                            classes = "btn btn-flat btn-block btn-info"
                                                        ) {
                                                            +"Login using WAYF"
                                                            img(alt = "WAYF Logo", src = "wayf_logo.png") {
                                                                height = "32px"
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        div(classes = "row") {
                                            div(classes = "col-sm-12") {
                                                div(classes = "alert alert-warning") {
                                                    +"Under construction."
                                                }
                                            }
                                        }

                                        div(classes = "row") {
                                            div(classes = "col-xs-3 pull-right") {
                                                img(
                                                    alt = "SDU Cloud Logo",
                                                    src = "sdu_plain_white.png",
                                                    classes = "mv-lg block-center img-responsive align-right"
                                                )
                                            }
                                        }

                                    }
                                }
                            }
                        }
                    }
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

            implement(AuthDescriptions.requestOneTimeTokenWithAudience) {
                logEntry(log, it)

                val bearerToken = call.request.bearer ?: return@implement run {
                    error(HttpStatusCode.Unauthorized)
                }

                val token = tokenService.requestOneTimeToken(bearerToken, *it.audience.split(",").toTypedArray())
                ok(token)
            }

            implement(AuthDescriptions.claim) { req ->
                logEntry(log, req)
                val token = call.request.bearer
                    ?.let { TokenValidation.validateOrNull(it) }
                        ?: return@implement run {
                            error(HttpStatusCode.Unauthorized)
                        }

                val userRole = token.getClaim("role")?.let {
                    try {
                        Role.valueOf(it.asString())
                    } catch (_: Exception) {
                        null
                    }
                } ?: return@implement run {
                    error(HttpStatusCode.Unauthorized)
                }

                if (userRole !in PRIVILEGED_ROLES) return@implement

                val tokenWasClaimed = db.withTransaction {
                    ottDao.claim(it, req.jti, token.subject)
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

                tokenService.logout(refreshToken, csrfToken)
                call.response.cookies.appendExpired(REFRESH_WEB_REFRESH_TOKEN_COOKIE, path = "/")
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }

    companion object {
        const val REFRESH_WEB_CSRF_TOKEN = "X-CSRFToken"
        const val REFRESH_WEB_REFRESH_TOKEN_COOKIE = "refreshToken"
    }
}