package dk.sdu.cloud.auth.http

import dk.sdu.cloud.auth.api.TwoFactorAuthDescriptions
import dk.sdu.cloud.auth.services.TwoFactorChallengeService
import dk.sdu.cloud.service.*
import io.ktor.response.respondText
import io.ktor.routing.Route
import org.slf4j.Logger

class TwoFactorAuthController<DBSession>(
    private val twoFactorChallengeService: TwoFactorChallengeService<DBSession>
) : Controller {
    override val baseContext: String = TwoFactorAuthDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(TwoFactorAuthDescriptions.createCredentials) { req ->
            logEntry(log, req)

            ok(twoFactorChallengeService.createSetupCredentialsAndChallenge(call.securityPrincipal.username))
        }

        implement(TwoFactorAuthDescriptions.answerChallenge) { req ->
            logEntry(log, req)

            val result = twoFactorChallengeService.verifyChallenge(req.challengeId, req.verificationCode)
            call.respondText("Correct: $result")
            okContentDeliveredExternally()
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}