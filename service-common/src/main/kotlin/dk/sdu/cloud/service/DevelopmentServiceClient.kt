package dk.sdu.cloud.service

import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.PreparedRESTCall
import dk.sdu.cloud.client.SDUCloud
import java.net.ConnectException

class DevelopmentServiceClient(
    private val sduCloud: SDUCloud,
    private val overrides: ServiceDiscoveryOverrides
) : CloudContext {
    override fun resolveEndpoint(namespace: String): String {
        val serviceDiscoveryOverride = overrides[namespace]
        return if (serviceDiscoveryOverride != null) {
            "http://${serviceDiscoveryOverride.hostname}:${serviceDiscoveryOverride.port}"
        } else {
            sduCloud.resolveEndpoint(namespace)
        }
    }

    override fun tryReconfigurationOnConnectException(call: PreparedRESTCall<*, *>, ex: ConnectException): Boolean {
        return false
    }
}
