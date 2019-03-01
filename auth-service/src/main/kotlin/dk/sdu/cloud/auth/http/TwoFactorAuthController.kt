package dk.sdu.cloud.auth.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.TwoFactorAuthDescriptions
import dk.sdu.cloud.auth.api.TwoFactorStatusResponse
import dk.sdu.cloud.auth.services.TwoFactorChallenge
import dk.sdu.cloud.auth.services.TwoFactorChallengeService
import dk.sdu.cloud.auth.services.TwoFactorException
import dk.sdu.cloud.auth.util.urlEncoded
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.accept
import io.ktor.request.receiveParameters
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.util.toMap
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

        implement(TwoFactorAuthDescriptions.answerChallengeViaForm) {
            with(ctx as HttpCall) {
                okContentAlreadyDelivered()

                val params = try {
                    call.receiveParameters().toMap()
                } catch (ex: Exception) {
                    return@implement loginResponder.handleUnsuccessful2FA(call, true, null, null)
                }

                val challengeId = params["challengeId"]?.firstOrNull()
                val verificationCode = params["verificationCode"]?.firstOrNull()

                if (challengeId == null || verificationCode == null) {
                    log.debug("Bad request")
                    return@implement loginResponder.handleUnsuccessful2FA(call, true, null, null)
                }

                val codeAsInt =
                    verificationCode.toIntOrNull()
                        ?: return@implement loginResponder.handleUnsuccessful2FA(call, true, null, challengeId)

                verifyChallenge(call, challengeId, codeAsInt, submittedViaForm = true)
            }
        }

        implement(TwoFactorAuthDescriptions.twoFactorStatus) {
            ok(TwoFactorStatusResponse(twoFactorChallengeService.isConnected(ctx.securityPrincipal.username)))
        }
    }

    private suspend fun verifyChallenge(
        call: ApplicationCall,
        challengeId: String,
        verificationCode: Int,
        submittedViaForm: Boolean = false
    ) {
        var exception: RPCException? = null
        val (verified, challenge) = try {
            twoFactorChallengeService.verifyChallenge(challengeId, verificationCode)
        } catch (ex: RPCException) {
            exception = ex
            false to null
        }

        suspend fun fail() {
            loginResponder.handleUnsuccessful2FA(call, submittedViaForm, exception, challengeId)
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
                    try {
                        twoFactorChallengeService.upgradeCredentials(challenge.credentials)
                    } catch (ex: RPCException) {
                        call.respond(ex.httpStatusCode, CommonErrorMessage(ex.why))
                        return
                    }

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
