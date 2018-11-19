package dk.sdu.cloud.{{ serviceName }}.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.RESTDescriptions
import io.ktor.http.HttpMethod

object {{ serviceNameTitle }}Descriptions : RESTDescriptions("{{ serviceName }}") {
    val baseContext = "/api/{{ serviceName }}"

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
