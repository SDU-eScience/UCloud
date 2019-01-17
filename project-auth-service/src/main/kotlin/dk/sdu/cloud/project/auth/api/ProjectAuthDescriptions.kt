package dk.sdu.cloud.project.auth.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.ktor.http.HttpMethod

data class FetchTokenRequest(val project: String)
typealias FetchTokenResponse = ProjectAuthenticationToken

data class ProjectAuthenticationToken(val accessToken: String)

object ProjectAuthDescriptions : RESTDescriptions("project.auth") {
    val baseContext = "/api/projects/auth"

    val fetchToken = callDescription<FetchTokenRequest, FetchTokenResponse, CommonErrorMessage> {
        name = "fetchToken"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
        }

        body { bindEntireRequestFromBody() }
    }
}
