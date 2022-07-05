package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ShellRequest {
    @Serializable
    @SerialName("initialize")
    data class Initialize(
        val sessionIdentifier: String,
        val cols: Int = 80,
        val rows: Int = 24
    ) : ShellRequest()

    @Serializable
    @SerialName("input")
    data class Input(val data: String) : ShellRequest() {
        override fun toString(): String = "Input()"
    }

    @Serializable
    @SerialName("resize")
    data class Resize(val cols: Int, val rows: Int) : ShellRequest()
}

@Serializable
sealed class ShellResponse {
    @Serializable
    @SerialName("initialize")
    class Initialized() : ShellResponse()

    @Serializable
    @SerialName("data")
    data class Data(val data: String) : ShellResponse() {
        override fun toString(): String = "Data()"
    }

    @Serializable
    @SerialName("ack")
    class Acknowledged : ShellResponse()
}

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
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
