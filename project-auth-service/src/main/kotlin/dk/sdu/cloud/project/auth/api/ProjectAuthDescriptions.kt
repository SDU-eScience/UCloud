package dk.sdu.cloud.project.auth.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod

data class FetchTokenRequest(val project: String)
typealias FetchTokenResponse = ProjectAuthenticationToken

data class ProjectAuthenticationToken(val accessToken: String)

object ProjectAuthDescriptions : CallDescriptionContainer("project.auth") {
    val baseContext = "/api/projects/auth"

    val fetchToken = call<FetchTokenRequest, FetchTokenResponse, CommonErrorMessage>("fetchToken") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
