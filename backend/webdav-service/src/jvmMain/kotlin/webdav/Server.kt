package dk.sdu.cloud.webdav

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
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
import dk.sdu.cloud.file.api.components
import dk.sdu.cloud.file.api.fileType
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.ServerFeature
import dk.sdu.cloud.micro.client
import dk.sdu.cloud.micro.feature
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
import dk.sdu.cloud.webdav.services.urlEncode
import io.ktor.application.*
import io.ktor.client.request.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.method
import io.ktor.routing.options
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
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

    private val fileConverter = CloudToDavConverter()

    private val userClientFactory =
        UserClientFactory(ClientAndBackend(micro.client, OutgoingHttpCall), micro.tokenValidation as TokenValidationJWT)

    private val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)

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
                        withDavHandler { (client, pathPrefix, username) ->
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

                                val path = getRealPath(requestPath, pathPrefix)

                                PropfindRequest(path, depth(), properties)
                            }

                            log.info(request.toString())
                            val components = request.path.components()

                            when (request.depth) {
                                Depth.ZERO -> {
                                    val file = FileDescriptions.stat.call(StatRequest(request.path), client).orThrow()

                                    log.info("Found: $file")

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
                                    log.debug("$components")
                                    val normalFiles = FileDescriptions.listAtPath.call(
                                        ListDirectoryRequest(request.path, -1, 0, null, null),
                                        client
                                    ).orNull()?.items ?: listOf(
                                        FileDescriptions.stat.call(
                                            StatRequest(request.path),
                                            client
                                        ).orThrow()
                                    )

                                    val virtualFolders = emptyList<StorageFile>()

                                    val files = normalFiles + virtualFolders

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
                        TODO()
                        /*
                        withDavHandler { (client, pathPrefix) ->
                            val file = FileDescriptions.stat.call(
                                StatRequest(getRealPath(requestPath, pathPrefix)),
                                client
                            ).orThrow()

                            if (file.fileType == FileType.DIRECTORY) {
                                call.respondText("", status = HttpStatusCode.NoContent)
                            } else {
                                val requestRanges = call.request.header(HttpHeaders.Range)

                                val ingoing = client.client.call(
                                    FileDescriptions.download,
                                    DownloadByURI(getRealPath(requestPath, pathPrefix), null),
                                    OutgoingHttpCall,
                                    beforeFilters = { call ->
                                        client.authenticator(call)
                                        call.builder.header(HttpHeaders.Range, requestRanges)
                                    },
                                    afterFilters = { call ->
                                        client.afterFilters?.invoke(call)
                                    }
                                ).orThrow().asIngoing()

                                val length = ingoing.length!!
                                val contentRange = ingoing.contentRange
                                val statusCode =
                                    if (contentRange != null) HttpStatusCode.PartialContent
                                    else HttpStatusCode.OK

                                if (contentRange != null) {
                                    call.response.header(HttpHeaders.ContentRange, contentRange)
                                }

                                call.respond(statusCode, object : OutgoingContent.WriteChannelContent() {
                                    override val contentLength: Long? = length
                                    override suspend fun writeTo(channel: ByteWriteChannel) {
                                        ingoing.channel.copyAndClose(channel)
                                    }
                                })
                            }
                        }
                         */
                    }
                }

                method(HttpMethod.Put) {
                    handle {
                        withDavHandler { (client, pathPrefix) ->
                            if (isPathVirtual(requestPath)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

                            val channel = call.receiveChannel()
                            MultiPartUploadDescriptions.simpleUpload.call(
                                SimpleUploadRequest(getRealPath(requestPath, pathPrefix)),
                                client.withHttpBody(
                                    ContentType.Application.OctetStream,
                                    call.request.header(HttpHeaders.ContentLength)?.toLongOrNull(),
                                    channel
                                )
                            ).orThrow()

                            call.respondText(status = HttpStatusCode.Created) { "" }
                        }
                    }
                }

                method(WebDavMethods.mkcol) {
                    handle {
                        withDavHandler { (client, pathPrefix) ->
                            if (isPathVirtual(requestPath)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

                            FileDescriptions.createDirectory.call(
                                CreateDirectoryRequest(
                                    getRealPath(requestPath, pathPrefix),
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
                            if (isPathVirtual(requestPath)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                            if (isPathVirtual(destination.path)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

                            val sourcePath = getRealPath(requestPath, pathPrefix)
                            val destinationPath = getRealPath(destination.path.urlDecode(), pathPrefix)

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
                            if (isPathVirtual(requestPath)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                            if (isPathVirtual(destination.path)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

                            FileDescriptions.move.call(
                                MoveRequest(
                                    getRealPath(requestPath, pathPrefix),
                                    getRealPath((destination.path).urlDecode(), pathPrefix)
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

                        val properties = runCatching {
                            val text = call.receiveOrNull<String>() ?: return@runCatching null
                            val tree = xmlMapper.readTree(text)
                            require(tree.isObject)
                            val propsWeDontCareAbout = ArrayList<String>()

                            runCatching {
                                ((tree["set"] as ObjectNode).get("prop") as ObjectNode).fields().forEach {
                                    propsWeDontCareAbout.add(it.key)
                                }
                            }

                            runCatching {
                                ((tree["remove"] as ObjectNode).get("prop") as ObjectNode).fields().forEach {
                                    propsWeDontCareAbout.add(it.key)
                                }
                            }

                            propsWeDontCareAbout
                        }.getOrNull() ?: emptyList<String>()

                        // Some systems, namely Windows, will refuse to perform certain file operations unless it is
                        // allowed to patch its Windows specific timestamps. Here we simply pretend that these
                        // operations are successful. Note: Jackson is throwing away the namespace information so we
                        // are simply guessing that the namespace is correct.

                        call.respondText(
                            contentType = ContentType.Application.Xml,
                            status = HttpStatusCode.MultiStatus
                        ) {
                            newDocument("d:multistatus") {
                                appendNewElement("d:response") {
                                    appendNewElement("d:href") {
                                        textContent = "/" + requestPath.split("/")
                                            .filter { it.isNotEmpty() }
                                            .joinToString("/") { it.urlEncode() }
                                    }

                                    properties.forEach { prop ->
                                        appendNewElement("d:propstat") {
                                            appendNewElement("d:prop") {
                                                val namespace =
                                                    if (prop.contains("win32", ignoreCase = true)) "z:" else "d:"
                                                appendNewElement(namespace + prop)
                                                appendNewElement("d:status") {
                                                    textContent = "HTTP/1.1 200 OK"
                                                }
                                            }
                                        }
                                    }
                                }
                            }.convertDocumentToString()
                        }
                    }
                }

                method(HttpMethod.Delete) {
                    handle {
                        withDavHandler { (client, pathPrefix) ->
                            if (isPathVirtual(requestPath)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

                            FileDescriptions.deleteFile.call(
                                DeleteFileRequest(getRealPath(requestPath, pathPrefix)),
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

    private fun getRealPath(
        requestPath: String,
        pathPrefix: String
    ): String {
        return pathPrefix + requestPath
    }

    private fun isPathVirtual(path: String): Boolean {
        return false
    }

    private fun PipelineContext<Unit, ApplicationCall>.logCall() {
        log.info("${call.request.httpMethod} $requestPath")
        log.debug(call.request.headers.flattenEntries().toString())
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
            val authHeader = call.request.header(HttpHeaders.Authorization) ?: throw RPCException.fromStatusCode(
                HttpStatusCode.Unauthorized
            )
            if (!authHeader.startsWith("Basic ")) throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
            val credentials = authHeader.substringAfter("Basic ")
            val decoded = Base64.getDecoder().decode(credentials).toString(Charsets.ISO_8859_1)
            val token = decoded.substringAfterLast(":")

            val client = userClientFactory.retrieveClient(token)
            block(client)
        } catch (ex: RPCException) {
            if (ex.httpStatusCode == HttpStatusCode.Unauthorized) {
                call.response.header(HttpHeaders.WWWAuthenticate, "Basic realm=\"SDUCloud\"")
            }
            call.respondText(contentType = ContentType.Application.Xml, status = ex.httpStatusCode) {
                newDocument("d:error") {}.convertDocumentToString()
            }
        }
    }

    private val PipelineContext<Unit, ApplicationCall>.requestPath: String
        get() = call.request.path().urlDecode()

    private fun String.urlDecode() = URLDecoder.decode(
        // The URLDecoder shouldn't be using '+' as space. We replace '+' with the percent encoded version which will
        // then get decoded back into a '+'.
        this.replace("+", "%2B"),
        "UTF-8"
    )
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
