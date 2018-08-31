package dk.sdu.cloud.storage.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.validateAndClaim
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.files.api.DOWNLOAD_FILE_SCOPE
import dk.sdu.cloud.files.api.FileDescriptions
import dk.sdu.cloud.files.api.FileType
import dk.sdu.cloud.storage.services.*
import dk.sdu.cloud.storage.util.tryWithFS
import io.ktor.application.ApplicationCall
import io.ktor.content.OutgoingContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.defaultForFilePath
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.routing.Route
import kotlinx.coroutines.experimental.io.ByteWriteChannel
import kotlinx.coroutines.experimental.io.jvm.javaio.toOutputStream
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SimpleDownloadController<Ctx : FSUserContext>(
    private val cloud: AuthenticatedCloud,
    private val commandRunnerFactory: FSCommandRunnerFactory<Ctx>,
    private val fs: CoreFileSystemService<Ctx>,
    private val bulkDownloadService: BulkDownloadService<Ctx>
): Controller {
    override val baseContext = FileDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(FileDescriptions.download) { request ->
            logEntry(log, request)

            val bearer = request.token
            val principal =
                TokenValidation.validateAndClaim(bearer, listOf(DOWNLOAD_FILE_SCOPE), cloud)
                        ?: return@implement error(
                            CommonErrorMessage("Unauthorized"),
                            HttpStatusCode.Unauthorized
                        )

            tryWithFS(commandRunnerFactory, principal.subject) { ctx ->
                val stat =
                    fs.stat(
                        ctx,
                        request.path,
                        setOf(FileAttribute.PATH, FileAttribute.INODE, FileAttribute.SIZE, FileAttribute.FILE_TYPE)
                    )

                when {
                    stat.fileType == FileType.DIRECTORY -> {
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            "attachment; filename=\"${stat.path.substringAfterLast('/')}.zip\""
                        )

                        call.respondDirectWrite(
                            contentType = ContentType.Application.Zip,
                            status = HttpStatusCode.OK
                        ) {
                            ZipOutputStream(toOutputStream()).use { os ->
                                fs.tree(
                                    ctx,
                                    request.path,
                                    setOf(FileAttribute.FILE_TYPE, FileAttribute.PATH)
                                ).forEach { item ->
                                    val filePath = item.path.substringAfter(stat.path).removePrefix("/")

                                    if (item.fileType == FileType.FILE) {
                                        os.putNextEntry(
                                            ZipEntry(
                                                filePath
                                            )
                                        )
                                        fs.read(ctx, item.path) { copyTo(os) }
                                        os.closeEntry()
                                    } else if (item.fileType == FileType.DIRECTORY) {
                                        os.putNextEntry(ZipEntry(filePath.removeSuffix("/") + "/"))
                                        os.closeEntry()
                                    }
                                }
                            }
                        }
                    }

                    stat.fileType == FileType.FILE -> {
                        val contentType = ContentType.defaultForFilePath(stat.path)
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            "attachment; filename=\"${stat.path.substringAfterLast('/')}\""
                        )

                        // See #185
                        // ktor unable to send files larger than 2GB
                        val sizeForWorkaroundIssue185 = if (stat.size >= Int.MAX_VALUE) null else stat.size

                        call.respondDirectWrite(sizeForWorkaroundIssue185, contentType, HttpStatusCode.OK) {
                            fs.read(ctx, request.path) {
                                val stream = this
                                runBlocking {
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
                                            val read = stream.read(buffer, ptr, buffer.size - ptr)
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

                    else -> error(
                        CommonErrorMessage("Bad request. Unsupported file type"),
                        HttpStatusCode.BadRequest
                    )
                }
            }
        }

        implement(FileDescriptions.bulkDownload) {
            logEntry(log, it)

            commandRunnerFactory.withContext(call.request.validatedPrincipal.subject) { ctx ->
                call.respondDirectWrite(contentType = ContentType.Application.GZip) {
                    bulkDownloadService.downloadFiles(
                        ctx,
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
