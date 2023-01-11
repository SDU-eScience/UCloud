package dk.sdu.cloud.faults

import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.httpUpdate
import kotlinx.serialization.builtins.serializer

object FaultInjections : CallDescriptionContainer("fault_injection") {
    const val baseContext = "/api/faultInjection"

    val clearCaches = call("clearCaches", Unit.serializer(), Unit.serializer(), Unit.serializer()) {
        httpUpdate(baseContext, "clearCaches", roles = Roles.PUBLIC)
    }
}
