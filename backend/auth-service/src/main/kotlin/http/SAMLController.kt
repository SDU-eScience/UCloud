package dk.sdu.cloud.auth.http

import com.onelogin.saml2.settings.Saml2Settings
import dk.sdu.cloud.auth.services.IdpService
import dk.sdu.cloud.auth.services.RegistrationService
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.services.saml.KtorUtils
import dk.sdu.cloud.auth.services.saml.SamlRequestProcessor
import dk.sdu.cloud.auth.util.urlDecoded
import dk.sdu.cloud.auth.util.urlEncoded
import dk.sdu.cloud.toReadableStacktrace
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private const val SAML_RELAY_STATE_PREFIX = "/auth/saml/login?service="

typealias SAMLRequestProcessorFactory = (Saml2Settings, ApplicationCall, Parameters) -> SamlRequestProcessor

class SAMLController(
    private val idpService: IdpService,
    private val authSettings: Saml2Settings,
    private val samlProcessorFactory: SAMLRequestProcessorFactory,
    private val tokenService: TokenService,
    private val loginResponder: LoginResponder,
    private val registrationService: RegistrationService,
) {
    fun configure(routing: Route): Unit = with(routing) {
        get("metadata") {
            call.respondText(authSettings.spMetadata, ContentType.Application.Xml)
        }

        get("login") {
            val service = call.parameters["service"] ?: return@get run {
                call.respondRedirect("/auth/login")
            }

            val relayState = KtorUtils.getSelfURLhost(call) +
                    "$SAML_RELAY_STATE_PREFIX${service.urlEncoded}"

            log.debug("Using relayState=$relayState")

            val auth = SamlRequestProcessor(authSettings, call)
            val samlRequestTarget = auth.login(
                setNameIdPolicy = true,
                returnTo = relayState,
                stay = true
            )

            log.debug("Redirecting to $samlRequestTarget")
            call.respondRedirect(samlRequestTarget, permanent = false)
        }

        post("acs") {
            val wayfIdp = idpService.findByTitle("wayf")

            val params = try {
                call.receiveParameters()
            } catch (ex: ContentTransformationException) {
                log.info("ACS failed due to a content transformation error")
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val service = params["RelayState"]?.let {
                val index = it.indexOf(SAML_RELAY_STATE_PREFIX)
                if (index == -1) return@let null

                it.substring(index + SAML_RELAY_STATE_PREFIX.length).urlDecoded
            } ?: return@post run {
                log.info("Missing or invalid relayState")
                call.respondRedirect("/auth/login")
            }

            val auth = samlProcessorFactory(authSettings, call, params)
            auth.processResponse()

            when (val result = tokenService.processSAMLAuthentication(auth)) {
                is TokenService.SamlAuthenticationResult.Success -> {
                    loginResponder.handleSuccessfulLogin(call, service, result.person)
                }

                is TokenService.SamlAuthenticationResult.SuccessButMissingInformation -> {
                    registrationService.submitRegistration(
                        result.firstNames,
                        result.lastName,
                        result.email,
                        result.email != null,
                        result.organization,
                        wayfIdp.id,
                        result.id,
                        call = call,
                    )
                }

                TokenService.SamlAuthenticationResult.Failure -> {
                    call.respond(HttpStatusCode.Unauthorized)
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SAMLController::class.java)
    }
}
