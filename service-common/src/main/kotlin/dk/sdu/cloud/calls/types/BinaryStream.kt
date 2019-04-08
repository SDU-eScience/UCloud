package dk.sdu.cloud.calls.types

import com.fasterxml.jackson.core.type.TypeReference
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.client.HttpClientConverter
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.HttpServerConverter
import io.ktor.application.call
import io.ktor.client.response.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.request.contentType
import io.ktor.request.receive
import kotlinx.coroutines.io.ByteReadChannel

sealed class BinaryStream {
    /**
     * An outgoing binary stream
     */
    class Outgoing(
        private val content: OutgoingContent
    ) : BinaryStream(), HttpServerConverter.OutgoingBody, HttpClientConverter.OutgoingBody {
        override fun clientOutgoingBody(call: CallDescription<*, *, *>): OutgoingContent {
            return content
        }

        override fun serverOutgoingBody(description: CallDescription<*, *, *>, call: HttpCall): OutgoingContent {
            return content
        }
    }

    /**
     * An ingoing binary stream
     */
    class Ingoing(
        val channel: ByteReadChannel,
        val contentType: ContentType? = null,
        val length: Long? = null
    ) : BinaryStream()

    fun asIngoing(): BinaryStream.Ingoing = this as BinaryStream.Ingoing

    companion object : HttpServerConverter.IngoingBody<BinaryStream>, HttpClientConverter.IngoingBody<BinaryStream> {
        override suspend fun clientIngoingBody(
            description: CallDescription<*, *, *>,
            call: HttpResponse,
            typeReference: TypeReference<BinaryStream>
        ): BinaryStream {
            return Ingoing(call.content, call.contentType(), call.contentLength())
        }

        override suspend fun serverIngoingBody(description: CallDescription<*, *, *>, call: HttpCall): BinaryStream {
            return Ingoing(
                call.call.receive(),
                call.call.request.contentType(),
                call.call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            )
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
