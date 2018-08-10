package dk.sdu.cloud.bare.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.bare.api.PingDescriptions
import dk.sdu.cloud.bare.api.PingResponse
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.service.*
import io.ktor.routing.Route

class PingController : Controller, Loggable {
    override val baseContext: String = PingDescriptions.baseContext
    override val log = logger()

    override fun configure(routing: Route): Unit = with(routing) {
//        protect()

        implement(PingDescriptions.ping) {
            logEntry(log, it)

            ok(PingResponse(it.ping))
        }

        implement(PingDescriptions.pingOther) {
            logEntry(log, it)
            val response = PingDescriptions.ping.call(it, call.cloudClient)
            when (response) {
                is RESTResponse.Ok -> ok(response.result)
                else -> {
                    error(CommonErrorMessage("Call failed! Raw response: ${response.status} - " +
                            response.rawResponseBody
                    ))
                }
            }
        }
    }
}
