package dk.sdu.cloud.app.kubernetes.services.proxy

import dk.sdu.cloud.events.EventStreamContainer

data class ProxyEvent(
    val id: String,
    val shouldCreate: Boolean,
    val domains: List<String>? = null,
    val replicas: Int = 1
)

object ProxyEvents : EventStreamContainer() {
    val events = stream<ProxyEvent>("appk8-proxy", { it.id })
}
