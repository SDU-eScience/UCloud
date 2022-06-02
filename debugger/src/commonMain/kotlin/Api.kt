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

    @Serializable
    @SerialName("Statistics")
    data class Statistics(
        // NOTE(Dan): Path to the service. If this locates an internal node, then this will refer to an aggregate of all
        // nodes beneath it.
        val servicePath: String,
        val client: ResponseStats,
        val server: ResponseStats,
        val logs: LogStats,
    ) : ServerToClient() {
        @Serializable
        data class ResponseStats(
            val p25: Double,
            val p50: Double,
            val p75: Double,
            val p99: Double,
            val min: Double,
            val avg: Double,
            val max: Double,
            val count: Long,
            val successes: Long,
            val errors: Long,
        )

        @Serializable
        data class LogStats(
            var everything: Long = 0L,
            var details: Long = 0L,
            var normal: Long = 0L,
            var odd: Long = 0L,
            var wrong: Long = 0L,
            var dangerous: Long = 0L,
        ) {
            operator fun plusAssign(other: LogStats) {
                everything += other.everything
                details += other.details
                normal += other.normal
                odd += other.odd
                wrong += other.wrong
                dangerous += other.dangerous
            }
        }
    }
}

@Serializable
sealed class ClientToServer {
    @Serializable
    @SerialName("OpenService")
    data class OpenService(val service: Int) : ClientToServer()

    @Serializable
    @SerialName("UpdateInterests")
    data class UpdateInterests(val interests: List<String>) : ClientToServer()
}
