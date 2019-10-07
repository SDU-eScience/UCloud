package dk.sdu.cloud.webdav

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.file.api.CopyRequest
import dk.sdu.cloud.file.api.CreateDirectoryRequest
import dk.sdu.cloud.file.api.DeleteFileRequest
import dk.sdu.cloud.file.api.DownloadByURI
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.ListDirectoryRequest
import dk.sdu.cloud.file.api.MoveRequest
import dk.sdu.cloud.file.api.MultiPartUploadDescriptions
import dk.sdu.cloud.file.api.SimpleUploadRequest
import dk.sdu.cloud.file.api.StatRequest
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.fileType
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.ServerFeature
import dk.sdu.cloud.micro.client
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.webdav.services.CloudToDavConverter
import dk.sdu.cloud.webdav.services.UserClient
import dk.sdu.cloud.webdav.services.UserClientFactory
import dk.sdu.cloud.webdav.services.appendNewElement
import dk.sdu.cloud.webdav.services.convertDocumentToString
import dk.sdu.cloud.webdav.services.newDocument
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.basicAuthenticationCredentials
import io.ktor.features.AutoHeadResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.request.header
import io.ktor.request.httpMethod
import io.ktor.request.path
import io.ktor.request.receiveChannel
import io.ktor.request.receiveOrNull
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.method
import io.ktor.routing.options
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.io.ByteWriteChannel
import kotlinx.coroutines.io.copyAndClose
import java.net.URL
import java.net.URLDecoder
import java.util.*

object WebDavMethods {
    val proppatch = HttpMethod.parse("PROPPATCH")
    val propfind = HttpMethod.parse("PROPFIND")
    val mkcol = HttpMethod.parse("MKCOL")
    val copy = HttpMethod.parse("COPY")
    val move = HttpMethod.parse("MOVE")
    val lock = HttpMethod.parse("LOCK")
    val unlock = HttpMethod.parse("UNLOCK")
}

private val xmlMapper = XmlMapper().also { it.registerKotlinModule() }

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    private val tokenValidation = micro.tokenValidation as TokenValidationJWT
    private val fileConverter = CloudToDavConverter()

    private val userClientFactory = UserClientFactory { token ->
        RefreshingJWTAuthenticator(
            micro.client,
            token,
            tokenValidation
        ).authenticateClient(OutgoingHttpCall)
    }


    override fun start() {
        val http = micro.feature(ServerFeature).ktorApplicationEngine!!.application
        http.install(AutoHeadResponse)

        http.routing {
            route("{...}") {
                options {
                    logCall()

                    val allowedMethods = (listOf(
                        WebDavMethods.propfind,
                        WebDavMethods.proppatch,
                        WebDavMethods.mkcol,
                        WebDavMethods.copy,
                        WebDavMethods.move,
                        WebDavMethods.lock,
                        WebDavMethods.unlock
                    ) + HttpMethod.DefaultMethods).joinToString(", ") { it.value }

                    respondWithDavSupport()
                    call.response.header(HttpHeaders.Allow, allowedMethods)
                    call.respondText("", status = HttpStatusCode.NoContent)
                }

                method(WebDavMethods.propfind) {
                    handle {
                        withDavHandler { (client, pathPrefix) ->
                            val request = run {
                                // Parse the request
                                val properties = runCatching {
                                    val text = call.receiveOrNull<String>() ?: return@runCatching null
                                    val tree = xmlMapper.readTree(text)
                                    require(tree.isObject)
                                    val propTree = tree["prop"] as ObjectNode

                                    propTree.fields().asSequence().toList().mapNotNull { field ->
                                        WebDavProperty.values().find { it.title == field.key }
                                    }
                                }.getOrNull() ?: listOf(WebDavProperty.AllProperties)

                                PropfindRequest(pathPrefix + requestPath, depth(), properties)
                            }

                            log.info(request.toString())

                            when (request.depth) {
                                Depth.ZERO -> {
                                    val file = FileDescriptions.stat.call(StatRequest(request.path), client).orThrow()
                                    call.respondText(
                                        status = HttpStatusCode.MultiStatus,
                                        contentType = ContentType.Application.Xml
                                    ) {
                                        newDocument("d:multistatus") {
                                            fileConverter.writeFileProps(file, this)
                                        }.convertDocumentToString()
                                    }
                                }

                                Depth.ONE -> {
                                    val files = FileDescriptions.listAtPath.call(
                                        ListDirectoryRequest(request.path, -1, 0, null, null),
                                        client
                                    ).orNull()?.items ?: listOf(
                                        FileDescriptions.stat.call(
                                            StatRequest(request.path),
                                            client
                                        ).orThrow()
                                    )

                                    call.respondText(
                                        status = HttpStatusCode.MultiStatus,
                                        contentType = ContentType.Application.Xml
                                    ) {
                                        newDocument("d:multistatus") {
                                            if (!files.any { it.path.normalize() == request.path.normalize() }) {
                                                fileConverter.writeFileProps(
                                                    StorageFile(
                                                        FileType.DIRECTORY,
                                                        request.path,
                                                        ownerName = "Unknown"
                                                    ),
                                                    this
                                                )
                                            }

                                            files.forEach { file ->
                                                fileConverter.writeFileProps(file, this)
                                            }
                                        }.convertDocumentToString()
                                    }
                                }

                                else -> {
                                    call.respondText(status = HttpStatusCode.BadRequest) { "" }
                                }
                            }
                        }
                    }
                }

                method(WebDavMethods.lock) {
                    // We don't actually support locking but macOS requires this. We simply have a fake
                    // implementation which will allow macOS to believe that we are correctly locking the files.
                    handle {
                        withDavHandler { (_, pathPrefix) ->
                            call.respondText(
                                status = HttpStatusCode.OK,
                                contentType = ContentType.Application.Xml
                            ) {
                                newDocument("d:prop") {
                                    appendNewElement("d:lockdiscovery") {
                                        appendNewElement("d:activelock") {
                                            appendNewElement("d:locktype") {
                                                appendNewElement("d:write")

                                            }
                                            appendNewElement("d:lockscope") {
                                                appendNewElement("d:exclusive")
                                            }

                                            appendNewElement("d:depth") {
                                                textContent = "infinity"
                                            }

                                            appendNewElement("d:timeout") {
                                                textContent = "Second-604800"
                                            }

                                            appendNewElement("d:locktoken") {
                                                appendNewElement("d:href") {
                                                    textContent = "urn:uuid:${UUID.randomUUID()}"
                                                }
                                            }

                                            appendNewElement("d:lockroot") {
                                                appendNewElement("d:href") {
                                                    textContent = pathPrefix + requestPath
                                                }
                                            }
                                        }
                                    }
                                }.convertDocumentToString()
                            }
                        }
                    }
                }

                method(WebDavMethods.unlock) {
                    handle {
                        logCall()
                        call.respondText(status = HttpStatusCode.NoContent) { "" }
                    }
                }

                method(HttpMethod.Get) {
                    handle {
                        withDavHandler { (client, pathPrefix) ->
                            val file = FileDescriptions.stat.call(
                                StatRequest(pathPrefix + requestPath),
                                client
                            ).orThrow()

                            if (file.fileType == FileType.DIRECTORY) {
                                call.respondText("", status = HttpStatusCode.NoContent)
                            } else {
                                val ingoing = FileDescriptions.download.call(
                                    DownloadByURI(pathPrefix + requestPath, null),
                                    client
                                ).orThrow().asIngoing()

                                val length = ingoing.length!!

                                call.respond(object : OutgoingContent.WriteChannelContent() {
                                    override val contentLength: Long? = length
                                    override suspend fun writeTo(channel: ByteWriteChannel) {
                                        ingoing.channel.copyAndClose(channel)
                                    }
                                })
                            }
                        }
                    }
                }

                method(HttpMethod.Put) {
                    handle {
                        withDavHandler { (client, pathPrefix) ->
                            val channel = call.receiveChannel()
                            MultiPartUploadDescriptions.simpleUpload.call(
                                SimpleUploadRequest(
                                    pathPrefix + requestPath,
                                    BinaryStream.outgoingFromChannel(
                                        channel,
                                        call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
                                    )
                                ),
                                client
                            ).orThrow()

                            call.respondText(status = HttpStatusCode.Created) { "" }
                        }
                    }
                }

                method(WebDavMethods.mkcol) {
                    handle {
                        withDavHandler { (client, pathPrefix) ->
                            FileDescriptions.createDirectory.call(
                                CreateDirectoryRequest(
                                    pathPrefix + requestPath,
                                    null,
                                    null
                                ),
                                client
                            ).orRethrowAs {
                                if (it.statusCode == HttpStatusCode.NotFound) {
                                    throw RPCException.fromStatusCode(HttpStatusCode.Conflict)
                                } else {
                                    throw RPCException.fromStatusCode(it.statusCode)
                                }
                            }

                            call.respondText(status = HttpStatusCode.NoContent) { "" }
                        }
                    }
                }

                method(WebDavMethods.copy) {
                    handle {
                        withDavHandler { (client, pathPrefix) ->
                            val destination = URL(call.request.header(HttpHeaders.Destination)!!)
                            val sourcePath = pathPrefix + requestPath
                            val destinationPath = (pathPrefix + destination.path).urlDecode()

                            if (depth() == Depth.ZERO) {
                                val type = FileDescriptions.stat.call(
                                    StatRequest(sourcePath),
                                    client
                                ).orThrow().fileType

                                if (type == FileType.DIRECTORY) {
                                    FileDescriptions.createDirectory.call(
                                        CreateDirectoryRequest(destinationPath, null),
                                        client
                                    ).orThrow()
                                } else {
                                    FileDescriptions.copy.call(
                                        CopyRequest(sourcePath, destinationPath),
                                        client
                                    ).orThrow()

                                    call.respondText(status = HttpStatusCode.NoContent) { "" }
                                }
                            } else {
                                FileDescriptions.copy.call(
                                    CopyRequest(
                                        sourcePath,
                                        destinationPath
                                    ),
                                    client
                                ).orThrow()

                                call.respondText(status = HttpStatusCode.NoContent) { "" }
                            }
                        }
                    }
                }

                method(WebDavMethods.move) {
                    handle {
                        withDavHandler { (client, pathPrefix) ->
                            val destination = URL(call.request.header(HttpHeaders.Destination)!!)

                            FileDescriptions.move.call(
                                MoveRequest(
                                    pathPrefix + requestPath,
                                    (pathPrefix + destination.path).urlDecode()
                                ),
                                client
                            ).orThrow()

                            call.respondText(status = HttpStatusCode.NoContent) { "" }
                        }
                    }
                }

                method(WebDavMethods.proppatch) {
                    handle {
                        logCall()
                        respondWithDavSupport()

                        // I am not sure if this is good enough. But this should be the response if we refuse to
                        // perform the update.
                        call.respondText(status = HttpStatusCode.Forbidden) { "" }
                    }
                }

                method(HttpMethod.Delete) {
                    handle {
                        withDavHandler { (client, pathPrefix) ->
                            FileDescriptions.deleteFile.call(
                                DeleteFileRequest(pathPrefix + requestPath),
                                client
                            ).orThrow()

                            call.respondText(status = HttpStatusCode.NoContent) { "" }
                        }
                    }
                }

                handle {
                    logCall()
                    call.respondText { "Any wildcard!" }
                }
            }
        }

        startServices()
    }

    private fun PipelineContext<Unit, ApplicationCall>.logCall() {
        log.info("${call.request.httpMethod} ${requestPath}")
    }

    private fun PipelineContext<Unit, ApplicationCall>.depth(): Depth {
        return when (call.request.header("Depth")) {
            "0" -> Depth.ZERO
            "1" -> Depth.ONE
            else -> Depth.INFINITY
        }
    }

    private fun PipelineContext<Unit, ApplicationCall>.respondWithDavSupport() {
        call.response.header("DAV", "1, 2") // Locking is required for MacOS to mount as R+W
    }

    private suspend inline fun PipelineContext<Unit, ApplicationCall>.withDavHandler(
        block: (client: UserClient) -> Unit
    ) {
        respondWithDavSupport()
        logCall()

        try {
            val token = call.request.basicAuthenticationCredentials()?.password
                ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)

            val client = userClientFactory.retrieveClient(token)
            block(client)
        } catch (ex: RPCException) {
            if (ex.httpStatusCode == HttpStatusCode.Unauthorized) {
                call.response.header(HttpHeaders.WWWAuthenticate, "Basic realm=\"SDUCloud\"")
            }
            call.respondText(status = ex.httpStatusCode) {
                newDocument("d:error") {}.convertDocumentToString()
            }
        }
    }

    private val PipelineContext<Unit, ApplicationCall>.requestPath: String
        get() = call.request.path().urlDecode()

    private fun String.urlDecode() = URLDecoder.decode(this, "UTF-8")
}

enum class Depth {
    ZERO,
    ONE,
    INFINITY
}

data class PropfindRequest(
    val path: String,
    val depth: Depth,
    val properties: List<WebDavProperty>
)

/**
 * See section 15 of RFC 4918
 */
enum class WebDavProperty(val title: String) {
    AllProperties("allprop"),
    CreationDate("creationdate"),
    DisplayName("displayname"),
    ContentLanguage("getcontentlanguage"),
    ContentLength("getcontentlength"),
    ETag("getetag"),
    LastModified("getlastmodified")
}
