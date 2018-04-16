package dk.sdu.cloud.storage.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.validateAndClaim
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.storage.api.DOWNLOAD_FILE_SCOPE
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.storage.services.FileSystemService
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
    private val fs: FileSystemService
) {
    fun configure(routing: Route) = with(routing) {
        route("files") {
            implement(FileDescriptions.download) { request ->
                logEntry(log, request)

                val bearer = request.token
                val principal =
                    TokenValidation.validateAndClaim(bearer, listOf(DOWNLOAD_FILE_SCOPE), cloud)
                            ?: return@implement error(
                                CommonErrorMessage("Unauthorized"),
                                HttpStatusCode.Unauthorized
                            )

                val stat = fs.stat(principal.subject, request.path) ?: return@implement run {
                    error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
                }

                if (stat.type != FileType.FILE) return@implement error(
                    CommonErrorMessage("Not a file"),
                    HttpStatusCode.BadRequest
                )

                val contentType = ContentType.defaultForFilePath(stat.path)
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    "attachment; filename=\"${stat.path.substringAfterLast('/')}\""
                )

                call.respondDirectWrite(stat.size, contentType, HttpStatusCode.OK) {
                    val process = fs.read(principal.subject, request.path)
                    val stream = process.inputStream

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

            // Determine file list (Prune by modified [iRODS, for now], then prune by checksum [Ceph])
            // Will contain:
            //   - missingCloud: list of files missing in cloud, but present in local
            //   - missingLocal: list of files missing in local, but present in cloud
            //   - needsUpdateInCloud: list of files that can be updated from local to cloud (no conflict)
            //   - needsUpdateInLocal: list of files that can be updated from cloud to local (no conflict)
            //   - conflicting: list of files that can be updated but there are conflicting versions between
            //     cloud and local. This will happen, if since the last sync, both the cloud and local versions have
            //     changed but they do not have the same checksum.
            //   - syncCompletedAt: timestamp, to avoid clock-skew between client and server


            // Bulk download based on file list

            /*
            get("tar-test") {
                call.respondDirectWrite(contentType = ContentType.Application.GZip) {
                    val tarStream = TarOutputStream(GZIPOutputStream(toOutputStream()))
                    val random = Random()
                    tarStream.use {
                        val payloadBuffer = ByteArray(1024 * 1024)
                        repeat(10) { idx ->
                            tarStream.putNextEntry(
                                TarEntry(
                                    TarHeader.createHeader(
                                        "file-$idx.txt",
                                        payloadBuffer.size.toLong(),
                                        System.currentTimeMillis() / 1000L,
                                        false,
                                        511 // 0777
                                    )
                                )
                            )

                            payloadBuffer.forEachIndexed { index, _ -> payloadBuffer[index] = random.nextInt().toByte() }
                            tarStream.write(payloadBuffer)

                            log.info("File $idx has been sent!")
                        }
                    }
                }
            }
            */
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
