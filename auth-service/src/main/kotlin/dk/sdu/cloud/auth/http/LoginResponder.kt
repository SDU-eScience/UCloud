package dk.sdu.cloud.auth.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.AccessTokenAndCsrf
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.services.ServiceDAO
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.services.TwoFactorChallengeService
import dk.sdu.cloud.auth.util.urlEncoded
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import io.ktor.application.ApplicationCall
import io.ktor.content.TextContent
import io.ktor.features.origin
import io.ktor.html.respondHtml
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.request.accept
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.util.date.GMTDate
import io.ktor.util.escapeHTML
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.body
import kotlinx.html.form
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.title

/**
 * Shared utility code for responding to successful login attempts.
 */
class LoginResponder<DBSession>(
    private val tokenService: TokenService<DBSession>,
    private val twoFactorChallengeService: TwoFactorChallengeService<DBSession>
) {
    // Note: This is placed in the http package since it deals pure with http.
    // But it is functionally a service.
    //
    // TODO Should we put services that deal with http logic in http package or services?

    private fun shouldRespondWithJson(call: ApplicationCall): Boolean {
        return call.request.accept()?.contains(ContentType.Application.Json.toString()) == true
    }

    suspend fun handleUnsuccessfulLogin(call: ApplicationCall, service: String) {
        if (shouldRespondWithJson(call)) {
            call.respond(
                TextContent(
                    defaultMapper.writeValueAsString(CommonErrorMessage("Incorrect username or password")),
                    ContentType.Application.Json,
                    HttpStatusCode.Unauthorized
                )
            )
        } else {
            call.respondRedirect("/auth/login?service=${service.urlEncoded}&invalid")
        }
    }

    private fun twoFactorJsonChallenge(loginChallenge: String): Map<String, Any> = mapOf("2fa" to loginChallenge)

    suspend fun handleSuccessfulLogin(call: ApplicationCall, service: String, user: Principal) {
        val resolvedService = ServiceDAO.findByName(service) ?: return run {
            log.info("Given service '$service' was invalid!")
            call.respondRedirect("/")
        }

        val loginChallenge = twoFactorChallengeService.createLoginChallengeOrNull(user.id, service)
        if (loginChallenge != null) {
            if (shouldRespondWithJson(call)) {
                call.respond(
                    TextContent(
                        defaultMapper.writeValueAsString(twoFactorJsonChallenge(loginChallenge)),
                        ContentType.Application.Json
                    )
                )
            } else {
                if (resolvedService.endpointAcceptsStateViaCookie) {
                    appendAuthStateInCookie(call, twoFactorJsonChallenge(loginChallenge))
                    call.respondRedirect(resolvedService.endpoint)
                } else {
                    call.respondRedirect(
                        "/auth/2fa?" +
                                "&challengeId=${loginChallenge.urlEncoded}"
                    )
                }
            }
        } else {
            handleCompletedLogin(call, service, user)
        }
    }

    suspend fun handleSuccessful2FA(call: ApplicationCall, service: String, user: Principal) {
        handleCompletedLogin(call, service, user)
    }

    private suspend fun handleCompletedLogin(call: ApplicationCall, service: String, user: Principal) {
        val resolvedService = ServiceDAO.findByName(service) ?: return run {
            log.info("Missing service: '$service'")
            call.respondRedirect("/")
        }

        val expiry = resolvedService.refreshTokenExpiresAfter?.let { System.currentTimeMillis() + it }
        val (token, refreshToken, csrfToken) = tokenService.createAndRegisterTokenFor(user, refreshTokenExpiry = expiry)

        if (shouldRespondWithJson(call)) {
            call.response.header(HttpHeaders.AccessControlAllowCredentials, "true")
            appendRefreshToken(call, refreshToken, expiry)

            call.respond(
                TextContent(
                    defaultMapper.writeValueAsString(AccessTokenAndCsrf(token, csrfToken)),
                    ContentType.Application.Json
                )
            )
        } else {
            if (resolvedService.endpointAcceptsStateViaCookie) {
                appendAuthStateInCookie(call, AccessTokenAndCsrf(token, csrfToken))
                appendRefreshToken(call, refreshToken, expiry)
                call.respondRedirect(resolvedService.endpoint)
            } else {
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
                            action = resolvedService.endpoint
                            id = "form"

                            input(InputType.hidden) {
                                name = "accessToken"
                                value = token.escapeHTML()
                            }

                            input(InputType.hidden) {
                                name = "refreshToken"
                                value = refreshToken.escapeHTML()
                            }

                            input(InputType.hidden) {
                                name = "csrfToken"
                                value = csrfToken.escapeHTML()
                            }

                            input(InputType.submit) {
                                value = "Submit"
                            }
                        }

                        script(src = "/auth/redirect.js") {}
                    }
                }
            }
        }
    }

    private fun appendAuthStateInCookie(call: ApplicationCall, value: Any) {
        call.response.cookies.append(
            name = CoreAuthController.REFRESH_WEB_AUTH_STATE_COOKIE,
            value = defaultMapper.writeValueAsString(value),
            httpOnly = false,
            expires = GMTDate(System.currentTimeMillis() + 1000L * 60 * 5),
            path = "/"
        )
    }

    private fun appendRefreshToken(call: ApplicationCall, refreshToken: String, expiry: Long?) {
        call.response.cookies.append(
            name = CoreAuthController.REFRESH_WEB_REFRESH_TOKEN_COOKIE,
            value = refreshToken,
            secure = call.request.origin.scheme == "https",
            httpOnly = true,
            expires = GMTDate(expiry ?: System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 30)),
            path = "/",
            extensions = mapOf(
                "SameSite" to "strict"
            )
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
