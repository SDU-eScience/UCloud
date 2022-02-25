package dk.sdu.cloud.auth.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@Serializable
data class ServiceAgreementText(val version: Int, val text: String)

@Serializable
data class AcceptSLARequest(val version: Int)

object ServiceLicenseAgreement : CallDescriptionContainer("sla") {
    const val baseContext = "/api/sla"

    init {
        title = "Service License Agreement (SLA)"
        description = """
The service-license-agreement API enforces that the SLA is accepted before using the service.
           
Our SLA API allows for multiple SLA version to be stored and retrieved.
A user has to accept the newest SLA to be allowed to access UCloud. This ensures that all users of UCloud 
are informed on what is legal usage of UCloud and what is not. 

${ApiConventions.nonConformingApiWarning}
        """.trimIndent()
    }

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
