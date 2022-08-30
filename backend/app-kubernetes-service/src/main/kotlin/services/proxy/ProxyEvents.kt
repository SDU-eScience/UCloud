package dk.sdu.cloud.app.kubernetes.services.proxy

import dk.sdu.cloud.events.EventStreamContainer
import kotlinx.serialization.Serializable

@Serializable
data class ProxyEvent(
    val id: String,
    val shouldCreate: Boolean,
    val domains: List<String>? = null,
    val replicas: Int = 1
)

object ProxyEvents : EventStreamContainer() {
    val events = stream(ProxyEvent.serializer(), "appk8-proxy", { it.id })
}
