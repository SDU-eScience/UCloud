package dk.sdu.cloud.debug

import java.nio.ByteBuffer

sealed class BinaryDebugMessage<Self : BinaryDebugMessage<Self>>(
    type: Byte,
    buf: ByteBuffer,
    offset: Int = 0
) : BinaryFrame(buf, offset) {
    var type by Schema.type
    var ctxGeneration by Schema.ctxGeneration
    var ctxParent by Schema.ctxParent
    var ctxId by Schema.ctxId
    var timestamp by Schema.timestamp
    var importance by Schema.importance
    var id by Schema.id

    init {
        if (buf.capacity() != 0 && !buf.isReadOnly) {
            this.type = type
        }
    }

    private var rsv1 by Schema.rsv1

    override fun toString(): String {
        return "(ctxGeneration=$ctxGeneration, ctxParent=$ctxParent, ctxId=$ctxId, timestamp=$timestamp, importance=$importance, id=$id)"
    }

    companion object Schema : BinaryFrameSchema() {
        val type = int1() // 1
        val ctxGeneration = int8() // 9
        val ctxParent = int4() // 13
        val ctxId = int4() // 17
        val timestamp = int8() // 25
        val importance = enum<MessageImportance>() // 26
        val id = int4() // 30

        val rsv1 = int2() // 32
    }

    abstract fun create(buf: ByteBuffer, offset: Int): Self

    class ClientRequest(buf: ByteBuffer, offset: Int = 0) : BinaryDebugMessage<ClientRequest>(1.toByte(), buf, offset) {
        var call by Schema.call
        var payload by Schema.payload

        override val schema = Schema

        override fun create(buf: ByteBuffer, offset: Int): ClientRequest = ClientRequest(buf, offset)

        companion object Schema : BinaryFrameSchema(BinaryDebugMessage) {
            val call = text(64)
            val payload = text(64)
        }
    }

    class ClientResponse(buf: ByteBuffer, offset: Int = 0) :
        BinaryDebugMessage<ClientResponse>(2.toByte(), buf, offset) {
        var responseCode by Schema.responseCode
        var responseTime by Schema.responseTime
        var call by Schema.call
        var response by Schema.response

        override val schema = Schema
        override fun create(buf: ByteBuffer, offset: Int): ClientResponse = ClientResponse(buf, offset)

        companion object Schema : BinaryFrameSchema(BinaryDebugMessage) {
            val responseCode = int1()
            val responseTime = int4()
            val call = text(64)
            val response = text(64)
        }
    }

    class ServerRequest(buf: ByteBuffer, offset: Int = 0) : BinaryDebugMessage<ServerRequest>(3.toByte(), buf, offset) {
        var call by Schema.call
        var payload by Schema.payload

        override val schema = Schema
        override fun create(buf: ByteBuffer, offset: Int): ServerRequest = ServerRequest(buf, offset)

        companion object Schema : BinaryFrameSchema(BinaryDebugMessage) {
            val call = text(64)
            val payload = text(64)
        }
    }

    class ServerResponse(buf: ByteBuffer, offset: Int = 0) :
        BinaryDebugMessage<ServerResponse>(4.toByte(), buf, offset) {
        var responseCode by Schema.responseCode
        var responseTime by Schema.responseTime
        var call by Schema.call
        var response by Schema.response

        override val schema = Schema
        override fun create(buf: ByteBuffer, offset: Int): ServerResponse = ServerResponse(buf, offset)

        companion object Schema : BinaryFrameSchema(BinaryDebugMessage) {
            val responseCode = int1()
            val responseTime = int4()
            val call = text(64)
            val response = text(64)
        }
    }

    class DatabaseTransaction(buf: ByteBuffer, offset: Int = 0) :
        BinaryDebugMessage<DatabaseTransaction>(5.toByte(), buf, offset) {
        var event by Schema.event

        override val schema = Schema
        override fun create(buf: ByteBuffer, offset: Int): DatabaseTransaction = DatabaseTransaction(buf, offset)

        companion object Schema : BinaryFrameSchema(BinaryDebugMessage) {
            val event = enum<DBTransactionEvent>()
        }
    }

    class DatabaseQuery(buf: ByteBuffer, offset: Int = 0) : BinaryDebugMessage<DatabaseQuery>(6.toByte(), buf, offset) {
        var parameters by Schema.parameters
        var query by Schema.query

        override val schema = Schema
        override fun create(buf: ByteBuffer, offset: Int): DatabaseQuery = DatabaseQuery(buf, offset)

        companion object Schema : BinaryFrameSchema(BinaryDebugMessage) {
            val parameters = text(64)
            val query = text(128)
        }
    }

    class DatabaseResponse(buf: ByteBuffer, offset: Int = 0) :
        BinaryDebugMessage<DatabaseResponse>(7.toByte(), buf, offset) {
        var responseTime by Schema.responseTime

        override val schema = Schema
        override fun create(buf: ByteBuffer, offset: Int): DatabaseResponse = DatabaseResponse(buf, offset)

        companion object Schema : BinaryFrameSchema(BinaryDebugMessage) {
            val responseTime = int4()
        }
    }

    class Log(buf: ByteBuffer, offset: Int = 0) : BinaryDebugMessage<Log>(8.toByte(), buf, offset) {
        var message by Schema.message
        var extra by Schema.extra

        override val schema = Schema
        override fun create(buf: ByteBuffer, offset: Int): Log = Log(buf, offset)

        override fun toString(): String = "Log($message, $extra, ${super.toString()})"

        companion object Schema : BinaryFrameSchema(BinaryDebugMessage) {
            val message = text(128)
            val extra = text(32)
        }
    }
}

enum class DBTransactionEvent {
    COMMIT,
    ROLLBACK
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

const val FRAME_SIZE = 256
const val LOG_FILE_SIZE = 1024 * 1024 * 16L
