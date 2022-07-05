package dk.sdu.cloud.auth.http

import dk.sdu.cloud.auth.api.OptionalAuthenticationTokens
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.services.ServiceDAO
import dk.sdu.cloud.auth.services.ServiceMode
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.services.TwoFactorChallengeService
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.escapeHtml
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.date.GMTDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString

/**
 * Shared utility code for responding to successful login attempts.
 */
class LoginResponder(
    private val tokenService: TokenService,
    private val twoFactorChallengeService: TwoFactorChallengeService
) {
    // Note: This is placed in the http package since it deals pure with http.
    // But it is functionally a service.

    private fun shouldRespondWithJson(call: ApplicationCall): Boolean {
        return call.request.accept()?.contains(ContentType.Application.Json.toString()) == true
    }

    fun handleUnsuccessfulLogin(): Nothing {
        throw RPCException("Incorrect username or password", HttpStatusCode.Unauthorized)
    }

    fun handleTooManyAttempts(): Nothing {
        throw RPCException(
            "Too many requests. Please wait a few minutes and try again.",
            HttpStatusCode.TooManyRequests
        )
    }

    private fun twoFactorJsonChallenge(loginChallenge: String): Map<String, String> = mapOf("2fa" to loginChallenge)

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
                        defaultMapper.encodeToString(twoFactorJsonChallenge(loginChallenge)),
                        ContentType.Application.Json
                    )
                )
            } else {
                // This will happen if we get a redirect from SAML (WAYF)
                appendAuthStateInCookie(
                    call,
                    twoFactorJsonChallenge(loginChallenge),
                    MapSerializer(String.serializer(), String.serializer())
                )
                call.respondRedirect(resolvedService.endpoint)
            }
        } else {
            handleCompletedLogin(call, service, user)
        }
    }

    fun handleUnsuccessful2FA(): Nothing {
        throw RPCException("Incorrect code", HttpStatusCode.Unauthorized)
    }

    suspend fun handleSuccessful2FA(call: ApplicationCall, service: String, user: Principal) {
        handleCompletedLogin(call, service, user)
    }

    private suspend fun handleCompletedLogin(call: ApplicationCall, service: String, user: Principal) {
        val resolvedService = ServiceDAO.findByName(service) ?: return run {
            log.info("Missing service: '$service'")
            call.respondRedirect("/")
        }

        val expiry = resolvedService.refreshTokenExpiresAfter?.let { Time.now() + it }
        val (token, refreshToken, csrfToken) = tokenService.createAndRegisterTokenFor(
            user,
            refreshTokenExpiry = expiry,
            userAgent = call.request.userAgent(),
            ip = call.request.origin.remoteHost
        )

        val tokens = when (resolvedService.serviceMode) {
            ServiceMode.WEB -> OptionalAuthenticationTokens(
                accessToken = token,
                csrfToken = csrfToken
            )

            ServiceMode.APPLICATION -> OptionalAuthenticationTokens(
                accessToken = token,
                csrfToken = csrfToken,
                refreshToken = refreshToken
            )
        }

        if (shouldRespondWithJson(call)) {
            call.response.header(HttpHeaders.AccessControlAllowCredentials, "true")
            appendRefreshToken(call, refreshToken, expiry)

            call.respond(
                TextContent(
                    defaultMapper.encodeToString(tokens),
                    ContentType.Application.Json
                )
            )
        } else {
            // This will happen if we get a redirect from SAML (WAYF)
            appendAuthStateInCookie(call, tokens, OptionalAuthenticationTokens.serializer())
            appendRefreshToken(call, refreshToken, expiry)

            // Using a 301 redirect causes Apple browsers (at least Safari likely more) to ignore the cookie.
            // Using a redirect via HTML works.
            call.respondText(ContentType.Text.Html, io.ktor.http.HttpStatusCode.OK) {
                //language=html
                """
                <!DOCTYPE html>
                <html lang="en">
                    <head>
                        <title>UCloud</title>
                        <meta http-equiv='refresh' content="0; url='${escapeHtml(resolvedService.endpoint)}'" />
                    </head>
                    <body>
                        <p>Please click <a href='${escapeHtml(resolvedService.endpoint)}'>here</a> if your browser does not redirect you automatically</p>
                    </body>
                </html>
                """
            }
        }
    }

    private fun <T> appendAuthStateInCookie(call: ApplicationCall, value: T, serializer: KSerializer<T>) {
        call.response.cookies.append(
            name = CoreAuthController.REFRESH_WEB_AUTH_STATE_COOKIE,
            value = defaultMapper.encodeToString(serializer, value),
            secure = call.request.origin.scheme == "https",
            httpOnly = false,
            expires = GMTDate(Time.now() + 1000L * 60 * 5),
            path = "/",
            extensions = mapOf(
                "SameSite" to "strict"
            )
        )
    }

    private fun appendRefreshToken(call: ApplicationCall, refreshToken: String, expiry: Long?) {
        call.response.cookies.append(
            name = CoreAuthController.REFRESH_WEB_REFRESH_TOKEN_COOKIE,
            value = refreshToken,
            secure = call.request.origin.scheme == "https",
            httpOnly = true,
            expires = GMTDate(expiry ?: Time.now() + (1000L * 60 * 60 * 24 * 30)),
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
