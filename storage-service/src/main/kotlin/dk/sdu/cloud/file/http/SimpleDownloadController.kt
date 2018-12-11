package dk.sdu.cloud.file.http

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.validateAndClaim
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.file.api.BulkFileAudit
import dk.sdu.cloud.file.api.DOWNLOAD_FILE_SCOPE
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.FindByPath
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.services.BulkDownloadService
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.FSUserContext
import dk.sdu.cloud.file.services.FileAttribute
import dk.sdu.cloud.file.services.withContext
import dk.sdu.cloud.file.util.tryWithFS
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.bearer
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.securityPrincipal
import dk.sdu.cloud.service.toSecurityToken
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
            val filesDownloaded = ArrayList<String?>()
            audit(BulkFileAudit(filesDownloaded, FindByPath(request.path)))

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

            if (hasTokenFromUrl) {
                overridePrincipalToken(principal.toSecurityToken())
            }

            tryWithFS(commandRunnerFactory, principal.subject) { ctx ->
                val mode = setOf(
                    FileAttribute.PATH,
                    FileAttribute.INODE,
                    FileAttribute.SIZE,
                    FileAttribute.FILE_TYPE,
                    FileAttribute.IS_LINK,
                    FileAttribute.LINK_TARGET
                )

                val stat = run {
                    val stat = fs.stat(ctx, request.path, mode)
                    if (stat.isLink) fs.stat(ctx, stat.linkTarget, mode) else stat
                }

                filesDownloaded.add(stat.inode)

                when {
                    stat.fileType == FileType.DIRECTORY -> {
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            "attachment; filename=\"${stat.path.safeFileName()}.zip\""
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
                                    setOf(FileAttribute.FILE_TYPE, FileAttribute.PATH, FileAttribute.INODE)
                                ).forEach { item ->
                                    filesDownloaded.add(item.inode)
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
                        // TODO FIXME HEADERS ARE NOT ESCAPED
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            "attachment; filename=\"${stat.path.safeFileName()}\""
                        )

                        okContentDeliveredExternally()
                        call.respondDirectWrite(stat.size, contentType, HttpStatusCode.OK) {
                            val writeChannel = this
                            fs.read(ctx, request.path) {
                                val stream = this
                                stream.copyTo(writeChannel.toOutputStream())
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
                val filesDownloaded = ArrayList<String?>()
                audit(BulkFileAudit(filesDownloaded, request))
                okContentDeliveredExternally()

                call.respondDirectWrite(contentType = ContentType.Application.GZip) {
                    bulkDownloadService.downloadFiles(
                        ctx,
                        request.prefix,
                        request.files,
                        toOutputStream(),
                        filesDownloaded
                    )
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()

        private val safeFileNameChars =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ._-+,@£$€!½§~'=()[]{}0123456789".let {
                CharArray(it.length) { i -> it[i] }.toSet()
            }

        private fun String.safeFileName(): String {
            val normalName = fileName()
            return buildString(normalName.length) {
                normalName.forEach {
                    when (it) {
                        in safeFileNameChars -> append(it)
                        else -> append('_')
                    }
                }
            }
        }
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
