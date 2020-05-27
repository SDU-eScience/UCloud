package dk.sdu.cloud.auth.http

import dk.sdu.cloud.auth.api.TwoFactorAuthDescriptions
import dk.sdu.cloud.auth.api.TwoFactorStatusResponse
import dk.sdu.cloud.auth.services.TwoFactorChallenge
import dk.sdu.cloud.auth.services.TwoFactorChallengeService
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import org.slf4j.Logger

class TwoFactorAuthController<DBSession>(
    private val twoFactorChallengeService: TwoFactorChallengeService<DBSession>,
    private val loginResponder: LoginResponder<DBSession>
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(TwoFactorAuthDescriptions.createCredentials) {
            ok(twoFactorChallengeService.createSetupCredentialsAndChallenge(ctx.securityPrincipal.username))
        }

        implement(TwoFactorAuthDescriptions.answerChallenge) {
            verifyChallenge((ctx as HttpCall).call, request.challengeId, request.verificationCode)
            okContentAlreadyDelivered()
        }

        implement(TwoFactorAuthDescriptions.twoFactorStatus) {
            ok(TwoFactorStatusResponse(twoFactorChallengeService.isConnected(ctx.securityPrincipal.username)))
        }
    }

    private suspend fun verifyChallenge(
        call: ApplicationCall,
        challengeId: String,
        verificationCode: Int
    ) {
        val (verified, challenge) = try {
            twoFactorChallengeService.verifyChallenge(challengeId, verificationCode)
        } catch (ex: RPCException) {
            false to null
        }

        fun fail(): Nothing {
            loginResponder.handleUnsuccessful2FA()
        }

        if (!verified && challenge == null) {
            fail()
        }

        when (challenge) {
            is TwoFactorChallenge.Login -> {
                if (verified) {
                    loginResponder.handleSuccessful2FA(
                        call,
                        challenge.service,
                        challenge.credentials.principal
                    )
                } else {
                    fail()
                }
            }

            is TwoFactorChallenge.Setup -> {
                if (verified) {
                    twoFactorChallengeService.upgradeCredentials(challenge.credentials)
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    fail()
                }
            }
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
