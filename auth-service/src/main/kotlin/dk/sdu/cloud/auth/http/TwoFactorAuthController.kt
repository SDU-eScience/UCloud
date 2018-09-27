package dk.sdu.cloud.auth.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.TwoFactorAuthDescriptions
import dk.sdu.cloud.auth.api.TwoFactorStatusResponse
import dk.sdu.cloud.auth.services.TwoFactorChallenge
import dk.sdu.cloud.auth.services.TwoFactorChallengeService
import dk.sdu.cloud.service.*
import io.ktor.application.ApplicationCall
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveParameters
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.util.toMap
import org.slf4j.Logger

class TwoFactorAuthController<DBSession>(
    private val twoFactorChallengeService: TwoFactorChallengeService<DBSession>,
    private val loginResponder: LoginResponder<DBSession>
) : Controller {
    override val baseContext: String = TwoFactorAuthDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(TwoFactorAuthDescriptions.createCredentials) { req ->
            logEntry(log, req)

            ok(twoFactorChallengeService.createSetupCredentialsAndChallenge(call.securityPrincipal.username))
        }

        implement(TwoFactorAuthDescriptions.answerChallenge) { req ->
            logEntry(log, req)

            verifyChallenge(call, req.challengeId, req.verificationCode)
            okContentDeliveredExternally()
        }

        implement(TwoFactorAuthDescriptions.answerChallengeViaForm) { req ->
            logEntry(log, req)

            val params = try {
                call.receiveParameters().toMap()
            } catch (ex: Exception) {
                return@implement call.respondRedirect("/auth/login?invalid")
            }

            val challengeId = params["challengeId"]?.firstOrNull()
            val verificationCode = params["verificationCode"]?.firstOrNull()

            if (challengeId == null || verificationCode == null) {
                return@implement call.respondRedirect("/auth/login?invalid")
            }

            val codeAsInt =
                verificationCode.toIntOrNull() ?: return@implement call.respondRedirect("/auth/2fa?challengeId=$challengeId")

            verifyChallenge(call, challengeId, codeAsInt)
        }

        implement(TwoFactorAuthDescriptions.twoFactorStatus) { req ->
            logEntry(log, req)

            ok(TwoFactorStatusResponse(twoFactorChallengeService.isConnected(call.securityPrincipal.username)))
        }
    }

    private suspend fun verifyChallenge(call: ApplicationCall, challengeId: String, verificationCode: Int) {
        val (verified, challenge) = twoFactorChallengeService.verifyChallenge(challengeId, verificationCode)
        when (challenge) {
            is TwoFactorChallenge.Login -> {
                if (verified) {
                    loginResponder.handleSuccessful2FA(
                        call,
                        challenge.service,
                        challenge.credentials.principal
                    )
                } else {
                    call.respondRedirect("/auth/2fa?challengeId=$challengeId")
                }
            }

            is TwoFactorChallenge.Setup -> {
                if (verified) {
                    try {
                        twoFactorChallengeService.upgradeCredentials(challenge.credentials)
                    } catch (ex: RPCException) {
                        call.respond(ex.httpStatusCode, CommonErrorMessage(ex.why))
                        return
                    }

                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.Unauthorized, CommonErrorMessage("Incorrect code"))
                }
            }
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}