package dk.sdu.cloud.auth.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.BinaryStream
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.ktor.http.HttpMethod

typealias HTMLPage = BinaryStream

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
)

typealias TokenExtensionResponse = OptionalAuthenticationTokens

data class TokenExtensionAudit(
    val requestedBy: String,
    val username: String?,
    val role: Role?,
    val requestedScopes: List<String>,
    val expiresIn: Long,
    val allowRefreshes: Boolean
)

object AuthDescriptions : RESTDescriptions("auth") {
    const val baseContext = "/auth"

    val refresh = callDescription<Unit, AccessToken, Unit> {
        method = HttpMethod.Post
        name = "refresh"

        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"refresh"
        }
    }

    val webRefresh = callDescription<Unit, AccessTokenAndCsrf, CommonErrorMessage> {
        method = HttpMethod.Post
        name = "refreshWeb"

        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"refresh"
            +"web"
        }
    }

    val logout = callDescription<Unit, Unit, Unit> {
        method = HttpMethod.Post
        name = "logout"

        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"logout"
        }
    }

    val webLogout = callDescription<Unit, Unit, CommonErrorMessage> {
        method = HttpMethod.Post
        name = "logoutWeb"

        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"logout"
            +"web"
        }
    }

    val claim = callDescription<ClaimOneTimeToken, Unit, Unit> {
        method = HttpMethod.Post
        name = "claim"

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"claim"
            +boundTo(ClaimOneTimeToken::jti)
        }
    }

    val requestOneTimeTokenWithAudience = callDescription<RequestOneTimeToken, OneTimeAccessToken, Unit> {
        method = HttpMethod.Post
        name = "requestOneTimeTokenWithAudience"

        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"request"
        }

        params {
            +boundTo(RequestOneTimeToken::audience)
        }
    }

    val tokenExtension = callDescriptionWithAudit<TokenExtensionRequest, TokenExtensionResponse,
            CommonErrorMessage, TokenExtensionAudit> {
        method = HttpMethod.Post
        name = "tokenExtension"

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"extend"
        }

        body { bindEntireRequestFromBody() }
    }

    internal val twoFactorPage = callDescriptionWithAudit<Unit, HTMLPage, HTMLPage, TwoFactorPageRequest> {
        name = "twoFactorPage"
        method = HttpMethod.Get

        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"2fa"
        }
    }

    internal val loginPage = callDescriptionWithAudit<Unit, HTMLPage, HTMLPage, LoginPageRequest> {
        name = "loginPage"
        method = HttpMethod.Get

        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"login"
        }
    }

    internal val passwordLogin = callDescriptionWithAudit<Unit, HTMLPage, HTMLPage, LoginRequest> {
        name = "passwordLogin"
        method = HttpMethod.Post

        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"login"
        }

        body { bindEntireRequestFromBody() }
    }
}

