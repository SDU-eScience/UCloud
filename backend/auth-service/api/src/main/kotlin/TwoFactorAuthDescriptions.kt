package dk.sdu.cloud.auth.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
@UCloudApiStable
data class Create2FACredentialsResponse(
    val otpAuthUri: String,
    val qrCodeB64Data: String,
    val secret: String,
    val challengeId: String
)

@Serializable
@UCloudApiStable
data class AnswerChallengeRequest(val challengeId: String, val verificationCode: Int) {
    override fun toString(): String {
        return "AnswerChallengeRequest(challengeId='$challengeId')"
    }
}

@Serializable
@UCloudApiStable
data class TwoFactorStatusResponse(val connected: Boolean)

@UCloudApiStable
object TwoFactorAuthDescriptions : CallDescriptionContainer("auth.twofactor") {
    const val baseContext = "/auth/2fa"

    init {
        title = "Two Factor Authentication (2FA)"
        description = """
UCloud supports 2FA for all users using a TOTP backend.
            
UCloud, for the most part, relies on the user's organization to enforce best practices. UCloud can be configured to
require additional factors of authentication via WAYF. On top of this UCloud allows you to optionally add TOTP based
two-factor authentication.

https://cloud.sdu.dk uses this by enforcing 2FA of all users authenticated via the `password` backend.

        """.trimIndent()
    }

    override fun documentation() {
        useCase("creating-2fa-credentials", "Creating 2FA credentials") {
            val user = basicUser()
            success(
                twoFactorStatus,
                Unit,
                TwoFactorStatusResponse(connected = false),
                user
            )

            success(
                createCredentials,
                Unit,
                Create2FACredentialsResponse(
                    "OTP URI",
                    "QR CODE BASE64 ENCODED",
                    "SECRET",
                    "CHALLENGE ID"
                ),
                user
            )

            success(
                answerChallenge,
                AnswerChallengeRequest(
                    "CHALLENGE ID",
                    999999
                ),
                Unit,
                user
            )

            success(
                twoFactorStatus,
                Unit,
                TwoFactorStatusResponse(connected = true),
                user
            )
        }
    }

    val createCredentials = call("createCredentials", Unit.serializer(), Create2FACredentialsResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
            }
        }

        documentation {
            summary = "Creates initial 2FA credentials and bootstraps a challenge for those credentials"
        }
    }

    val answerChallenge = call("answerChallenge", AnswerChallengeRequest.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
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

        documentation {
            summary = "Answers a challenge previously issued by createCredentials"
        }
    }

    val twoFactorStatus = call("twoFactorStatus", Unit.serializer(), TwoFactorStatusResponse.serializer(), CommonErrorMessage.serializer()) {
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

        documentation {
            summary = "Retrieves the 2FA status of the currently authenticated user"
        }
    }
}
