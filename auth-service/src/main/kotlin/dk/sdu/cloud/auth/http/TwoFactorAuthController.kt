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

                okContentAlreadyDelivered()

                val codeAsInt =
                    verificationCode.toIntOrNull()
                        ?: return@implement call.respondRedirect("/auth/2fa?challengeId=$challengeId&invalid")

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
            if (submittedViaForm && call.request.accept()?.contains(ContentType.Application.Json.toString()) != true) {
                val params = ArrayList<Pair<String, String>>()

                if (exception is TwoFactorException.InvalidChallenge) {
                    // Make them go back through the entire thing (hopefully)
                    call.respondRedirect("/")
                } else {
                    if (exception != null) {
                        params.add("message" to exception.why)
                    } else {
                        params.add("invalid" to "true")
                    }

                    call.respondRedirect("/auth/2fa" +
                            "?challengeId=$challengeId&" +
                            params.joinToString("&") { it.first.urlEncoded + "=" + it.second.urlEncoded }
                    )
                }
            } else {
                if (exception != null) {
                    call.respond(exception.httpStatusCode, exception.why)
                } else {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        TextContent(
                            defaultMapper.writeValueAsString(CommonErrorMessage("Incorrect code")),
                            ContentType.Application.Json
                        )
                    )
                }
            }
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
