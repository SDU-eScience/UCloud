package dk.sdu.cloud.calls.client

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*

@OptIn(InternalAPI::class)
fun AuthenticatedClient.withHttpBody(
    contentType: ContentType,
    contentLength: Long?,
    channel: ByteReadChannel
): AuthenticatedClient {
    return withHooks(
        beforeHook = {
            val call = (it as OutgoingHttpCall)
            call.builder.body = object : OutgoingContent.ReadChannelContent() {
                override val contentType: ContentType = contentType
                override val contentLength: Long? = contentLength
                override fun readFrom(): ByteReadChannel = channel
            }
        }
    )
}

fun AuthenticatedClient.withHttpBody(
    text: String,
    contentType: ContentType = ContentType.Text.Plain
): AuthenticatedClient {
    val encoded = text.encodeToByteArray()
    return withHttpBody(contentType, encoded.size.toLong(), ByteReadChannel(encoded))
}
