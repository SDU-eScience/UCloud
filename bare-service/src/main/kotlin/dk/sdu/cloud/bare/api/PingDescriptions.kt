package dk.sdu.cloud.bare.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.ktor.http.HttpMethod

data class PingRequest(val ping: String)
data class PingResponse(val pong: String)
data class EverythingReport(val report: String)

object PingDescriptions : RESTDescriptions(BareServiceDescription) {
    val baseContext = "/api/bare/ping"

    val ping = callDescription<PingRequest, PingResponse, CommonErrorMessage> {
        prettyName = "ping"
        method = HttpMethod.Post

        path {
            using(baseContext)
        }

        body { bindEntireRequestFromBody() }
    }

    val pingOther = callDescription<PingRequest, PingResponse, CommonErrorMessage> {
        prettyName = "pingOther"
        method = HttpMethod.Post

        path {
            using(baseContext)
            +"other"
        }

        body { bindEntireRequestFromBody() }
    }

    val everything = callDescription<Unit, EverythingReport, CommonErrorMessage> {
        prettyName = "everything"
        method = HttpMethod.Get

        path {
            using(baseContext)
            +"everything"
        }
    }
}