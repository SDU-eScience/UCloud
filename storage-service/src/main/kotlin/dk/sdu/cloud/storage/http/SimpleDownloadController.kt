package dk.sdu.cloud.storage.http

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.validateAndClaim
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.file.api.BulkFileAudit
import dk.sdu.cloud.file.api.DOWNLOAD_FILE_SCOPE
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.FindByPath
import dk.sdu.cloud.file.api.SingleFileAudit
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.bearer
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.service.securityPrincipal
import dk.sdu.cloud.storage.services.BulkDownloadService
import dk.sdu.cloud.storage.services.CoreFileSystemService
import dk.sdu.cloud.storage.services.FSCommandRunnerFactory
import dk.sdu.cloud.storage.services.FSUserContext
import dk.sdu.cloud.storage.services.FileAttribute
import dk.sdu.cloud.storage.services.withContext
import dk.sdu.cloud.storage.util.tryWithFS
import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.defaultForFilePath
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.routing.Route
import kotlinx.coroutines.io.ByteWriteChannel
import kotlinx.coroutines.io.jvm.javaio.toOutputStream
import kotlinx.coroutines.runBlocking
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val BUFFER_SIZE = 1024 * 1024
private const val LOG_INTERVAL = 100

class SimpleDownloadController<Ctx : FSUserContext>(
    private val cloud: AuthenticatedCloud,
    private val commandRunnerFactory: FSCommandRunnerFactory<Ctx>,
    private val fs: CoreFileSystemService<Ctx>,
    private val bulkDownloadService: BulkDownloadService<Ctx>,
    private val tokenValidation: TokenValidation<DecodedJWT>
) : Controller {
    override val baseContext = FileDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(FileDescriptions.download) { request ->
            audit(SingleFileAudit(null, FindByPath(request.path)))

            val hasTokenFromUrl = request.token != null
            val bearer = request.token ?: call.request.bearer ?: return@implement error(
                CommonErrorMessage("Unauthorized"),
                HttpStatusCode.Unauthorized
            )

            val principal = (if (hasTokenFromUrl) {
                tokenValidation.validateAndClaim(bearer, listOf(DOWNLOAD_FILE_SCOPE), cloud)
            } else {
                tokenValidation.validateOrNull(bearer)
            }) ?: return@implement error(
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

                audit(SingleFileAudit(stat.inode, FindByPath(request.path)))

                when {
                    stat.fileType == FileType.DIRECTORY -> {
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            "attachment; filename=\"${stat.path.substringAfterLast('/')}.zip\""
                        )

                        okContentDeliveredExternally()
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

                        okContentDeliveredExternally()
                        call.respondDirectWrite(stat.size, contentType, HttpStatusCode.OK) {
                            fs.read(ctx, request.path) {
                                val stream = this
                                runBlocking {
                                    var readSum = 0L
                                    var writeSum = 0L
                                    var iterations = 0
                                    var bytes = 0

                                    val buffer = ByteArray(BUFFER_SIZE)
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
                                        if (iterations % LOG_INTERVAL == 0) {
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

        implement(FileDescriptions.bulkDownload) { request ->
            audit(BulkFileAudit(request.files.map { null }, request))

            commandRunnerFactory.withContext(call.securityPrincipal.username) { ctx ->
                val files = request.files.map { path ->
                    val absPath = "${request.prefix.removeSuffix("/")}/${path.removePrefix("/")}"
                    fs.statOrNull(ctx, absPath, setOf(FileAttribute.INODE))?.inode
                }
                audit(BulkFileAudit(files, request))
                okContentDeliveredExternally()

                call.respondDirectWrite(contentType = ContentType.Application.GZip) {
                    bulkDownloadService.downloadFiles(
                        ctx,
                        request.prefix,
                        request.files,
                        toOutputStream()
                    )
                }
            }
        }
    }


    companion object : Loggable {
        override val log = logger()
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
