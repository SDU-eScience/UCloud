package dk.sdu.cloud.elastic.management.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod

object HelloDescriptions : CallDescriptionContainer("elastic.management") {
    val baseContext = "/api/elasticManagement"

    val hello = call<Unit, Unit, CommonErrorMessage>("hello") {

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"call"
            }
        }

        auth {
            access = AccessRight.READ
        }
    }
}
