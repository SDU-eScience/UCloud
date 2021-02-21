package dk.sdu.cloud.auth.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod

data class ServiceAgreementText(val version: Int, val text: String)
data class AcceptSLARequest(val version: Int)

object ServiceLicenseAgreement : CallDescriptionContainer("sla") {
    const val baseContext = "/api/sla"

    val find = call<Unit, ServiceAgreementText, CommonErrorMessage>("find") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
            }
        }
    }

    val accept = call<AcceptSLARequest, Unit, CommonErrorMessage>("accept") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"accept"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
