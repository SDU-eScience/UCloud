package dk.sdu.cloud.webdav

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.call
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
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.ServerFeature
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.webdav.services.CloudToDavConverter
import dk.sdu.cloud.webdav.services.appendNewElement
import dk.sdu.cloud.webdav.services.convertDocumentToString
import dk.sdu.cloud.webdav.services.newDocument
import io.ktor.application.ApplicationCall
import io.ktor.application.call
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
import io.ktor.util.flattenEntries
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

    override fun start() {
        // TODO This will only work in dev
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val pathPrefix = "/home/admin@dev"
        val fileConverter = CloudToDavConverter()

        val http = micro.feature(ServerFeature).ktorApplicationEngine!!.application
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
                        try {
                            logCall()
                            respondWithDavSupport()

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

                                PropfindRequest(pathPrefix + call.request.path(), depth(), properties)
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
                                    ).orThrow().items

                                    call.respondText(
                                        status = HttpStatusCode.MultiStatus,
                                        contentType = ContentType.Application.Xml
                                    ) {
                                        newDocument("d:multistatus") {
                                            fileConverter.writeFileProps(
                                                StorageFile(
                                                    FileType.DIRECTORY,
                                                    request.path,
                                                    ownerName = "Unknown"
                                                ),
                                                this
                                            )

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
                        } catch (ex: RPCException) {
                            call.respondText(status = ex.httpStatusCode) {
                                newDocument("d:error") {}.convertDocumentToString()
                            }
                        }
                    }
                }

                method(WebDavMethods.lock) {
                    // We don't actually support locking but macOS requires this. We simply have a fake
                    // implementation which will allow macOS to believe that we are correctly locking the files.
                    handle {
                        logCall()
                        try {
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
                                                    textContent = pathPrefix + call.request.path()
                                                }
                                            }
                                        }
                                    }
                                }.convertDocumentToString()
                            }
                        } catch (ex: RPCException) {
                            call.respondText(status = ex.httpStatusCode) {
                                newDocument("d:error") {}.convertDocumentToString()
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
                        try {
                            logCall()
                            val ingoing = FileDescriptions.download.call(
                                DownloadByURI(pathPrefix + call.request.path(), null),
                                client
                            ).orThrow().asIngoing()

                            val length = ingoing.length!!

                            call.respond(object : OutgoingContent.WriteChannelContent() {
                                override val contentLength: Long? = length
                                override suspend fun writeTo(channel: ByteWriteChannel) {
                                    ingoing.channel.copyAndClose(channel)
                                }
                            })
                        } catch (ex: RPCException) {
                            call.respondText(status = ex.httpStatusCode) {
                                newDocument("d:error") {}.convertDocumentToString()
                            }
                        }
                    }
                }

                method(HttpMethod.Put) {
                    // TODO The propfind thing needs to respond something other than 404
                    handle {
                        try {
                            respondWithDavSupport()
                            logCall()

                            val channel = call.receiveChannel()
                            MultiPartUploadDescriptions.simpleUpload.call(
                                SimpleUploadRequest(
                                    pathPrefix + call.request.path(),
                                    BinaryStream.outgoingFromChannel(
                                        channel,
                                        call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
                                    )
                                ),
                                client
                            ).orThrow()

                            call.respondText(status = HttpStatusCode.Created) { "" }
                        } catch (ex: RPCException) {
                            call.respondText(status = ex.httpStatusCode) {
                                newDocument("d:error") {}.convertDocumentToString()
                            }
                        }
                    }
                }

                method(WebDavMethods.mkcol) {
                    handle {
                        try {
                            logCall()
                            respondWithDavSupport()

                            FileDescriptions.createDirectory.call(
                                CreateDirectoryRequest(
                                    pathPrefix + call.request.path(),
                                    null,
                                    null
                                ),
                                client
                            ).orThrow()

                            call.respondText(status = HttpStatusCode.NoContent) { "" }
                        } catch (ex: RPCException) {
                            call.respondText(status = ex.httpStatusCode) {
                                newDocument("d:error") {}.convertDocumentToString()
                            }
                        }
                    }
                }

                method(WebDavMethods.copy) {
                    handle {
                        try {
                            logCall()
                            respondWithDavSupport()
                            val destination = URL(call.request.header(HttpHeaders.Destination)!!)
                            val sourcePath = pathPrefix + call.request.path()
                            val destinationPath = URLDecoder.decode(pathPrefix + destination.path, "UTF-8")

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
                        } catch (ex: RPCException) {
                            call.respondText(status = ex.httpStatusCode) {
                                newDocument("d:error") {}.convertDocumentToString()
                            }
                        }
                    }
                }

                method(WebDavMethods.move) {
                    handle {
                        try {
                            logCall()
                            respondWithDavSupport()

                            val destination = URL(call.request.header(HttpHeaders.Destination)!!)
                            // TODO Handle depth = 0 for folders. This should just create a folder with that name.

                            FileDescriptions.move.call(
                                MoveRequest(
                                    pathPrefix + call.request.path(),
                                    pathPrefix + destination.path
                                ),
                                client
                            ).orThrow()

                            call.respondText(status = HttpStatusCode.NoContent) { "" }
                        } catch (ex: RPCException) {
                            call.respondText(status = ex.httpStatusCode) {
                                newDocument("d:error") {}.convertDocumentToString()
                            }
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
                        try {
                            logCall()
                            respondWithDavSupport()

                            FileDescriptions.deleteFile.call(
                                DeleteFileRequest(pathPrefix + call.request.path()),
                                client
                            ).orThrow()

                            call.respondText(status = HttpStatusCode.NoContent) { "" }
                        } catch (ex: RPCException) {
                            call.respondText(status = ex.httpStatusCode) {
                                newDocument("d:error") {}.convertDocumentToString()
                            }
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
        log.info("Handling new call!")
        log.info(call.request.path())
        log.info(call.request.httpMethod.toString())
        log.info(call.request.headers.flattenEntries().toString())
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
        call.response.header("Ms-Author-Via", "DAV")
    }
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
