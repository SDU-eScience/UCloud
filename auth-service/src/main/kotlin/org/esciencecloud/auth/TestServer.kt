package org.esciencecloud.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.onelogin.saml2.settings.SettingsBuilder
import com.onelogin.saml2.util.Util
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.content.file
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
import java.net.URLEncoder
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

data class RequestAndRefreshToken(val accessToken: String, val refreshToken: String)
data class AccessToken(val accessToken: String)

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
                .withClaim("nickname", user.nickname)
                .withClaim("email", user.email)
                .withIssuer("https://auth.cloud.sdu.dk")
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

    private fun processSAMLAuthentication(auth: Auth) {
        println(auth.authenticated)
        repeat(3) { println() }
        if (auth.authenticated) {
            println(auth.attributes[AttributeURIs.Email])
            println(auth.attributes[AttributeURIs.CommonName])
            println(auth.attributes[AttributeURIs.FamilyName])
            println(auth.attributes[AttributeURIs.EduPersonPrincipalName])
            println(auth.attributes[AttributeURIs.EduPersonPrimaryAffiliation])
            println(auth.attributes[AttributeURIs.EduPersonTargetedId])
            println(auth.attributes[AttributeURIs.SchacHomeOrganization])
            println(auth.authenticated)
        }
    }

    private val String.urlEncoded: String get() = URLEncoder.encode(this, "UTF-8")

    fun createServer(): ApplicationEngine =
            embeddedServer(Netty, port = 8080) {
                install(DefaultHeaders)
                install(ContentNegotiation) {
                    jackson {
                        registerKotlinModule()
                    }
                }

                routing {
                    route("saml") {
                        get("metadata") {
                            call.respondText(authSettings.spMetadata, ContentType.Application.Xml)
                        }

                        get("login") {
                            val service = call.parameters["service"] ?: return@get run {
                                call.respond(HttpStatusCode.BadRequest)
                            }

                            val relayState = KtorUtils.getSelfURLhost(call) +
                                    "/saml/login?service=${service.urlEncoded}"

                            val auth = Auth(authSettings, call)
                            val samlRequestTarget = auth.login(
                                    setNameIdPolicy = true,
                                    returnTo = relayState,
                                    stay = true
                            )

                            // Not possible to check if we made the request without changing library.
                            // Not that important anyway, an attacker could easily forge this.

                            call.respondRedirect(samlRequestTarget, permanent = false)
                        }

                        post("acs") {
                            val auth = Auth(authSettings, call)
                            auth.processResponse()
                            processSAMLAuthentication(auth)
                            println(call.receiveParameters()["RelayState"])

                            call.respondRedirect("/yay")
                        }
                    }

                    static {
                        val folder = File("static")
                        file("login", File(folder, "login.html"))
                        files(folder)
                    }

                    post("login") {
                        // TODO We end up throwing away the service arg if invalid pass
                        // Endpoint for handling basic password logins
                        val params = try {
                            call.receiveParameters()
                        } catch (ex: Exception) {
                            return@post call.respondRedirect("/login?invalid")
                        }

                        val username = params["username"]
                        val password = params["password"]
                        val service = params["service"] ?: return@post run {
                            call.respondRedirect("/login?invalid")
                        }

                        if (username == null || password == null) {
                            return@post call.respondRedirect("/login?invalid")
                        }

                        val user = UserDAO.findById(username) ?: return@post run {
                            call.respondRedirect("/login?invalid")
                        }

                        val validPassword = user.checkPassword(password)
                        if (!validPassword) return@post run {
                            call.respondRedirect("/login?invalid")
                        }

                        val token = createAndRegisterTokenFor(user)
                        call.respondRedirect("/login-redirect?" +
                                "service=${service.urlEncoded}" +
                                "&accessToken=${token.accessToken.urlEncoded}" +
                                "&refreshToken=${token.refreshToken.urlEncoded}"
                        )
                    }

                    get("login-redirect") {
                        val service = call.parameters["service"]?.let { ServiceDAO.findByName(it) } ?: return@get run {
                            log.info("missing service")
                            call.respond(HttpStatusCode.BadRequest)
                        }

                        val token = call.parameters["accessToken"] ?: return@get run {
                            log.info("missing access token")
                            call.respond(HttpStatusCode.BadRequest)
                        }

                        val refreshToken = call.parameters["refreshToken"]

                        call.respondHtml {
                            head {
                                meta("charset", "UTF-8")
                                title("SDU Login Redirection")
                            }

                            body {
                                onLoad = "main()"

                                p { +"If your browser does not automatically redirect you, then please click submit." }

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


fun main(args: Array<String>) {
    fun loadKeysAndInsertIntoProps(properties: Properties): Pair<RSAPublicKey, RSAPrivateKey> {
        val certs = File("certs").takeIf { it.exists() && it.isDirectory } ?:
                throw IllegalStateException("Missing 'certs' folder")
        val x509Cert = File(certs, "cert.pem").takeIf { it.exists() && it.isFile } ?:
                throw IllegalStateException("Missing x509 cert. Expected at: ${certs.absolutePath} with name cert.pem")
        val privateKey = File(certs, "key.pem").takeIf { it.exists() && it.isFile } ?:
                throw IllegalStateException("Missing x509 cert. Expected at: ${certs.absolutePath} with name key.pem")

        val x509Text = x509Cert.readText()
        val privText = privateKey.readText()
        properties["onelogin.saml2.sp.x509cert"] = x509Text
        properties["onelogin.saml2.sp.privatekey"] = privText

        val loadedX509Cert = Util.loadCert(x509Text)
        val loadedPrivKey = Util.loadPrivateKey(privText)

        return Pair(
                loadedX509Cert.publicKey as RSAPublicKey,
                loadedPrivKey as RSAPrivateKey
        )
    }

    val properties = Properties().apply {
        load(AuthServer::class.java.classLoader.getResourceAsStream("saml.properties"))
    }
    val (pub, priv) = loadKeysAndInsertIntoProps(properties)

    AuthServer(properties, pub, priv).createServer().start(wait = true)
}

