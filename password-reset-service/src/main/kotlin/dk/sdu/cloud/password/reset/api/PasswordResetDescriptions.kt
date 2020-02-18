package dk.sdu.cloud.password.reset.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod

data class PasswordResetRequest(val email: String)
data class NewPasswordRequest(val token: String, val newPassword: String)

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
