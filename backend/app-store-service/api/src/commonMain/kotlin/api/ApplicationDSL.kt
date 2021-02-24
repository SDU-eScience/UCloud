package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.calls.RPCException
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

sealed class ApplicationVerificationException(why: String, httpStatusCode: HttpStatusCode) :
    RPCException(why, httpStatusCode) {
    class DuplicateDefinition(type: String, definitions: List<String>) :
        ApplicationVerificationException(
            "Duplicate definition of $type. " +
                    "Duplicates where: ${definitions.joinToString(", ")}",
            HttpStatusCode.BadRequest
        )

    class BadValue(parameter: String, why: String) :
        ApplicationVerificationException("Parameter '$parameter' received a bad value. $why", HttpStatusCode.BadRequest)

    class BadVariableReference(where: String, name: String) :
        ApplicationVerificationException(
            "Variable referenced at $where with name '$name' could not be resolved",
            HttpStatusCode.BadRequest
        )
}

@Serializable
enum class ApplicationType {
    BATCH,
    VNC,
    WEB
}

