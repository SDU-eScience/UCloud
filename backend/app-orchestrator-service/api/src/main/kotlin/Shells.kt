package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@UCloudApiStable
sealed class ShellRequest {
    @Serializable
    @SerialName("initialize")
    @UCloudApiStable
    data class Initialize(
        val sessionIdentifier: String,
        val cols: Int = 80,
        val rows: Int = 24
    ) : ShellRequest()

    @Serializable
    @SerialName("input")
    @UCloudApiStable
    @UCloudApiDoc("An event triggered when a user types any sort of input into a terminal")
    data class Input(val data: String) : ShellRequest() {
        override fun toString(): String = "Input()"
    }

    @Serializable
    @SerialName("resize")
    @UCloudApiStable
    @UCloudApiDoc("An event triggered when a user resizes a terminal")
    data class Resize(val cols: Int, val rows: Int) : ShellRequest()
}

@Serializable
@UCloudApiStable
sealed class ShellResponse {
    @Serializable
    @SerialName("initialize")
    @UCloudApiStable
    @UCloudApiDoc("Emitted by the provider when the terminal has been initialized")
    class Initialized() : ShellResponse()

    @Serializable
    @SerialName("data")
    @UCloudApiStable
    @UCloudApiDoc("Emitted by the provider when new data is available for the terminal")
    data class Data(val data: String) : ShellResponse() {
        override fun toString(): String = "Data()"
    }

    @Serializable
    @SerialName("ack")
    @UCloudApiStable
    @UCloudApiDoc("Emitted by the provider to acknowledge a previous request")
    class Acknowledged : ShellResponse()
}

@UCloudApiStable
open class Shells(namespace: String) : CallDescriptionContainer("jobs.compute.$namespace.shell") {
    val baseContext = "/ucloud/$namespace/jobs/shells"

    init {
        title = "Provider API: Compute/Shells"
        description = """
            Provides an API for providers to give shell access to their running compute jobs.
        """.trimIndent()

        serializerLookupTable = mapOf(
            serializerEntry(OpenSession.serializer()),
            serializerEntry(JobsProviderFollowRequest.serializer()),
            serializerEntry(ShellRequest.serializer()),
            serializerEntry(ShellResponse.serializer())
        )
    }

    val open = call("open", ShellRequest.serializer(), ShellResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PUBLIC // NOTE(Dan): Access is granted via sessionIdentifier
        }

        websocket("/ucloud/$namespace/websocket")
    }
}
