package dk.sdu.cloud.auth.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.ktor.http.HttpMethod

data class Create2FACredentialsResponse(val otpAuthUri: String, val qrCodeB64Data: String, val challengeId: String)
data class AnswerChallengeRequest(val challengeId: String, val verificationCode: Int)

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
}