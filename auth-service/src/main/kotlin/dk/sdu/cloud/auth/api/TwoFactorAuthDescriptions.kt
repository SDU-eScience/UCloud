package dk.sdu.cloud.auth.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import io.ktor.http.HttpMethod

data class Create2FACredentialsResponse(val otpAuthUri: String, val qrCodeB64Data: String, val challengeId: String)

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
}