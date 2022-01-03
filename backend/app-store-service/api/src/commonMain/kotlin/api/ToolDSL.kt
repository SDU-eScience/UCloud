package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.calls.RPCException
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

@Serializable
enum class ToolBackend {
    SINGULARITY,
    DOCKER,
    VIRTUAL_MACHINE,
    NATIVE,
}

sealed class ToolVerificationException(why: String, httpStatusCode: HttpStatusCode) :
    RPCException(why, httpStatusCode) {
    class BadValue(parameter: String, reason: String) :
        ToolVerificationException("Parameter '$parameter' received a bad value. $reason", HttpStatusCode.BadRequest)
}
