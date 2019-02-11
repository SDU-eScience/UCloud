package dk.sdu.cloud.auth.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.ktor.http.HttpMethod

data class LookupUsersRequest(val users: List<String>)
data class UserLookup(val subject: String, val uid: Long, val role: Role)
data class LookupUsersResponse(val results: Map<String, UserLookup?>)

typealias CreateUserAudit = List<CreateSingleUserAudit>
data class CreateSingleUserAudit(val username: String, val role: Role?)

typealias CreateUserRequest = List<CreateSingleUserRequest>
data class CreateSingleUserRequest(val username: String, val password: String?, val role: Role?) {
    override fun toString() = "CreateUserRequest(username = $username, role = $role)"
}

typealias CreateUserResponse = List<CreateSingleUserResponse>
typealias CreateSingleUserResponse = AuthenticationTokens

class ChangePasswordAudit

data class ChangePasswordRequest(val currentPassword: String, val newPassword: String) {
    override fun toString() = "ChangePasswordRequest()"
}

data class LookupUIDRequest(val uids: List<Long>)
data class LookupUIDResponse(val users: Map<Long, UserLookup?>)

object UserDescriptions : RESTDescriptions("auth.users") {
    const val baseContext = "/auth/users"

    val createNewUser =
        callDescriptionWithAudit<CreateUserRequest, CreateUserResponse, CommonErrorMessage, CreateUserAudit> {
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

    val lookupUID = callDescription<LookupUIDRequest, LookupUIDResponse, CommonErrorMessage> {
        name = "lookupUID"
        method = HttpMethod.Post

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"lookup-uid"
        }

        body { bindEntireRequestFromBody() }
    }

    val openUserIterator = callDescription<Unit, FindByStringId, CommonErrorMessage> {
        method = HttpMethod.Post
        name = "openUserIterator"

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"iterator"
            +"open"
        }
    }

    val fetchNextIterator = callDescription<FindByStringId, List<Principal>, CommonErrorMessage> {
        method = HttpMethod.Post
        name = "fetchNextIterator"

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"iterator"
            +"next"
        }

        body { bindEntireRequestFromBody() }
    }

    val closeIterator = callDescription<FindByStringId, Unit, CommonErrorMessage> {
        method = HttpMethod.Post
        name = "closeIterator"

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"iterator"
            +"close"
        }

        body { bindEntireRequestFromBody() }
    }
}
