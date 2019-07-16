package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.calls.RPCException
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.url
import io.ktor.client.utils.EmptyContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.pipeline.PipelineContext
import io.ktor.util.toMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.io.writer
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.core.ExperimentalIoApi
import kotlinx.io.core.IoBuffer
import kotlinx.io.core.use
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext


fun main() = runBlocking {
    val client = HttpClient(OkHttp) {
        followRedirects = false
        engine {
            config {
                // NOTE(Dan): Anything which takes more than 15 minutes is definitely broken.
                // I do not believe we should increase this to fix other peoples broken software.
                readTimeout(15, TimeUnit.MINUTES)
                writeTimeout(15, TimeUnit.MINUTES)
                followRedirects(false)
            }
        }
    }

    embeddedServer(Netty, configure = { responseWriteTimeoutSeconds = 0 }) {
        routing {
            get("get") {
                val clientCall = client.execute(
                    HttpRequestBuilder().apply {
                        url("https://httpbin.org/get")
                        this.method = HttpMethod.Get
                    }
                )

                proxyResponseToClient(clientCall)
            }

            get("post") {
                val clientCall = client.execute(
                    HttpRequestBuilder().apply {
                        url("https://httpbin.org/post")
                        this.method = HttpMethod.Post
                        this.body = """
                            "message": "Hello!
                            "key": "fh8sfnk8na(jKLÆf8snk"
                            "Username": "user1"
                        """.trimIndent()
                    }
                )

                proxyResponseToClient(clientCall)
            }

            get("image") {
                val clientCall = client.execute(
                    HttpRequestBuilder().apply {
                        url("https://httpbin.org/image/jpeg")
                        this.method = HttpMethod.Get
                    }
                )

                proxyResponseToClient(clientCall)
            }

            get("gb") {
                val clientCall = client.execute(
                    HttpRequestBuilder().apply {
                        url("http://ipv4.download.thinkbroadband.com/1GB.zip")
                        this.method = HttpMethod.Get
                    }
                )

                proxyResponseToClient(clientCall)
            }

            get("chunked") {
                val clientCall = client.execute(
                    HttpRequestBuilder().apply {
                        url("http://anglesharp.azurewebsites.net/Chunked")
                        this.method = HttpMethod.Get
                    }
                )

                proxyResponseToClient(clientCall)

            }

        }
    }.start(wait = true)


    Unit
}
private suspend fun PipelineContext<Unit, ApplicationCall>.proxyResponseToClient(clientCall: HttpClientCall) {
    val statusCode = clientCall.response.status

    val responseContentLength = clientCall.response.contentLength()
    val responseContentType = clientCall.response.contentType()

    val responseHeaders =
        clientCall.response.headers.toMap().mapKeys { it.key.toLowerCase() }.toMutableMap().apply {
            remove(HttpHeaders.Server.toLowerCase())
            remove(HttpHeaders.ContentLength.toLowerCase())
            remove(HttpHeaders.ContentType.toLowerCase())
            remove(HttpHeaders.TransferEncoding.toLowerCase())
            remove(HttpHeaders.Upgrade.toLowerCase())
        }

    // Need last check because OKHttp does not always tell encoding or content length
    val hasResponseBody =
        responseContentLength != null ||
                clientCall.response.headers[HttpHeaders.TransferEncoding] != null ||
                clientCall.response.content.availableForRead > 0

    val tempResponse = Files.createTempFile("resp", ".bin")

    val fileChannel = FileChannel.open(
        tempResponse, StandardOpenOption.CREATE, StandardOpenOption.READ,
        StandardOpenOption.WRITE
    )

    val responseBody: OutgoingContent = if (!hasResponseBody) {
        EmptyContent
    } else {
        val readState = WebService.Communication()
        GlobalScope.launch {
            producer(
                fileChannel,
                clientCall.response.content,
                readState
            )
        }

        object : OutgoingContent.ReadChannelContent() {
            override fun readFrom(): ByteReadChannel = if (responseContentLength != null) {
                println("size: $responseContentLength")
                tempResponse.toFile().keepReadingChannel(responseContentLength, null)
            } else {
                println("unknownsize")
                tempResponse.toFile().keepReadingChannel(null, readState)
            }

            override val contentLength: Long? = responseContentLength
            override val contentType: ContentType? = responseContentType
        }
    }

    responseHeaders.forEach { (header, values) ->
        values.forEach { value ->
            call.response.headers.append(header, value)
        }
    }

    call.respond(statusCode, responseBody)
}

fun ReadableByteChannel.read(buffer: IoBuffer): Int {
    if (buffer.writeRemaining == 0) return 0
    var count = 0

    buffer.writeDirect(1) { bb ->
        count = read(bb)
    }

    return count
}

@UseExperimental(ExperimentalIoApi::class)
fun File.keepReadingChannel(
    length: Long?,
    readState: WebService.Communication?,
    coroutineContext: CoroutineContext = Dispatchers.IO
): ByteReadChannel {
    val file = RandomAccessFile(this@keepReadingChannel, "r")
    return CoroutineScope(coroutineContext).writer(coroutineContext, autoFlush = true) {
    try {
        file.use {
            val fileChannel: FileChannel = file.channel
            if (length != null) {
                channel.writeSuspendSession {
                    var read = 0L
                    while (read < length) {
                        val buffer = request(1)
                        if (buffer == null) {
                            channel.flush()
                            tryAwait(1)
                            continue
                        }

                        val rc = fileChannel.read(buffer)
                        if (rc == -1) {
                            delay(10)
                            written(0)
                            continue
                        } else {
                            read += rc
                        }
                        written(rc)
                    }
                    flush()
                }
            } else if (readState != null) {
                channel.writeSuspendSession {
                    var read = 0L
                    while (true) {
                        val buffer = request(1)
                        if (buffer == null) {
                            channel.flush()
                            tryAwait(1)
                            continue
                        }

                        val rc = fileChannel.read(buffer)
                        if (rc == -1) {
                            delay(10)
                            written(0)
                            if (readState.isDone && readState.read == read) {
                                println("produced: ${readState.read}, consumed: $read")
                                break
                            }
                            if (readState.isDone && readState.read < read ) {
                                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                            }
                            continue
                        } else {
                            read += rc
                        }
                        written(rc)
                    }
                    flush()
                }
            } else {
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }
        }
    } finally {
        delete()
    }
}.channel
}

suspend fun producer(fileChannel: FileChannel, byteReadChannel: ByteReadChannel, readState: WebService.Communication) {
    val buffer = ByteBuffer.allocate(1024 * 64)
    var totalRead = 0L
    do {
        val currentRead = byteReadChannel.readAvailable(buffer)
        if (currentRead == -1) {
            readState.isDone = true
            readState.read = totalRead
            break
        }
        buffer.flip()
        fileChannel.write(buffer)
        buffer.clear()
        totalRead += currentRead
        println("$currentRead, $totalRead")
    } while (currentRead > 0)
}
