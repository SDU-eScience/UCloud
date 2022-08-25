package dk.sdu.cloud.calls.types

import dk.sdu.cloud.calls.server.HttpCall
import io.ktor.client.statement.*
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.*
import io.ktor.utils.io.*
import java.nio.charset.Charset

sealed class BinaryStream {
    /**
     * An outgoing binary stream
     */
    class Outgoing(
        private val content: OutgoingContent
    ) : BinaryStream()

    /**
     * An ingoing binary stream
     */
    class Ingoing(
        val channel: ByteReadChannel,
        val contentType: ContentType? = null,
        val length: Long? = null,
        val contentRange: String? = null
    ) : BinaryStream()

    fun asIngoing(): BinaryStream.Ingoing = this as BinaryStream.Ingoing

    companion object {
        @OptIn(InternalAPI::class)
        suspend fun clientIngoingBody(
            call: HttpResponse,
        ): BinaryStream {
            return Ingoing(
                call.content,
                call.contentType(),
                call.contentLength(),
                call.headers[HttpHeaders.ContentRange]
            )
        }

        suspend fun serverIngoingBody(call: HttpCall): BinaryStream {
            return Ingoing(
                call.ktor.call.receiveChannel(),
                call.ktor.call.request.contentType(),
                call.ktor.call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            )
        }

        fun outgoingFromText(
            text: String,
            charset: Charset = Charsets.UTF_8,
            contentType: ContentType = ContentType.Text.Any
        ): Outgoing {
            return outgoingFromArray(text.toByteArray(charset), contentType)
        }

        fun outgoingFromArray(
            array: ByteArray,
            contentType: ContentType = ContentType.Application.OctetStream
        ): Outgoing {
            return outgoingFromChannel(ByteReadChannel(array), array.size.toLong(), contentType)
        }

        fun outgoingFromChannel(
            channel: ByteReadChannel,
            contentLength: Long? = null,
            contentType: ContentType = ContentType.Application.OctetStream
        ): Outgoing {
            return Outgoing(object : OutgoingContent.ReadChannelContent() {
                override val contentLength = contentLength
                override val contentType = contentType
                override fun readFrom(): ByteReadChannel = channel
            })
        }
    }
}
