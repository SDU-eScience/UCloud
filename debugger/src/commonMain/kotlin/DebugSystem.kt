package dk.sdu.cloud.debug

import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class DebugContext(
    val id: String,
    var path: List<String> = emptyList(),
    val parent: String? = null,
    var depth: Int = 0,
)

@Serializable
sealed class DebugMessage : Comparable<DebugMessage> {
    abstract val context: DebugContext
    abstract val timestamp: Long
    abstract val importance: MessageImportance
    abstract val messageType: MessageType

    override fun compareTo(other: DebugMessage): Int {
        return timestamp.compareTo(other.timestamp)
    }

    interface WithCall {
        val call: String?
    }

    interface WithResponseCode {
        val responseCode: Int
    }

    interface WithResponseTime {
        val responseTime: Long
    }

    @SerialName("client_request")
    @Serializable
    data class ClientRequest(
        override val context: DebugContext,
        override val timestamp: Long = Time.now(),
        override val importance: MessageImportance,
        override val call: String?,
        val payload: JsonElement?,
        val resolvedHost: String,
    ) : DebugMessage(), WithCall {
        override val messageType = MessageType.CLIENT
    }

    @SerialName("client_response")
    @Serializable
    data class ClientResponse(
        override val context: DebugContext,
        override val timestamp: Long = Time.now(),
        override val importance: MessageImportance,
        override val call: String?,
        val response: JsonElement?,
        override val responseCode: Int,
        override val responseTime: Long,
    ) : DebugMessage(), WithCall, WithResponseCode, WithResponseTime {
        override val messageType = MessageType.CLIENT
    }

    @SerialName("server_request")
    @Serializable
    data class ServerRequest(
        override val context: DebugContext,
        override val timestamp: Long = Time.now(),
        override val importance: MessageImportance,
        override val call: String?,
        val payload: JsonElement?,
    ) : DebugMessage(), WithCall {
        override val messageType = MessageType.SERVER
    }

    @SerialName("server_response")
    @Serializable
    data class ServerResponse(
        override val context: DebugContext,
        override val timestamp: Long = Time.now(),
        override val importance: MessageImportance,
        override val call: String?,
        val response: JsonElement?,
        override val responseCode: Int,
        override val responseTime: Long,
    ) : DebugMessage(), WithCall, WithResponseCode, WithResponseTime {
        override val messageType = MessageType.SERVER
    }

    @SerialName("database_connection")
    @Serializable
    data class DatabaseConnection(
        override val context: DebugContext,
        val isOpen: Boolean,
        override val timestamp: Long = Time.now(),
        override val importance: MessageImportance = MessageImportance.TELL_ME_EVERYTHING,
    ) : DebugMessage() {
        override val messageType = MessageType.DATABASE
    }

    enum class DBTransactionEvent {
        OPEN,
        COMMIT,
        ROLLBACK
    }

    @SerialName("database_transaction")
    @Serializable
    data class DatabaseTransaction(
        override val context: DebugContext,
        val event: DBTransactionEvent,
        override val timestamp: Long = Time.now(),
        override val importance: MessageImportance = MessageImportance.TELL_ME_EVERYTHING,
    ) : DebugMessage() {
        override val messageType = MessageType.DATABASE
    }

    @SerialName("database_query")
    @Serializable
    data class DatabaseQuery(
        override val context: DebugContext,
        val query: String,
        val parameters: JsonObject,
        override val importance: MessageImportance = MessageImportance.THIS_IS_NORMAL,
        override val timestamp: Long = Time.now(),
    ) : DebugMessage() {
        override val messageType = MessageType.DATABASE
    }

    @SerialName("database_response")
    @Serializable
    data class DatabaseResponse(
        override val context: DebugContext,
        override val importance: MessageImportance = MessageImportance.IMPLEMENTATION_DETAIL,
        override val timestamp: Long = Time.now(),
        override val responseTime: Long,
    ) : DebugMessage(), WithResponseTime {
        override val messageType = MessageType.DATABASE
    }

    @SerialName("log")
    @Serializable
    data class Log(
        override val context: DebugContext,
        val message: String,
        val extras: JsonObject? = null,
        override val importance: MessageImportance = MessageImportance.IMPLEMENTATION_DETAIL,
        override val timestamp: Long = Time.now(),
    ) : DebugMessage() {
        override val messageType = MessageType.LOG
    }

    companion object {
        fun sortingKey(timestamp: Long): DebugMessage {
            return Log(DebugContext(""), "", timestamp = timestamp)
        }
    }
}

enum class MessageType {
    SERVER,
    DATABASE,
    CLIENT,
    LOG
}

enum class MessageImportance {
    /**
     * You (the developer/operator) only want to see this message if something is badly misbehaving, and you are
     * desperate for clues. It should primarily contain the insignificant details.
     */
    TELL_ME_EVERYTHING,

    /**
     * You (the developer/operator) want to see this message if something is mildly misbehaving. It should contain the
     * most important implementation details.
     */
    IMPLEMENTATION_DETAIL,

    /**
     * Indicates that an ordinary event has occurred. Most RPCs, which aren't chatty, fall into this category.
     */
    THIS_IS_NORMAL,

    /**
     * Indicates that something might be wrong, but not for certain. A developer/operator might want to see this, but
     * it is not critical.
     */
    THIS_IS_ODD,

    /**
     * A clear message that something is wrong. A developer/operator want to see this as soon as possible, but it can
     * probably wait until the next morning.
     */
    THIS_IS_WRONG,

    /**
     * This should never happen. This event needs to be investigated immediately.
     *
     * Only pick this category if you feel comfortable waking up your co-workers in the middle of the night to tell
     * them about this.
     */
    THIS_IS_DANGEROUS
}

