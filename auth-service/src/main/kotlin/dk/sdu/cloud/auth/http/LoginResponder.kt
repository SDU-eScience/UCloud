package dk.sdu.cloud.auth.http

import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.services.TwoFactorChallengeService
import dk.sdu.cloud.auth.util.urlEncoded
import io.ktor.application.ApplicationCall
import io.ktor.response.respondRedirect

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
        val token = tokenService.createAndRegisterTokenFor(user)
        call.respondRedirect(
            "/auth/login-redirect?" +
                    "service=${service.urlEncoded}" +
                    "&accessToken=${token.accessToken.urlEncoded}" +
                    "&refreshToken=${token.refreshToken.urlEncoded}" +
                    "&csrfToken=${token.csrfToken.urlEncoded}"
        )
    }
}
