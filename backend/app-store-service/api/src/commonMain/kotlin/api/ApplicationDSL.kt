package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.TYPE_REF
import dk.sdu.cloud.calls.UCloudApiDoc
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
@UCloudApiDoc("""
    The ApplicationType determines how user's interact with an Application
    
    - `BATCH`: A non-interactive $TYPE_REF Application which runs without user input
    - `VNC`: An interactive $TYPE_REF Application exposing a remote desktop interface
    - `WEB`: An interactive $TYPE_REF Application exposing a graphical web interface
""", importance = 980)
enum class ApplicationType {
    @UCloudApiDoc("A non-interactive $TYPE_REF Application which runs without user input")
    BATCH,
    @UCloudApiDoc("An interactive $TYPE_REF Application exposing a remote desktop interface")
    VNC,
    @UCloudApiDoc("An interactive $TYPE_REF Application exposing a graphical web interface")
    WEB
}

