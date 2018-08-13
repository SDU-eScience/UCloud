package dk.sdu.cloud.service

import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.PreparedRESTCall
import dk.sdu.cloud.client.ServiceDescription
import java.net.ConnectException

/**
 * A client intended for internal services.
 *
 * This client will connect directly to another internal service using the service registry. Services are chosen
 * from the registry in a random fashion and saved in a local cache. If the service eventually becomes unavailable
 * the [PreparedRESTCall] will call back to this client and it will automatically attempt to use a different service.
 *
 * In the event that no services are available a [ConnectException] will be thrown.
 */
class DirectServiceClient : CloudContext {
    override fun resolveEndpoint(call: PreparedRESTCall<*, *>): String {
        return resolveEndpoint(call.owner)
    }

    override fun resolveEndpoint(service: ServiceDescription): String {
        TODO()
    }

    override fun tryReconfigurationOnConnectException(call: PreparedRESTCall<*, *>, ex: ConnectException): Boolean {
        TODO()
    }

    companion object : Loggable {
        override val log = logger()
    }
}