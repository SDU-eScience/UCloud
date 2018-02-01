package dk.sdu.cloud.web.api

import dk.sdu.cloud.client.RESTDescriptions
import io.netty.handler.codec.http.HttpMethod
import org.slf4j.LoggerFactory

object WebGatewayDescriptions : RESTDescriptions(WebServiceDescription) {
    private val f = callDescription<Unit, Unit, Unit> {
        path { +"/api/auth-callback" }
        method = HttpMethod.POST
    }

    private val log = LoggerFactory.getLogger(WebGatewayDescriptions::class.java)
}