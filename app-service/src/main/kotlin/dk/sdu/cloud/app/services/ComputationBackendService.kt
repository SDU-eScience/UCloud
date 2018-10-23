package dk.sdu.cloud.app.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.api.ComputationDescriptions
import dk.sdu.cloud.service.RPCException
import io.ktor.http.HttpStatusCode

class NamedComputationBackendDescriptions(name: String) : ComputationDescriptions(name)

class ComputationBackendService(
    backends: List<String>
) {
    private val backends = backends.toSet()

    fun getByName(backend: String, principal: SecurityPrincipal? = null): NamedComputationBackendDescriptions {
        if (backend !in backends) throw ComputationBackendException.UnrecognizedBackend(backend)
        if (principal != null && principal.username != backendPrincipalName(backend)) {
            throw ComputationBackendException.UntrustedSource()
        }

        return NamedComputationBackendDescriptions(backend)
    }

    fun backendPrincipalName(backend: String) = "_$backend"
}

sealed class ComputationBackendException(
    why: String,
    httpStatusCode: HttpStatusCode
) : RPCException(why, httpStatusCode) {
    class UnrecognizedBackend(backend: String) :
        ComputationBackendException("Unrecognized backend: $backend", HttpStatusCode.BadRequest)

    class UntrustedSource : ComputationBackendException("Forbidden", HttpStatusCode.Forbidden)
}
