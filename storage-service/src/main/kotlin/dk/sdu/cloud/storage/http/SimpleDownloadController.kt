package dk.sdu.cloud.storage.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.validateAndClaim
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.storage.api.DOWNLOAD_FILE_SCOPE
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.storage.services.BulkDownloadService
import dk.sdu.cloud.storage.services.FileSystemService
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.content.OutgoingContent
import io.ktor.http.*
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondWrite
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.method
import io.ktor.routing.route
import kotlinx.coroutines.experimental.io.ByteWriteChannel
import kotlinx.coroutines.experimental.io.jvm.javaio.toOutputStream
import org.slf4j.LoggerFactory
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SimpleDownloadController(
    private val cloud: AuthenticatedCloud,
    private val fs: FileSystemService,
    private val bulkDownloadService: BulkDownloadService
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

                val ctx = fs.openContext(principal.subject)
                val stat = fs.stat(ctx, request.path) ?: return@implement run {
                    error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
                }

                when {
                    stat.type == FileType.DIRECTORY -> {
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            "attachment; filename=\"${stat.path.substringAfterLast('/')}.zip\""
                        )

                        call.respondDirectWrite(contentType = ContentType.Application.Zip, status = HttpStatusCode.OK) {
                            ZipOutputStream(toOutputStream()).use { os ->
                                fs.syncList(ctx, request.path) { item ->
                                    val filePath = item.path.substringAfter(stat.path).removePrefix("/")

                                    if (item.type == FileType.FILE) {
                                        os.putNextEntry(
                                            ZipEntry(
                                                filePath
                                            )
                                        )
                                        fs.read(ctx, item.path).copyTo(os)
                                        os.closeEntry()
                                    } else if (item.type == FileType.DIRECTORY) {
                                        os.putNextEntry(ZipEntry(filePath.removeSuffix("/") + "/"))
                                        os.closeEntry()
                                    }
                                }
                            }
                        }
                    }

                    stat.type == FileType.FILE -> {
                        val contentType = ContentType.defaultForFilePath(stat.path)
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            "attachment; filename=\"${stat.path.substringAfterLast('/')}\""
                        )

                        // See #185
                        // ktor unable to send files larger than 2GB
                        val sizeForWorkaroundIssue185 = if (stat.size >= Int.MAX_VALUE) null else stat.size

                        call.respondDirectWrite(sizeForWorkaroundIssue185, contentType, HttpStatusCode.OK) {
                            val stream = fs.read(ctx, request.path)

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

                    else -> error(CommonErrorMessage("Bad request. Unsupported file type"), HttpStatusCode.BadRequest)
                }


            }

            implement(FileDescriptions.bulkDownload) {
                logEntry(log, it)

                call.respondDirectWrite(contentType = ContentType.Application.GZip) {
                    bulkDownloadService.downloadFiles(
                        call.request.validatedPrincipal.subject,
                        it.prefix,
                        it.files,
                        toOutputStream()
                    )
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
