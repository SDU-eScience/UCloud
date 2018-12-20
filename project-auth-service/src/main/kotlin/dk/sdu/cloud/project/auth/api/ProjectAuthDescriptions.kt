package dk.sdu.cloud.project.auth.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.RESTDescriptions
import io.ktor.http.HttpMethod

object ProjectAuthDescriptions : RESTDescriptions("project.auth") {
    val baseContext = "/api/projects/auth"

    val fetchTokens = callDescription<Unit, Unit, CommonErrorMessage> {
        name = "fetchTokens"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
        }
    }

    val invalidateTokens = callDescription<Unit, Unit, CommonErrorMessage> {
        name = "invalidateTokens"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"invalidate"
        }
    }

    val refresh = callDescription<Unit, Unit, CommonErrorMessage> {
        name = "refresh"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"refresh"
        }
    }
}
