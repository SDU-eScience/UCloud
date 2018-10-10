package dk.sdu.cloud.auth.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.ktor.http.HttpMethod

data class Create2FACredentialsResponse(
    val otpAuthUri: String,
    val qrCodeB64Data: String,
    val secret: String,
    val challengeId: String
)

data class AnswerChallengeRequest(val challengeId: String, val verificationCode: Int)
data class TwoFactorStatusResponse(val connected: Boolean)

object TwoFactorAuthDescriptions : RESTDescriptions("auth.2fa") {
    const val baseContext = "/auth/2fa"

    val createCredentials = callDescription<Unit, Create2FACredentialsResponse, CommonErrorMessage> {
        name = "createCredentials"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
        }
    }

    val answerChallenge = callDescription<AnswerChallengeRequest, Unit, CommonErrorMessage> {
        name = "answerChallenge"
        method = HttpMethod.Post

        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"challenge"
        }

        body { bindEntireRequestFromBody() }
    }

    val answerChallengeViaForm = callDescription<Unit, Unit, Unit> {
        name = "answerChallengeViaForm"
        method = HttpMethod.Post

        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"challenge"
            +"form"
        }
    }

    val twoFactorStatus = callDescription<Unit, TwoFactorStatusResponse, CommonErrorMessage> {
        name = "twoFactorStatus"
        method = HttpMethod.Get

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"status"
        }
    }
}