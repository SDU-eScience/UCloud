package dk.sdu.cloud.app.fs.services

import dk.sdu.cloud.app.fs.api.FileSystemCalls
import dk.sdu.cloud.calls.RPCException
import io.ktor.http.HttpStatusCode

private class FileSystemCallsWithBackend(val backend: String) : FileSystemCalls(backend)

/**
 * A service for contacting other backends.
 *
 * This service performs caching of the [FileSystemCalls] objects and performs validation of the requested backends.
 */
class BackendService(
    private val knownBackends: Set<String>,
    private val defaultBackend: String
) {
    private val backendCache = HashMap<String, FileSystemCalls>()

    init {
        if (defaultBackend !in knownBackends) {
            throw IllegalArgumentException("defaultBackend ($defaultBackend) is not in knownBackends ($knownBackends)")
        }
    }

    fun verifyBackend(name: String?): String {
        val backendName = name ?: defaultBackend
        if (backendName !in knownBackends) throw RPCException("Unknown backend", HttpStatusCode.BadRequest)
        return backendName
    }

    fun getBackend(name: String?): FileSystemCalls {
        val backendName = name ?: defaultBackend
        if (backendName !in knownBackends) throw RPCException("Unknown backend", HttpStatusCode.BadRequest)

        val cached = backendCache[backendName]
        if (cached != null) return cached

        val newBackend = FileSystemCallsWithBackend(backendName)
        backendCache[backendName] = newBackend
        return newBackend
    }
}
