package dk.sdu.cloud.avatar.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.RESTDescriptions
import io.ktor.http.HttpMethod

object AvatarDescriptions : RESTDescriptions("avatar") {
    val baseContext = "/api/avatar"

    val create = callDescription<Unit, Unit, CommonErrorMessage> {
        name = "create"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"create"
        }
    }

    val update = callDescription<Unit, Unit, CommonErrorMessage> {
        name = "update"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"update"
        }
    }
}
