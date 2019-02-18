package dk.sdu.cloud.auth.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.audit
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod

typealias HTMLPage = Unit

internal data class TwoFactorPageRequest(
    val challengeId: String?,
    val invalid: Boolean,
    val message: String?
)

internal data class LoginPageRequest(
    val service: String?,
    val invalid: Boolean
)

internal data class LoginRequest(
    val username: String?,
    val service: String?
)

data class OneTimeAccessToken(val accessToken: String, val jti: String)
data class RequestOneTimeToken(val audience: String)
data class ClaimOneTimeToken(val jti: String)

data class TokenExtensionRequest(
    /**
     * A valid JWT for the security principal extension is requested
     */
    val validJWT: String,

    /**
     * A list of [SecurityScope]s that this request requires.
     *
     * It is not possible to ask for all.
     */
    val requestedScopes: List<String>,

    /**
     * How many ms the new token should be valid for.
     *
     * It is not possible to extend this deadline. Currently the maximum deadline is configured to be 24 hours.
     */
    val expiresIn: Long,

    /**
     * Should this extension allow the token to be refreshed?
     *
     * This will happen through a refresh token passed via [OptionalAuthenticationTokens.refreshToken].
     */
    val allowRefreshes: Boolean = false
) {
    override fun toString(): String =
        "TokenExtensionRequest(" +
                "requestedScopes = $requestedScopes, " +
                "expiresIn = $expiresIn, " +
                "allowRefreshes = $allowRefreshes" +
                ")"
}

typealias TokenExtensionResponse = OptionalAuthenticationTokens

data class TokenExtensionAudit(
    val requestedBy: String,
    val username: String?,
    val role: Role?,
    val requestedScopes: List<String>,
    val expiresIn: Long,
    val allowRefreshes: Boolean
)

data class BulkInvalidateRequest(val tokens: List<String>)
typealias BulkInvalidateResponse = Unit

object AuthDescriptions : CallDescriptionContainer("auth") {
    const val baseContext = "/auth"

    val refresh = call<Unit, AccessToken, Unit>("refresh") {
        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"refresh"
            }
        }
    }

    val webRefresh = call<Unit, AccessTokenAndCsrf, CommonErrorMessage>("webRefresh") {
        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"refresh"
                +"web"
            }
        }
    }

    val bulkInvalidate = call<BulkInvalidateRequest, BulkInvalidateResponse, CommonErrorMessage>("bulkInvalidate") {
        audit<Unit>()

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"logout"
                +"bulk"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val logout = call<Unit, Unit, Unit>("logout") {
        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"logout"
            }
        }
    }

    val webLogout = call<Unit, Unit, CommonErrorMessage>("webLogout") {
        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"logout"
                +"web"
            }
        }
    }

    val claim = call<ClaimOneTimeToken, Unit, Unit>("claim") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"claim"
                +boundTo(ClaimOneTimeToken::jti)
            }
        }
    }

    val requestOneTimeTokenWithAudience = call<RequestOneTimeToken, OneTimeAccessToken, Unit>("requestOneTimeTokenWithAudience") {
        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"request"
            }

            params {
                +boundTo(RequestOneTimeToken::audience)
            }
        }
    }

    val tokenExtension = call<TokenExtensionRequest, TokenExtensionResponse, CommonErrorMessage>("tokenExtension") {
        audit<TokenExtensionAudit>()
        auth {
            roles = setOf(Role.USER, Role.SERVICE, Role.ADMIN)
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"extend"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    internal val twoFactorPage = call<Unit, HTMLPage, HTMLPage>("twoFactorPage") {
        audit<TwoFactorPageRequest>()
        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get
            path {
                using(baseContext)
                +"2fa"
            }
        }
    }

    internal val loginPage = call<Unit, HTMLPage, HTMLPage>("loginPage") {
        audit<LoginPageRequest>()

        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get
            path {
                using(baseContext)
                +"login"
            }
        }
    }

    internal val passwordLogin = call<Unit, HTMLPage, HTMLPage>("passwordLogin") {
        audit<LoginRequest>()

        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"login"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}

