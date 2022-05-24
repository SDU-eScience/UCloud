package dk.sdu.cloud.debug

import kotlinx.serialization.*

@Serializable
sealed class ServerToClient {
    @Serializable
    @SerialName("NewService")
    data class NewService(val service: ServiceMetadata) : ServerToClient()

    @Serializable
    @SerialName("Log")
    data class Log(val clearExisting: Boolean, val messages: List<DebugMessage>) : ServerToClient()
}

@Serializable
sealed class ClientToServer {
    @Serializable
    @SerialName("OpenService")
    data class OpenService(val service: Int) : ClientToServer()
}
