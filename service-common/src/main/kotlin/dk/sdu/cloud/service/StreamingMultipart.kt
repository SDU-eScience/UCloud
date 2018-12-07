package dk.sdu.cloud.service

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.CIOHeaders
import io.ktor.http.cio.MultipartEvent
import io.ktor.http.cio.parseMultipart
import io.ktor.util.InternalAPI
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.io.ByteReadChannel
import org.slf4j.Logger
import java.io.Closeable
import kotlin.coroutines.CoroutineContext

class StreamingMultipart private constructor(
    override val coroutineContext: CoroutineContext,
    contentType: String,
    contentLength: Long?,
    channel: ByteReadChannel
) : CoroutineScope {
    @UseExperimental(KtorExperimentalAPI::class) // Bad idea
    private val eventChannel = parseMultipart(channel, contentType, contentLength)

    private suspend fun readNextEvent(): MultipartEvent.MultipartPart? {
        for (event in eventChannel) {
            if (event is MultipartEvent.MultipartPart) {
                return event
            } else {
                event.release()
            }
        }
        return null
    }

    private fun readPartHeaders(headers: Headers): PartHeaders {
        val contentDisposition = headers[HttpHeaders.ContentDisposition]?.let { ContentDisposition.parse(it) }
            ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
        val contentLength = headers[HttpHeaders.ContentLength]?.toLongOrNull()
        val contentType = headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
        return PartHeaders(contentDisposition, contentType, contentLength)
    }

    @UseExperimental(InternalAPI::class) // Very bad idea
    suspend fun readPart(): StreamingPart? {
        val next = readNextEvent() ?: return null
        val allHeaders = CIOHeaders(next.headers.await())
        val partHeaders = readPartHeaders(allHeaders)
        return StreamingPart(partHeaders, allHeaders, next.body) { next.release() }
    }

    companion object : Loggable {
        override val log: Logger = logger()

        fun construct(ctx: PipelineContext<*, ApplicationCall>): StreamingMultipart {
            with(ctx) {
                val contentType = call.request.headers[HttpHeaders.ContentType]?.takeIf { it.startsWith("multipart/") }
                    ?: throw RPCException.fromStatusCode(
                        HttpStatusCode.BadRequest,
                        "Bad ${HttpHeaders.ContentType} header"
                    )

                val contentLength =
                    call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()

                val channel = call.request.receiveChannel()
                return StreamingMultipart(
                    coroutineContext,
                    contentType,
                    contentLength,
                    channel
                )
            }
        }
    }
}

data class StreamingPart(
    val partHeaders: PartHeaders,
    val headers: Headers,
    val channel: ByteReadChannel,
    val dispose: () -> Unit
) : Closeable {
    override fun close() {
        dispose()
    }
}

data class PartHeaders(
    val contentDisposition: ContentDisposition,
    val contentType: ContentType?,
    val contentLength: Long?
)
