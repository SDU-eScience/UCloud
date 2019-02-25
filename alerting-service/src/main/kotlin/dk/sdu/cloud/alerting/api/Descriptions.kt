package dk.sdu.cloud.alerting.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.RESTDescriptions
import io.ktor.http.HttpMethod

object AlertingDescriptions : RESTDescriptions("alerting") {
    val baseContext = "/api/alerting"

    val call = callDescription<Unit, Unit, CommonErrorMessage> {
        name = "call"
        method = HttpMethod.Get

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"call"
        }
    }
}
