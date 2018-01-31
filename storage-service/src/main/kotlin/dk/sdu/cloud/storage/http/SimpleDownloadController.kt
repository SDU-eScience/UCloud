package dk.sdu.cloud.storage.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.validateAndClaim
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.storage.ext.StorageConnectionFactory
import io.ktor.application.ApplicationCall
import io.ktor.content.OutgoingContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.defaultForFilePath
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.route
import kotlinx.coroutines.experimental.io.ByteWriteChannel
import org.slf4j.LoggerFactory

class SimpleDownloadController(
    private val cloud: AuthenticatedCloud,
    private val storageConnectionFactory: StorageConnectionFactory
) {
    fun configure(routing: Route) = with(routing) {
        route("files") {
            implement(FileDescriptions.download) { request ->
                logEntry(log, request)

                val bearer = request.token
                val principal =
                    TokenValidation.validateAndClaim(bearer, listOf("downloadFile"), cloud) ?: return@implement error(
                        CommonErrorMessage("Unauthorized"),
                        HttpStatusCode.Unauthorized
                    )

                val connection = storageConnectionFactory.createForAccount(principal.subject, principal.token).capture()
                        ?: return@implement error(
                            CommonErrorMessage("Internal Server Error"),
                            HttpStatusCode.InternalServerError
                        )

                connection.use {
                    val path = connection.paths.parseAbsolute(request.path, true)
                    val stat = connection.fileQuery.stat(path).capture() ?: return@implement error(
                        CommonErrorMessage("Not found"),
                        HttpStatusCode.NotFound
                    )

                    val contentType = ContentType.defaultForFilePath(stat.path.path)
                    call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${stat.path.name}\"")

                    call.respondDirectWrite(stat.sizeInBytes, contentType, HttpStatusCode.OK) {
                        // The Jargon API will close the stream if it is transferred between threads.
                        // So we have to read from it in a blocking way. This is why we have to push it into
                        // respondDirectWrite and not do the opening before that
                        val stream = try {
                            connection.files.get(path)
                        } catch (ex: Exception) {
                            log.warn("Caught exception while downloading file from iRODS: $it")
                            log.warn(ex.stackTraceToString())
                            // Hopefully this won't happen. Because it is way too late to change anything in the
                            // response
                            throw IllegalStateException(ex)
                        }

                        stream.use {
                            var readSum = 0L
                            var writeSum = 0L
                            var iterations = 0
                            var bytes = 0

                            val buffer = ByteArray(1024 * 1024)
                            var hasMoreData = true
                            while (hasMoreData) {
                                var ptr = 0
                                val startRead = System.nanoTime()
                                while (ptr < buffer.size && hasMoreData) {
                                    val read = it.read(buffer, ptr, buffer.size - ptr)
                                    if (read <= 0) {
                                        hasMoreData = false
                                        break
                                    }
                                    ptr += read
                                    bytes += read
                                }
                                val startWrite = System.nanoTime()
                                readSum += startWrite - startRead
                                writeFully(buffer, 0, ptr)
                                writeSum += System.nanoTime() - startWrite

                                iterations++
                                if (iterations % 100 == 0) {
                                    var rStr = (readSum / iterations).toString()
                                    var wStr = (writeSum / iterations).toString()

                                    if (rStr.length > wStr.length) wStr = wStr.padStart(rStr.length, ' ')
                                    if (wStr.length > rStr.length) rStr = rStr.padStart(rStr.length, ' ')

                                    log.debug("Avg. read time:  $rStr")
                                    log.debug("Avg. write time: $wStr")
                                }
                            }
                        }
                    }
                }

            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SimpleDownloadController::class.java)
    }
}

suspend fun ApplicationCall.respondDirectWrite(
    size: Long? = null,
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    writer: suspend ByteWriteChannel.() -> Unit
) {
    val message = DirectWriteContent(writer, size, contentType, status)
    return respond(message)
}

class DirectWriteContent(
    private val writer: suspend ByteWriteChannel.() -> Unit,
    override val contentLength: Long? = null,
    override val contentType: ContentType? = null,
    override val status: HttpStatusCode? = null
) : OutgoingContent.WriteChannelContent() {
    override suspend fun writeTo(channel: ByteWriteChannel) {
        writer(channel)
    }
}
