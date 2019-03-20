package dk.sdu.cloud.auth.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod

data class Create2FACredentialsResponse(
    val otpAuthUri: String,
    val qrCodeB64Data: String,
    val secret: String,
    val challengeId: String
)

data class AnswerChallengeRequest(val challengeId: String, val verificationCode: Int)
data class TwoFactorStatusResponse(val connected: Boolean)

object TwoFactorAuthDescriptions : CallDescriptionContainer("auth.2fa") {
    const val baseContext = "/auth/2fa"

    val createCredentials = call<Unit, Create2FACredentialsResponse, CommonErrorMessage>("createCredentials") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
            }
        }
    }

    val answerChallenge = call<AnswerChallengeRequest, Unit, CommonErrorMessage>("answerChallenge") {
        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"challenge"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val twoFactorStatus = call<Unit, TwoFactorStatusResponse, CommonErrorMessage>("twoFactorStatus") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Get
            path {
                using(baseContext)
                +"status"
            }
        }
    }
}
