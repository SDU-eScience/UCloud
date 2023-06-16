package dk.sdu.cloud.auth.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

@Serializable
data class LookupUsersRequest(val users: List<String>)

@Serializable
data class UserLookup(val subject: String, val role: Role)

@Serializable
data class LookupUsersResponse(val results: Map<String, UserLookup?>)

@Serializable
data class LookupEmailRequest(val userId: String)

@Serializable
data class LookupEmailResponse(val email: String)

@Serializable
data class LookupUserWithEmailRequest(val email: String)
@Serializable
data class LookupUserWithEmailResponse(val userId: String, val firstNames: String, val lastName: String)

typealias CreateUserAudit = List<CreateSingleUserAudit>

@Serializable
data class CreateSingleUserAudit(val username: String, val role: Role? = null)

typealias CreateUserRequest = List<CreateSingleUserRequest>

@Serializable
data class CreateSingleUserRequest(
    val username: String,
    val password: String? = null,
    val email: String? = null,
    val role: Role? = null,
    val firstnames: String? = null,
    val lastname: String? = null,
    val orgId: String? = null
) {
    override fun toString() = "CreateUserRequest(username = $username, role = $role, organization = $orgId)"
}

typealias CreateUserResponse = List<CreateSingleUserResponse>
typealias CreateSingleUserResponse = AuthenticationTokens

@Serializable
data class UpdateUserInfoRequest(
    val email: String? = null,
    val firstNames: String? = null,
    val lastName: String? = null,
)
typealias UpdateUserInfoResponse = Unit

typealias GetUserInfoRequest = Unit
@Serializable
data class GetUserInfoResponse(
    val email: String? = null,
    val firstNames: String? = null,
    val lastName: String? = null,
    val organization: String? = null,
)

@Serializable
data class GetPrincipalRequest(
    val username: String
)
typealias GetPrincipalResponse = Principal

@Serializable
class ChangePasswordAudit

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String) {
    override fun toString() = "ChangePasswordRequest()"
}

@Serializable
data class ChangePasswordWithResetRequest(val userId: String, val newPassword: String)

object UserDescriptions : CallDescriptionContainer("auth.users") {
    const val baseContext = "/auth/users"

    init {
        title = "Users"
        description = """
Users form the basis of all authentication in UCloud.

Users in UCloud are authenticated in one of two ways:

1. `WAYF`: The user is created on first login by using their login credentials from WAYF (Where Are You From) 
which is a identity federation allowing the reuse of logins from most danish and north atlantic 
research and education centers on external sites. 

2. `PASSWORD`: The users is created by an ADMIN of the system. This is mainly used to give access to people 
outside WAYF. When a user is a PASSWORD user then there is also a requirement of 2FA. The 2FA is setup after 
first login.

Each user has a role defining their privileges on the UCloud system. See $TYPE_REF dk.sdu.cloud.Role for more details.

${ApiConventions.nonConformingApiWarning}
            
        """.trimIndent()
    }

    val createNewUser = call("createNewUser", ListSerializer(CreateSingleUserRequest.serializer()), ListSerializer(CreateSingleUserResponse.serializer()), CommonErrorMessage.serializer()) {
        audit(ListSerializer(CreateSingleUserAudit.serializer()))

        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"register"
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Request creation of a new $TYPE_REF PASSWORD user."
        }
    }

    val updateUserInfo = call("updateUserInfo", UpdateUserInfoRequest.serializer(), UpdateUserInfoResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.END_USER
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"updateUserInfo"
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Request update of information about the current user."
        }
    }

    val getUserInfo = call("getUserInfo", GetUserInfoRequest.serializer(), GetUserInfoResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.END_USER
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get
            path {
                using(baseContext)
                +"userInfo"
            }
        }

        documentation {
            summary = "Request information about the current user."
        }
    }

    val verifyUserInfo = call("verifyUserInfo", FindByStringId.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get
            path {
                using(baseContext)
                +"verifyUserInfo"
            }

            params { +boundTo(FindByStringId::id) }
        }

        documentation {
            summary = "Verifies a change in user info (typically accessed through an email)"
        }
    }

    val retrievePrincipal = call("retrievePrincipal", GetPrincipalRequest.serializer(), GetPrincipalResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = setOf(Role.SERVICE)
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"retrievePrincipal"
            }

            params {
                +boundTo(GetPrincipalRequest::username)
            }
        }
    }

    val changePassword = call("changePassword", ChangePasswordRequest.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        audit(ChangePasswordAudit.serializer())

        auth {
            roles = Roles.END_USER
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"password"
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Request change of the password of the current user (if $TYPE_REF PASSWORD user)."
        }
    }

    val changePasswordWithReset = call("changePasswordWithReset", ChangePasswordWithResetRequest.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        audit(ChangePasswordAudit.serializer())

        auth {
            roles = setOf(Role.SERVICE)
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"password"
                +"reset"
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Request reset of password of a $TYPE_REF PASSWORD user."
            description = """
                This request can only be called by other services, and is used by the `PasswordResetService` to reset a
                user's password in case they are unable to log in. Read more in [Password Reset](authentication/password-reset.md).
            """.trimIndent()
        }
    }

    val lookupUsers = call("lookupUsers", LookupUsersRequest.serializer(), LookupUsersResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"lookup"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val lookupEmail = call("lookupEmail", LookupEmailRequest.serializer(), LookupEmailResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"lookup"
                +"email"
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Request the email of a user."
        }
    }

    val lookupUserWithEmail = call("lookupUserWithEmail", LookupUserWithEmailRequest.serializer(), LookupUserWithEmailResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = setOf(Role.SERVICE)
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"lookup"
                +"with-email"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val openUserIterator = call("openUserIterator", Unit.serializer(), FindByStringId.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"iterator"
                +"open"
            }
        }
    }

    /**
     * Fetches more principals from an iterator.
     *
     * Note: twoFactorAuthentication field is not calculated correctly at the moment.
     */
    val fetchNextIterator = call("fetchNextIterator", FindByStringId.serializer(), ListSerializer(Principal.serializer()), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"iterator"
                +"next"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val closeIterator = call("closeIterator", FindByStringId.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"iterator"
                +"close"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
