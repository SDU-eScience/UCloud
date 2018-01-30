package dk.sdu.cloud.auth.api

import dk.sdu.cloud.client.RESTDescriptions
import io.netty.handler.codec.http.HttpMethod

data class OneTimeAccessToken(val accessToken: String, val jti: String)
object AuthDescriptions : RESTDescriptions(AuthServiceDescription) {
    // This is a bit weird.
    //
    // For the frontends the proxying (and load balancing) will be performed by the apache server which is in front
    // of the gateway. But for internal services they will contact us directly. For this reason we will only need to
    // write down the actual REST interface, as we don't need to register any of the "manual" steps that are used
    // only for in browser authentication (i.e. by a frontend)
    //
    // TODO But it really should be done by the gateway. There is no real reason that we have apache.
    // Other than, of course, not trusting that the gateway is actually efficient at proxying requests (which I doubt
    // it is).
    // TODO Also the gateway doesn't currently do request bodies (it is also not trivial to implement efficiently)

    private const val baseContext = "/auth"

    // TODO Maybe we should post the refresh token in the body as opposed to the header?
    val refresh = callDescription<Unit, AccessToken, Unit> {
        method = HttpMethod.POST
        prettyName = "auth.refresh"
        shouldProxyFromGateway = false

        path {
            using(baseContext)
            +"refresh"
        }
    }

    val logout = callDescription<Unit, Unit, Unit> {
        method = HttpMethod.POST
        prettyName = "auth.logout"
        shouldProxyFromGateway = false

        path {
            using(baseContext)
            +"logout"
        }
    }

    val claim = callDescription<ClaimOneTimeToken, Unit, Unit> {
        method = HttpMethod.POST
        prettyName = "auth.claim"
        shouldProxyFromGateway = false

        path {
            using(baseContext)
            +"claim"
            +boundTo(ClaimOneTimeToken::jti)
        }
    }

    val requestOneTimeTokenWithAudience = callDescription<RequestOneTimeToken, OneTimeAccessToken, Unit> {
        method = HttpMethod.POST
        prettyName = "auth.requestOneTimeTokenWithAudience"
        shouldProxyFromGateway = false

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