package dk.sdu.cloud.auth.http

import com.onelogin.saml2.settings.Saml2Settings
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveParameters
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import dk.sdu.cloud.auth.services.saml.Auth
import dk.sdu.cloud.auth.services.saml.KtorUtils
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.util.urlDecoded
import dk.sdu.cloud.auth.util.urlEncoded

private const val SAML_RELAY_STATE_PREFIX = "/auth/saml/login?service="

class SAMLController (
        private val authSettings: Saml2Settings,
        private val tokenService: TokenService
){
    fun configure(routing: Routing): Unit = with(routing) {
        route("auth") {
            route("saml") {
                get("metadata") {
                    call.respondText(authSettings.spMetadata, ContentType.Application.Xml)
                }

                get("login") {
                    val service = call.parameters["service"] ?: return@get run {
                        call.respondRedirect("/auth/login")
                    }

                    val relayState = KtorUtils.getSelfURLhost(call) +
                            "${SAML_RELAY_STATE_PREFIX}${service.urlEncoded}"

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

                    val user = tokenService.processSAMLAuthentication(auth)
                    if (user == null) {
                        call.respond(HttpStatusCode.Unauthorized)
                    } else {
                        val token = tokenService.createAndRegisterTokenFor(user)
                        call.respondRedirect("/auth/login-redirect?" +
                                "service=${service.urlEncoded}" +
                                "&accessToken=${token.accessToken.urlEncoded}" +
                                "&refreshToken=${token.refreshToken.urlEncoded}"
                        )
                    }
                }
            }
        }
    }
}
