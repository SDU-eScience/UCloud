package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.calls.RPCException
import io.ktor.http.HttpStatusCode

data class ApplicationPeer(val name: String, val jobId: String) {
    init {
        if (!name.matches(hostNameRegex)) throw RPCException("Invalid hostname: $name", HttpStatusCode.BadRequest)
    }

    companion object {
        private val hostNameRegex =
            Regex("^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])\$")
    }
}
