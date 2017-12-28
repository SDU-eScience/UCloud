package dk.sdu.cloud.auth.api

import dk.sdu.cloud.client.RESTDescriptions
import io.netty.handler.codec.http.HttpMethod

object AuthDescriptions : RESTDescriptions() {
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

    private val baseContext = "/auth"

    // TODO Maybe we should post the refresh token in the body as opposed to the header?
    val refresh = callDescription<Unit, AccessToken, Unit> {
        method = HttpMethod.POST
        shouldProxyFromGateway = false

        path {
            using(baseContext)
            +"refresh"
        }
    }

    val logout = callDescription<Unit, Unit, Unit> {
        method = HttpMethod.POST
        shouldProxyFromGateway = false

        path {
            using(baseContext)
            +"logout"
        }
    }
}