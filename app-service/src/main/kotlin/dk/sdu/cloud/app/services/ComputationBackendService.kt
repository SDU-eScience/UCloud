package dk.sdu.cloud.app.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.api.ApplicationBackend
import dk.sdu.cloud.app.api.ComputationDescriptions
import dk.sdu.cloud.calls.RPCException
import io.ktor.http.HttpStatusCode

class NamedComputationBackendDescriptions(
    val config: ApplicationBackend
) : ComputationDescriptions(config.name)

class ComputationBackendService(
    backends: List<ApplicationBackend>,
    private val developmentModeEnabled: Boolean
) {
    private val backends = backends.associateBy { it.name }
    private val cachedBackends = HashMap<String, NamedComputationBackendDescriptions>()

    fun getAndVerifyByName(backend: String, principal: SecurityPrincipal? = null): NamedComputationBackendDescriptions {
        val backendConfig = backends[backend]
        if (backendConfig == null) throw ComputationBackendException.UnrecognizedBackend(backend)
        if (!developmentModeEnabled) {
            if (principal != null && principal.username != backendPrincipalName(backend)) {
                throw ComputationBackendException.UntrustedSource()
            }
        }

        return cachedBackends[backend] ?: (NamedComputationBackendDescriptions(backendConfig).also {
            cachedBackends[backend] = it
        })
    }

    fun backendPrincipalName(backend: String) = "_app-$backend"
}

sealed class ComputationBackendException(
    why: String,
    httpStatusCode: HttpStatusCode
) : RPCException(why, httpStatusCode) {
    class UnrecognizedBackend(backend: String) :
        ComputationBackendException("Unrecognized backend: $backend", HttpStatusCode.BadRequest)

    class UntrustedSource : ComputationBackendException("Forbidden", HttpStatusCode.Forbidden)
}
