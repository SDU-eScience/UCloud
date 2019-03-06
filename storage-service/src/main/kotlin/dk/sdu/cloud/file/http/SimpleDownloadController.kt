package dk.sdu.cloud.file.http

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.validateAndClaim
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.calls.server.bearer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.calls.server.toSecurityToken
import dk.sdu.cloud.calls.types.BinaryStream
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
import dk.sdu.cloud.file.services.FileRow
import dk.sdu.cloud.file.services.withContext
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.file.util.tryWithFS
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.TokenValidation
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.defaultForFilePath
import io.ktor.response.header
import kotlinx.coroutines.io.ByteWriteChannel
import kotlinx.coroutines.io.jvm.javaio.toOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SimpleDownloadController<Ctx : FSUserContext>(
    private val cloud: AuthenticatedClient,
    private val commandRunnerFactory: FSCommandRunnerFactory<Ctx>,
    private val fs: CoreFileSystemService<Ctx>,
    private val bulkDownloadService: BulkDownloadService<Ctx>,
    private val tokenValidation: TokenValidation<DecodedJWT>
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(FileDescriptions.download) {
            with(ctx as HttpCall) {
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
                    ctx.audit.securityPrincipalTokenToAudit = principal.toSecurityToken()
                }

                lateinit var stat: FileRow
                tryWithFS(commandRunnerFactory, principal.subject) { ctx ->
                    val mode = setOf(
                        FileAttribute.PATH,
                        FileAttribute.INODE,
                        FileAttribute.SIZE,
                        FileAttribute.FILE_TYPE,
                        FileAttribute.IS_LINK,
                        FileAttribute.LINK_TARGET
                    )

                    stat = run {
                        val stat = fs.stat(ctx, request.path, mode)

                        if (stat.isLink) {
                            // If the link is dead the linkTarget will be equal to "/"
                            if (stat.linkTarget == "/") throw FSException.NotFound()

                            fs.stat(ctx, stat.linkTarget, mode)
                        } else {
                            stat
                        }
                    }

                    filesDownloaded.add(stat.inode)
                }

                when {
                    stat.fileType == FileType.DIRECTORY -> {
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            "attachment; filename=\"${stat.path.safeFileName()}.zip\""
                        )

                        ok(
                            BinaryStream.Outgoing(
                                DirectWriteContent(
                                    contentType = ContentType.Application.Zip,
                                    status = HttpStatusCode.OK
                                ) {
                                    tryWithFS(commandRunnerFactory, principal.subject) { ctx ->
                                        ZipOutputStream(toOutputStream()).use { os ->
                                            fs.tree(
                                                ctx,
                                                stat.path,
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
                            )
                        )
                    }

                    stat.fileType == FileType.FILE -> {
                        val contentType = ContentType.defaultForFilePath(stat.path)
                        // TODO FIXME HEADERS ARE NOT ESCAPED
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            "attachment; filename=\"${stat.path.safeFileName()}\""
                        )

                        ok(
                            BinaryStream.Outgoing(
                                DirectWriteContent(
                                    contentLength = stat.size,
                                    contentType = contentType,
                                    status = HttpStatusCode.OK
                                ) {
                                    tryWithFS(commandRunnerFactory, principal.subject) { ctx ->
                                        val writeChannel = this
                                        fs.read(ctx, request.path) {
                                            val stream = this
                                            stream.copyTo(writeChannel.toOutputStream())
                                        }
                                    }
                                }
                            )
                        )
                    }

                    else -> error(
                        CommonErrorMessage("Bad request. Unsupported file type"),
                        HttpStatusCode.BadRequest
                    )
                }
            }
        }

        implement(FileDescriptions.bulkDownload) {
            val filesDownloaded = ArrayList<String?>()
            audit(BulkFileAudit(filesDownloaded, request))

            ok(
                BinaryStream.Outgoing(
                    DirectWriteContent(contentType = ContentType.Application.GZip) {
                        commandRunnerFactory.withContext(ctx.securityPrincipal.username) { ctx ->
                            bulkDownloadService.downloadFiles(
                                ctx,
                                request.prefix,
                                request.files,
                                toOutputStream(),
                                filesDownloaded
                            )
                        }
                    }
                )
            )
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

class DirectWriteContent(
    override val contentLength: Long? = null,
    override val contentType: ContentType? = null,
    override val status: HttpStatusCode? = null,
    private val writer: suspend ByteWriteChannel.() -> Unit
) : OutgoingContent.WriteChannelContent() {
    override suspend fun writeTo(channel: ByteWriteChannel) {
        writer(channel)
    }
}
