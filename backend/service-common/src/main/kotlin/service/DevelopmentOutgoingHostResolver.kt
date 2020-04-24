package dk.sdu.cloud.service

import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.calls.client.OutgoingHostResolver
import dk.sdu.cloud.micro.ServiceDiscoveryOverrides

class DevelopmentOutgoingHostResolver(
    private val delegate: OutgoingHostResolver,
    private val overrides: ServiceDiscoveryOverrides
) : OutgoingHostResolver {
    override fun resolveEndpoint(callDescription: CallDescription<*, *, *>): HostInfo {
        val serviceDiscoveryOverride = overrides[callDescription.namespace]
        return if (serviceDiscoveryOverride != null) {
            HostInfo(
                scheme = "http",
                host = serviceDiscoveryOverride.hostname,
                port = serviceDiscoveryOverride.port
            )
        } else {
            delegate.resolveEndpoint(callDescription)
        }
    }
}
