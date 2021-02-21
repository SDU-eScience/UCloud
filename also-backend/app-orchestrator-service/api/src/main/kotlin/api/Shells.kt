package dk.sdu.cloud.app.orchestrator.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.service.TYPE_PROPERTY
import dk.sdu.cloud.calls.*

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ShellRequest.Initialize::class, name = "initialize"),
    JsonSubTypes.Type(value = ShellRequest.Input::class, name = "input"),
    JsonSubTypes.Type(value = ShellRequest.Resize::class, name = "resize"),
)
sealed class ShellRequest {
    data class Initialize(
        val sessionIdentifier: String,
        val cols: Int = 80,
        val rows: Int = 24
    ) : ShellRequest()

    data class Input(val data: String) : ShellRequest() {
        override fun toString(): String = "Input()"
    }

    data class Resize(val cols: Int, val rows: Int) : ShellRequest()
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ShellResponse.Initialized::class, name = "initialize"),
    JsonSubTypes.Type(value = ShellResponse.Data::class, name = "data"),
    JsonSubTypes.Type(value = ShellResponse.Acknowledged::class, name = "ack")
)
sealed class ShellResponse {
    class Initialized() : ShellResponse()
    data class Data(val data: String) : ShellResponse() {
        override fun toString(): String = "Data()"
    }
    class Acknowledged : ShellResponse()
}

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
abstract class Shells(namespace: String) : CallDescriptionContainer("jobs.compute.$namespace.shell") {
    val baseContext = "/ucloud/$namespace/compute/jobs/shells"

    init {
        title = "Provider API: Compute/Shells"
        description = """
            Provides an API for providers to give shell access to their running compute jobs.
        """.trimIndent()
    }

    val open = call<ShellRequest, ShellResponse, CommonErrorMessage>("open") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PUBLIC // NOTE(Dan): Access is granted via sessionIdentifier
        }

        websocket(baseContext)
    }
}
