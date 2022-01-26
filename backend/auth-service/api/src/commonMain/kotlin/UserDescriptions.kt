package dk.sdu.cloud.auth.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@Serializable
data class LookupUsersRequest(val users: List<String>)

@Serializable
data class UserLookup(val subject: String, val uid: Long, val role: Role)

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

@Serializable
data class LookupUIDRequest(val uids: List<Long>)

@Serializable
data class LookupUIDResponse(val users: Map<Long, UserLookup?>)

object UserDescriptions : CallDescriptionContainer("auth.users") {
    const val baseContext = "/auth/users"

    val createNewUser = call<CreateUserRequest, CreateUserResponse, CommonErrorMessage>("createNewUser") {
        audit<CreateUserAudit>()

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
    }

    val updateUserInfo = call<UpdateUserInfoRequest, UpdateUserInfoResponse, CommonErrorMessage>("updateUserInfo") {
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
    }

    val getUserInfo = call<GetUserInfoRequest, GetUserInfoResponse, CommonErrorMessage>("getUserInfo") {
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
    }

    val retrievePrincipal = call<GetPrincipalRequest, GetPrincipalResponse, CommonErrorMessage>("retrievePrincipal") {
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

    val changePassword = call<ChangePasswordRequest, Unit, CommonErrorMessage>("changePassword") {
        audit<ChangePasswordAudit>()

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
    }

    val changePasswordWithReset = call<ChangePasswordWithResetRequest, Unit, CommonErrorMessage>("changePasswordWithReset") {
        audit<ChangePasswordAudit>()

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
    }

    val lookupUsers = call<LookupUsersRequest, LookupUsersResponse, CommonErrorMessage>("lookupUsers") {
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

    val lookupEmail = call<LookupEmailRequest, LookupEmailResponse, CommonErrorMessage>("lookupEmail") {
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
    }

    val lookupUserWithEmail = call<LookupUserWithEmailRequest, LookupUserWithEmailResponse, CommonErrorMessage>("lookupUserWithEmail") {
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

    val lookupUID = call<LookupUIDRequest, LookupUIDResponse, CommonErrorMessage>("lookupUID") {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"lookup-uid"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val openUserIterator = call<Unit, FindByStringId, CommonErrorMessage>("openUserIterator") {
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
    val fetchNextIterator = call<FindByStringId, List<Principal>, CommonErrorMessage>("fetchNextIterator") {
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

    val closeIterator = call<FindByStringId, Unit, CommonErrorMessage>("closeIterator") {
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
