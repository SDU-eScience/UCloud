package dk.sdu.cloud.auth.api

import dk.sdu.cloud.Role
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.ktor.http.HttpMethod

data class LookupUsersRequest(val users: List<String>)
data class UserLookup(val subject: String, val role: Role)
data class LookupUsersResponse(val results: Map<String, UserLookup?>)

data class CreateUserAudit(val username: String, val role: Role?)

data class CreateUserRequest(val username: String, val password: String, val role: Role?) {
    override fun toString() = "CreateUserRequest(username = $username, role = $role)"
}

typealias CreateUserResponse = AuthenticationTokens

class ChangePasswordAudit

data class ChangePasswordRequest(val currentPassword: String, val newPassword: String) {
    override fun toString() = "ChangePasswordRequest()"
}

object UserDescriptions : RESTDescriptions("auth.users") {
    const val baseContext = "/auth/users"

    val createNewUser = callDescriptionWithAudit<CreateUserRequest, CreateUserResponse, CommonErrorMessage, CreateUserAudit> {
        method = HttpMethod.Post
        name = "createNewUser"

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"register"
        }

        body { bindEntireRequestFromBody() }
    }

    val changePassword =
        callDescriptionWithAudit<ChangePasswordRequest, Unit, CommonErrorMessage, ChangePasswordAudit> {
            method = HttpMethod.Post
            name = "changePassword"

            auth {
                roles = Roles.END_USER
                access = AccessRight.READ_WRITE
            }

            path {
                using(baseContext)
                +"password"
            }

            body { bindEntireRequestFromBody() }
        }

    val lookupUsers = callDescription<LookupUsersRequest, LookupUsersResponse, CommonErrorMessage> {
        method = HttpMethod.Post
        name = "lookupUsers"

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"lookup"
        }

        body { bindEntireRequestFromBody() }
    }
}