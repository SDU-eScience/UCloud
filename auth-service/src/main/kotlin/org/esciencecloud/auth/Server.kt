package org.esciencecloud.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.onelogin.saml2.settings.SettingsBuilder
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.content.files
import io.ktor.content.static
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.html.respondHtml
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.header
import io.ktor.request.receiveParameters
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.html.*
import org.esciencecloud.auth.saml.AttributeURIs
import org.esciencecloud.auth.saml.Auth
import org.esciencecloud.auth.saml.KtorUtils
import org.esciencecloud.auth.saml.validateOrThrow
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

data class RequestAndRefreshToken(val accessToken: String, val refreshToken: String)
data class AccessToken(val accessToken: String)

private const val SAML_RELAY_STATE_PREFIX = "/auth/saml/login?service="

class AuthServer(properties: Properties, pubKey: RSAPublicKey, privKey: RSAPrivateKey) {
    private val jwtAlg = Algorithm.RSA256(privKey)
    private val authSettings = SettingsBuilder().fromProperties(properties).build().validateOrThrow()

    private fun createAccessTokenForExistingSession(user: User): AccessToken {
        val zone = ZoneId.of("GMT")
        val iat = Date.from(LocalDateTime.now().atZone(zone).toInstant())
        val exp = Date.from(LocalDateTime.now().plusMinutes(30).atZone(zone).toInstant())
        val token = JWT.create()
                .withSubject(user.primaryKey)
                .withClaim("roles", user.roles.joinToString(",") { it.name })
                .withClaim("name", user.fullName)
                .withClaim("email", user.email)
                .withIssuer("https://cloud.sdu.dk/auth")
                .withExpiresAt(exp)
                .withIssuedAt(iat)
                .sign(jwtAlg)

        return AccessToken(token)
    }

    private fun createAndRegisterTokenFor(user: User): RequestAndRefreshToken {
        val token = createAccessTokenForExistingSession(user).accessToken
        val refreshToken = UUID.randomUUID().toString()
        if (!RefreshTokenAndUserDAO.insert(RefreshTokenAndUser(user.primaryKey, refreshToken))) {
            throw RuntimeException("Unable to insert refresh token")
        }
        return RequestAndRefreshToken(token, refreshToken)
    }

    private fun processSAMLAuthentication(auth: Auth): User? {
        if (auth.authenticated) {
            // THIS MIGHT NOT BE AN ACTUAL EMAIL
            println("I have received the following attributes:")
            auth.attributes.forEach { k, v -> println("  $k: $v") }

            /*
            I have received the following attributes:
              schacCountryOfCitizenship: [DK, GB]
              preferredLanguage: [en]
              urn:oid:1.3.6.1.4.1.2428.90.1.4: [12345678]
              mail: [lars.larsen@institution.dk]
              norEduPersonLIN: [12345678]
              urn:oid:2.5.4.10: [Institution]
              eduPersonAssurance: [2]
              eduPersonPrimaryAffiliation: [staff]
              eduPersonScopedAffiliation: [staff@testidp.wayf.dk]
              eduPersonTargetedID: [WAYF-DK-5e9d51a044ff4466fab46ad94a758510723baa13]
              schacHomeOrganization: [testidp.wayf.dk]
              eduPersonPrincipalName: [ll@testidp.wayf.dk]
              sn: [Larsen]
              urn:oid:2.5.4.4: [Larsen]
              urn:oid:2.5.4.3: [Lars L]
              urn:oid:2.16.840.1.113730.3.1.39: [en]
              eduPersonEntitlement: [test]
              urn:oid:1.3.6.1.4.1.25178.1.2.15: [urn:mace:terena.org:schac:personalUniqueID:dk:CPR:0304741234]
              organizationName: [Institution]
              urn:oid:0.9.2342.19200300.100.1.3: [lars.larsen@institution.dk]
              gn: [Lars]
              schacPersonalUniqueID: [urn:mace:terena.org:schac:personalUniqueID:dk:CPR:0304741234]
              urn:oid:2.5.4.42: [Lars]
              urn:oid:1.3.6.1.4.1.5923.1.1.1.10: [WAYF-DK-5e9d51a044ff4466fab46ad94a758510723baa13]
              cn: [Lars L]
              urn:oid:1.3.6.1.4.1.5923.1.1.1.11: [2]
              urn:oid:1.3.6.1.4.1.25178.1.2.9: [testidp.wayf.dk]
              urn:oid:1.3.6.1.4.1.25178.1.2.5: [DK, GB]
              urn:oid:1.3.6.1.4.1.5923.1.1.1.6: [ll@testidp.wayf.dk]
              urn:oid:1.3.6.1.4.1.5923.1.1.1.5: [staff]
              urn:oid:1.3.6.1.4.1.5923.1.1.1.9: [staff@testidp.wayf.dk]
              urn:oid:1.3.6.1.4.1.5923.1.1.1.7: [test]
             */

            val email = auth.attributes[AttributeURIs.EduPersonPrincipalName]?.firstOrNull() ?: return null
            val name = auth.attributes[AttributeURIs.CommonName]?.firstOrNull() ?: return null

            // TODO Fire of Kafka message and validate origin of user
            return UserDAO.findById(email) ?: User.createUserNoPassword(name, email, listOf(Role.USER))
        }
        return null
    }

    private val String.urlEncoded: String get() = URLEncoder.encode(this, "UTF-8")
    private val String.urlDecoded: String get() = URLDecoder.decode(this, "UTF-8")

    fun createServer(): ApplicationEngine =
            embeddedServer(Netty, port = 42300) {
                install(DefaultHeaders)
                install(ContentNegotiation) {
                    jackson {
                        registerKotlinModule()
                    }
                }

                routing {
                    // TODO Redirect to error pages, and not just return a status
                    route("auth") {
                        static {
                            val folder = File("static")
                            //file("login", File(folder, "login.html"))
                            files(folder)
                        }

                        route("saml") {
                            get("metadata") {
                                call.respondText(authSettings.spMetadata, ContentType.Application.Xml)
                            }

                            get("login") {
                                val service = call.parameters["service"] ?: return@get run {
                                    call.respondRedirect("/auth/login")
                                }

                                val relayState = KtorUtils.getSelfURLhost(call) +
                                        "$SAML_RELAY_STATE_PREFIX${service.urlEncoded}"

                                val auth = Auth(authSettings, call)
                                val samlRequestTarget = auth.login(
                                        setNameIdPolicy = true,
                                        returnTo = relayState,
                                        stay = true
                                )

                                call.respondRedirect(samlRequestTarget, permanent = false)
                            }

                            post("acs") {
                                val params = call.receiveParameters()
                                val service = params["RelayState"]?.let {
                                    val index = it.indexOf(SAML_RELAY_STATE_PREFIX)
                                    if (index == -1) return@let null

                                    it.substring(index + SAML_RELAY_STATE_PREFIX.length).urlDecoded
                                } ?: return@post run {
                                    call.respondRedirect("/auth/login")
                                }

                                val auth = Auth(authSettings, call, params)
                                auth.processResponse()

                                val user = processSAMLAuthentication(auth)
                                if (user == null) {
                                    call.respond(HttpStatusCode.Unauthorized)
                                } else {
                                    val token = createAndRegisterTokenFor(user)
                                    call.respondRedirect("/auth/login-redirect?" +
                                            "service=${service.urlEncoded}" +
                                            "&accessToken=${token.accessToken.urlEncoded}" +
                                            "&refreshToken=${token.refreshToken.urlEncoded}"
                                    )
                                }
                            }
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

                            val token = createAndRegisterTokenFor(user)
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
                            val header = call.request.header(HttpHeaders.Authorization) ?: return@post run {
                                call.respond(HttpStatusCode.Unauthorized)
                            }

                            if (!header.startsWith("Bearer ")) return@post call.respond(HttpStatusCode.Unauthorized)
                            val rawToken = header.removePrefix("Bearer ")

                            val token = RefreshTokenAndUserDAO.findById(rawToken) ?: return@post run {
                                call.respond(HttpStatusCode.Unauthorized)
                            }

                            val user = UserDAO.findById(token.associatedUser) ?: return@post run {
                                log.warn("Received a valid token, but was unable to resolve the associated user: " +
                                        token.associatedUser)
                                call.respond(HttpStatusCode.Unauthorized)
                            }

                            call.respond(createAccessTokenForExistingSession(user))
                        }
                    }
                }
            }
}


