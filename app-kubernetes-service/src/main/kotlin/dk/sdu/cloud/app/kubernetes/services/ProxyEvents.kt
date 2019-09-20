package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.events.EventStreamContainer

data class ProxyEvent(val id: String, val shouldCreate: Boolean)

object ProxyEvents : EventStreamContainer() {
    val events = stream<ProxyEvent>("appk8-proxy", { it.id })
}
