package dk.sdu.cloud.password.reset.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
data class PasswordResetRequest(val email: String)

@Serializable
data class NewPasswordRequest(val token: String, val newPassword: String)

@TSTopLevel
object PasswordResetDescriptions : CallDescriptionContainer("password.reset") {
    init {
        title = "Password Reset"
        description = """
Users that authenticate with the password backend have the ability to reset their password.

Users have the ability to reset their password from the Login page, using their email address.
When the user submits an email address, the response will always be a `200 OK` (for security reasons).

In case the email address is valid, the `password-reset-service` will act as follows:

 - Generate a random `token`.
 - Send a link with the `token` to the provided email address.
 - Save the token along with the user's id and an `expiresAt` timestamp
   (set to `now + 30 minutes`) in the database.

When the user click's the link in the email sent from the service, he/she will be taken to a
"Enter new password" page. Upon submission, the `password-reset-service` will check if the token is
valid (i.e. if it exists in the database table) and not expired (`now < expiresAt`). If so, a
request with be sent to the `auth-service` to change the password through an end-point only
accessible to password-reset-service.

${ApiConventions.nonConformingApiWarning}

        """.trimIndent()
    }
    val baseContext = "/api/password/reset"

    val reset = call("reset", PasswordResetRequest.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val newPassword = call("newPassword", NewPasswordRequest.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"new"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
