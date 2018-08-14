package dk.sdu.cloud.service

import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.PreparedRESTCall
import dk.sdu.cloud.client.SDUCloud
import dk.sdu.cloud.client.ServiceDescription
import java.net.ConnectException

class DevelopmentServiceClient(
    private val sduCloud: SDUCloud,
    private val overrides: ServiceDiscoveryOverrides
) : CloudContext {
    override fun resolveEndpoint(call: PreparedRESTCall<*, *>): String {
        return resolveEndpoint(call.owner)
    }

    override fun resolveEndpoint(service: ServiceDescription): String {
        val serviceDiscoveryOverride = overrides[service.name]
        return if (serviceDiscoveryOverride != null) {
            "http://${serviceDiscoveryOverride.hostname}:${serviceDiscoveryOverride.port}"
        } else {
            sduCloud.resolveEndpoint(service)
        }
    }

    override fun tryReconfigurationOnConnectException(call: PreparedRESTCall<*, *>, ex: ConnectException): Boolean {
        return false
    }
}
