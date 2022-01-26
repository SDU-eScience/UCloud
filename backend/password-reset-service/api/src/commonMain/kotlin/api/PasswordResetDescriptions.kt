package dk.sdu.cloud.password.reset.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@Serializable
data class PasswordResetRequest(val email: String)

@Serializable
data class NewPasswordRequest(val token: String, val newPassword: String)

@TSTopLevel
object PasswordResetDescriptions : CallDescriptionContainer("password.reset") {
    val baseContext = "/api/password/reset"

    val reset = call<PasswordResetRequest, Unit, CommonErrorMessage>("reset") {
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

    val newPassword = call<NewPasswordRequest, Unit, CommonErrorMessage>("newPassword") {
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
