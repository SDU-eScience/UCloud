package dk.sdu.cloud.auth.http

import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.services.ServiceDAO
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.services.TwoFactorChallengeService
import dk.sdu.cloud.auth.util.urlEncoded
import dk.sdu.cloud.service.Loggable
import io.ktor.application.ApplicationCall
import io.ktor.html.respondHtml
import io.ktor.response.respondRedirect
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

    suspend fun handleUnsuccessfulLogin(call: ApplicationCall, service: String) {
        call.respondRedirect("/auth/login?service=${service.urlEncoded}&invalid")
    }

    suspend fun handleSuccessfulLogin(call: ApplicationCall, service: String, user: Principal) {
        val loginChallenge = twoFactorChallengeService.createLoginChallengeOrNull(user.id, service)
        if (loginChallenge != null) {
            call.respondRedirect(
                "/auth/2fa?" +
                        "&challengeId=${loginChallenge.urlEncoded}"
            )
        } else {
            handleCompletedLogin(call, service, user)
        }
    }

    suspend fun handleSuccessful2FA(call: ApplicationCall, service: String, user: Principal) {
        handleCompletedLogin(call, service, user)
    }

    private suspend fun handleCompletedLogin(call: ApplicationCall, service: String, user: Principal) {
        val resolvedService = ServiceDAO.findByName(service) ?: return run {
            log.info("Missing service")
            call.respondRedirect("/auth/login")
        }
        val expiry = resolvedService.refreshTokenExpiresAfter?.let { System.currentTimeMillis() + it }

        val (token, refreshToken, csrfToken) = tokenService.createAndRegisterTokenFor(user, refreshTokenExpiry = expiry)

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

    companion object : Loggable {
        override val log = logger()
    }
}
