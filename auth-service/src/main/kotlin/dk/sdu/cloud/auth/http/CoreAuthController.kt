package dk.sdu.cloud.auth.http

import io.ktor.application.call
import io.ktor.content.files
import io.ktor.content.static
import io.ktor.html.respondHtml
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.request.ApplicationRequest
import io.ktor.request.header
import io.ktor.request.receiveParameters
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import kotlinx.html.*
import dk.sdu.cloud.auth.services.ServiceDAO
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.services.UserDAO
import dk.sdu.cloud.auth.services.checkPassword
import dk.sdu.cloud.auth.util.urlEncoded
import org.slf4j.LoggerFactory
import java.io.File

// TODO Bad name
class CoreAuthController(
        private val tokenService: TokenService
) {
    private val ApplicationRequest.bearerToken: String?
        get() {
            val header = call.request.header(HttpHeaders.Authorization) ?: return null

            if (!header.startsWith("Bearer ")) return null
            return header.removePrefix("Bearer ")
        }

    private val log = LoggerFactory.getLogger(CoreAuthController::class.java)

    fun configure(routing: Routing): Unit = with(routing) {
        route("auth") {
            static {
                val folder = File("static")
                files(folder)
            }

            get("login") {
                val service = call.parameters["service"]?.let { ServiceDAO.findByName(it) }
                val isInvalid = call.parameters["invalid"] != null


                fun FlowContent.formControlField(name: String, text: String, iconType: String,
                                                 type: String = "text") {
                    div(classes = "mda-form-group float-label mda-input-group") {
                        div(classes = "mda-form-control") {
                            input(classes = "form-control") {
                                this.type = InputType.valueOf(type)
                                this.name = name
                                this.id = name
                            }

                            div(classes = "mda-form-control-line")

                            label {
                                htmlFor = name
                                +text
                            }
                        }
                        span(classes = "mda-input-group-addon") {
                            em(classes = "ion-ios-$iconType icon-lg")
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

                        link(rel = "stylesheet", href = "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css")
                        link(rel = "stylesheet", href = "/auth/css/ionicons.css")
                        link(rel = "stylesheet", href = "/auth/css/colors.css")
                        link(rel = "stylesheet", href = "/auth/css/app.css")
                    }

                    body {
                        div(classes = "layout-container") {
                            div(classes = "page-container bg-blue-grey-900") {
                                div(classes = "container-full") {
                                    div(classes = "container container-xs") {
                                        img(
                                                alt = "SDU Cloud Logo",
                                                src = "sdu_plain_white.png",
                                                classes = "mv-lg block-center img-responsive"
                                        )
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
                                                method = FormMethod.post
                                                action = "/auth/login"

                                                div(classes = "card-offset pb0")
                                                div(classes = "card-heading") {
                                                    div(classes = "card-title text-center") {
                                                        +"Login"
                                                    }
                                                }
                                                div(classes = "card-body") {
                                                    input {
                                                        type = InputType.hidden
                                                        value = service.name
                                                        name = "service"
                                                    }

                                                    formControlField(
                                                            name = "username",
                                                            text = "Username",
                                                            iconType = "email-outline"
                                                    )

                                                    formControlField(
                                                            name = "password",
                                                            text = "Password",
                                                            iconType = "locked-outline",
                                                            type = "password"
                                                    )
                                                }
                                                button(type = ButtonType.submit) {
                                                    classes = setOf("btn", "btn-primary", "btn-flat")
                                                    +"Authenticate"
                                                }

                                                div {
                                                    a(
                                                            href = "/auth/saml/login?service=${service.name}",
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
                                }
                            }
                        }
                    }
                }
            }

            post("login") {
                // TODO We end up throwing away the service arg if invalid pass
                // Endpoint for handling basic password logins
                val params = try {
                    call.receiveParameters()
                } catch (ex: Exception) {
                    return@post call.respondRedirect("/auth/login?invalid")
                }

                val username = params["username"]
                val password = params["password"]
                val service = params["service"] ?: return@post run {
                    call.respondRedirect("/auth/login?invalid")
                }

                if (username == null || password == null) {
                    return@post call.respondRedirect("/auth/login?service=${service.urlEncoded}&invalid")
                }

                val user = UserDAO.findById(username) ?: return@post run {
                    call.respondRedirect("/auth/login?service=${service.urlEncoded}&invalid")
                }

                val validPassword = user.checkPassword(password)
                if (!validPassword) return@post run {
                    call.respondRedirect("/auth/login?service=${service.urlEncoded}&invalid")
                }

                val token = tokenService.createAndRegisterTokenFor(user)
                call.respondRedirect("/auth/login-redirect?" +
                        "service=${service.urlEncoded}" +
                        "&accessToken=${token.accessToken.urlEncoded}" +
                        "&refreshToken=${token.refreshToken.urlEncoded}"
                )
            }

            get("login-redirect") {
                val service = call.parameters["service"]?.let { ServiceDAO.findByName(it) } ?:
                        return@get run {
                            log.info("missing service")
                            call.respondRedirect("/auth/login")
                        }

                val token = call.parameters["accessToken"] ?: return@get run {
                    log.info("missing access token")
                    call.respondRedirect("/auth/login")
                }

                val refreshToken = call.parameters["refreshToken"]

                call.respondHtml {
                    head {
                        meta("charset", "UTF-8")
                        title("SDU Login Redirection")
                    }

                    body {
                        onLoad = "main()"

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
                                value = token
                            }

                            if (refreshToken != null) {
                                input(InputType.hidden) {
                                    name = "refreshToken"
                                    value = refreshToken
                                }
                            }

                            input(InputType.submit) {
                                value = "Submit"
                            }
                        }

                        script {
                            unsafe {
                                //language=JavaScript
                                +"""
                            function main() {
                                document.querySelector("#form").submit();
                            }
                            """.trimIndent()
                            }
                        }
                    }
                }
            }


            post("refresh") {
                val refreshToken = call.request.bearerToken ?: return@post run {
                    call.respond(HttpStatusCode.Unauthorized)
                }

                val token = try {
                    tokenService.refresh(refreshToken)
                } catch (ex: TokenService.RefreshTokenException) {
                    call.respond(ex.httpCode)
                }

                call.respond(token)
            }

            post("logout") {
                // TODO Invalidate at WAYF
                // TODO Don't implement here
                // TODO Share code with refresh
                val refreshToken = call.request.bearerToken ?: return@post run {
                    call.respond(HttpStatusCode.Unauthorized)
                }
                try {
                    tokenService.logout(refreshToken)
                    call.respond(HttpStatusCode.NoContent)
                } catch (ex: TokenService.RefreshTokenException) {
                    call.respond(ex.httpCode)
                }
            }
        }
    }
}