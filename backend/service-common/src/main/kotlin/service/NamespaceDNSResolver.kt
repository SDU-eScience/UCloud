package dk.sdu.cloud.service

import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.calls.client.OutgoingHostResolver

class NamespaceDNSResolver(private val scheme: String, private val port: Int) : OutgoingHostResolver {
    override fun resolveEndpoint(callDescription: CallDescription<*, *, *>): HostInfo {
        val safeHostname = callDescription.namespace.replace('.', '-')
        return HostInfo(safeHostname, scheme, port)
    }
}
