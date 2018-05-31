package dk.sdu.cloud.auth.api

import dk.sdu.cloud.client.RESTDescriptions
import io.ktor.http.HttpMethod

data class OneTimeAccessToken(val accessToken: String, val jti: String)
object AuthDescriptions : RESTDescriptions(AuthServiceDescription) {
    private const val baseContext = "/auth"

    val refresh = callDescription<Unit, AccessToken, Unit> {
        method = HttpMethod.Post
        prettyName = "refresh"

        path {
            using(baseContext)
            +"refresh"
        }
    }

    val logout = callDescription<Unit, Unit, Unit> {
        method = HttpMethod.Post
        prettyName = "logout"

        path {
            using(baseContext)
            +"logout"
        }
    }

    val claim = callDescription<ClaimOneTimeToken, Unit, Unit> {
        method = HttpMethod.Post
        prettyName = "claim"

        path {
            using(baseContext)
            +"claim"
            +boundTo(ClaimOneTimeToken::jti)
        }
    }

    val requestOneTimeTokenWithAudience = callDescription<RequestOneTimeToken, OneTimeAccessToken, Unit> {
        method = HttpMethod.Post
        prettyName = "requestOneTimeTokenWithAudience"

        path {
            using(baseContext)
            +"request"
        }

        params {
            +boundTo(RequestOneTimeToken::audience)
        }
    }
}

data class RequestOneTimeToken(val audience: String)

data class ClaimOneTimeToken(val jti: String)