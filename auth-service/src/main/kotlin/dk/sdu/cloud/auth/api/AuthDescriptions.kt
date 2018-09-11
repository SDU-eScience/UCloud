package dk.sdu.cloud.auth.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.RESTDescriptions
import io.ktor.http.HttpMethod

data class OneTimeAccessToken(val accessToken: String, val jti: String)
data class RequestOneTimeToken(val audience: String)
data class ClaimOneTimeToken(val jti: String)

object AuthDescriptions : RESTDescriptions("auth") {
    private const val baseContext = "/auth"

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
        name = "refresh-web"

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
        name = "logout-web"

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

    // TODO This will change!!!
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
}
