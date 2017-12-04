package dk.sdu.escience.tus

import kotlinx.coroutines.experimental.delay
import org.jetbrains.ktor.application.ApplicationCallPipeline
import org.jetbrains.ktor.application.install
import org.jetbrains.ktor.application.log
import org.jetbrains.ktor.features.CallLogging
import org.jetbrains.ktor.features.DefaultHeaders
import org.jetbrains.ktor.features.StatusPages
import org.jetbrains.ktor.gson.GsonSupport
import org.jetbrains.ktor.host.embeddedServer
import org.jetbrains.ktor.http.HttpHeaders
import org.jetbrains.ktor.http.HttpStatusCode
import org.jetbrains.ktor.netty.Netty
import org.jetbrains.ktor.pipeline.PipelinePhase
import org.jetbrains.ktor.request.header
import org.jetbrains.ktor.request.receiveChannel
import org.jetbrains.ktor.response.ApplicationResponse
import org.jetbrains.ktor.response.header
import org.jetbrains.ktor.response.respond
import org.jetbrains.ktor.response.respondText
import org.jetbrains.ktor.routing.*
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

sealed class TransferState(val id: String)
class InitiatedTransferState(id: String, val length: Long) : TransferState(id) {
    var offset: Long = 0
        private set

    fun advance(byBytes: Int) {
        assert(byBytes >= 0)
        offset += byBytes
    }
}

val idSequence = AtomicInteger()
val activeTransfers = HashMap<String, InitiatedTransferState>()

fun main(args: Array<String>) {
    // Probably want to use a small database for keeping track of existing uploads
    val serverConfiguration = ServerConfiguration(
            prefix = "/upload",
            tusVersion = SimpleSemanticVersion(1, 0, 0),
            supportedVersions = listOf(SimpleSemanticVersion(1, 0, 0)),
            maxSizeInBytes = null
    )

    embeddedServer(Netty, 8080) {
        install(CallLogging)
        install(DefaultHeaders)

        install(GsonSupport) {
            // Gson settings goes here
        }

        routing {
            route(serverConfiguration.prefix) {
                // Intercept unsupported TUS client version
                intercept(ApplicationCallPipeline.Infrastructure) {
                    val version = call.request.headers[TusHeaders.Resumable]
                    if (version != null) {
                        val parsedVersion = SimpleSemanticVersion.parse(version)
                        if (parsedVersion == null) {
                            call.respondText("Invalid client version: $version", status = HttpStatusCode.BadRequest)
                            return@intercept finish()
                        }

                        if (parsedVersion !in serverConfiguration.supportedVersions) {
                            call.respondText("Version not supported", status = HttpStatusCode.PreconditionFailed)
                            return@intercept finish()
                        }
                    }
                }

                get {
                    call.respondText {
                        "GET endpoint not supported. Running TUS version ${serverConfiguration.tusVersion}"
                    }
                }

                // These use the ID returned from the Creation extension
                head("{id}") {
                    val id = call.parameters["id"] ?: return@head call.respond(HttpStatusCode.BadRequest)
                    val transferState = activeTransfers[id] ?: return@head call.respond(HttpStatusCode.NotFound)

                    // Disable cache
                    call.response.header(HttpHeaders.CacheControl, "no-store")

                    // Write current transfer state
                    call.response.tusVersion(serverConfiguration.tusVersion)
                    call.response.tusLength(transferState.length)
                    call.response.tusOffset(transferState.offset)

                    // Response contains no body
                    call.respond(HttpStatusCode.NoContent)
                }

                patch("{id}") {
                    // TODO For deferred lengths we should accept the length header here.
                    println("Hi, we are going!")
                    // Check and retrieve transfer state
                    val id = call.parameters["id"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                    val transferState = activeTransfers[id] ?: return@patch call.respond(HttpStatusCode.NotFound)

                    // Check content type
                    val contentType = call.request.header(HttpHeaders.ContentType) ?:
                            return@patch call.respond(HttpStatusCode.BadRequest)
                    if (contentType != "application/offset+octet-stream") {
                        return@patch call.respondText("Invalid content type", status = HttpStatusCode.BadRequest)
                    }

                    // Check that claimed offset matches internal state. These must match without partial extension
                    // support
                    val claimedOffset = call.request.header(TusHeaders.UploadOffset)?.toLongOrNull() ?:
                            return@patch call.respond(HttpStatusCode.BadRequest)
                    if (claimedOffset != transferState.offset) {
                        return@patch call.respond(HttpStatusCode.Conflict)
                    }

                    // Start reading some contents
                    val channel = call.receiveChannel()
                    val buffer = ByteBuffer.allocate(1024 * 32)
                    // TODO Implement in transfer state?
                    while (transferState.offset < transferState.length) {
                        // TODO If needed we should catch this co-routine being canceled
                        // Basically discard everything for now
                        buffer.rewind()
                        val readBytes = channel.read(buffer)
                        if (readBytes == -1) {
                            break
                        } else {
                            log.debug("Read $readBytes bytes from stream")
                            delay(500)
                            transferState.advance(readBytes)
                        }
                        // We should support async IO in the storage library via co-routines
                    }

                    // TODO The problem with storing files in this staging area is that iput can completely negate this
                    // As a result we will end up having to validate files through two separate interfaces:
                    // the iput interface (which would have to be done through rules) and this upload interface.
                    // I know which one I would prefer, and which would be faster, but there is no real way around
                    // this. I also fear that the rule-based solution might fire on files that are already internal
                    // to the system.
                    //
                    // We should also discuss how exactly these rules are to be performed. Should we validate files
                    // that are internal to the system? What do we allow to be stored? All of these questions should
                    // be answered ASAP.

                    call.response.tusOffset(transferState.offset)
                    call.response.tusVersion(serverConfiguration.tusVersion)
                    call.respond(HttpStatusCode.NoContent)
                }

                post {
                    // Create a new resource for uploading
                    val next = idSequence.getAndIncrement()
                    val id = "transfer-$next"

                    // TODO Support deferred length
                    val length = call.request.headers[TusHeaders.UploadLength]?.toLongOrNull() ?:
                            return@post call.respond(HttpStatusCode.BadRequest)

                    if (serverConfiguration.maxSizeInBytes != null && length > serverConfiguration.maxSizeInBytes) {
                        return@post call.respond(HttpStatusCode(413, "Request Entity Too Large"))
                    }

                    activeTransfers[id] = InitiatedTransferState(id, length)
                    call.response.header(HttpHeaders.Location, "${serverConfiguration.prefix}/$id")
                    call.respond(HttpStatusCode.Created)
                }

                options {
                    // Probes about the server's configuration
                    with(serverConfiguration) {
                        call.response.tusSupportedVersions(supportedVersions)
                        call.response.tusMaxSize(maxSizeInBytes)
                        call.response.tusExtensions(listOf(TusExtensions.Creation, TusExtensions.SduArchives))
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }
    }.start(wait = true)
}

data class SimpleSemanticVersion(val major: Int, val minor: Int, val patch: Int) {
    override fun toString() = "$major.$minor.$patch"

    companion object {
        fun parse(value: String): SimpleSemanticVersion? {
            val tokens = value.split('.')
            if (tokens.size != 3) return null
            val mapped = tokens.map { it.toIntOrNull() }
            if (mapped.any { it == null }) return null
            return SimpleSemanticVersion(mapped[0]!!, mapped[1]!!, mapped[2]!!)
        }
    }
}

data class ServerConfiguration(
        val prefix: String,
        val tusVersion: SimpleSemanticVersion,
        val supportedVersions: List<SimpleSemanticVersion>,
        val maxSizeInBytes: Long?
)

data class ArchiveManifest(val files: List<FileManifest>)
data class FileManifest(val path: String, val sizeInBytes: Long)

fun ApplicationResponse.tusMaxSize(sizeInBytes: Long?) {
    val size = sizeInBytes ?: return
    assert(size >= 0)
    header(TusHeaders.MaxSize, size)
}

fun ApplicationResponse.tusSupportedVersions(supportedVersions: List<SimpleSemanticVersion>) {
    header(TusHeaders.Version, supportedVersions.joinToString(",") { it.toString() })
}

fun ApplicationResponse.tusExtensions(supportedExtensions: List<String>) {
    header(TusHeaders.Extension, supportedExtensions.joinToString(","))
}

fun ApplicationResponse.tusOffset(currentOffset: Long) {
    assert(currentOffset >= 0)
    header(TusHeaders.UploadOffset, currentOffset)
}

fun ApplicationResponse.tusLength(length: Long) {
    assert(length >= 0)
    header(TusHeaders.UploadLength, length)
}

fun ApplicationResponse.tusVersion(currentVersion: SimpleSemanticVersion) {
    header(TusHeaders.Resumable, currentVersion.toString())
}

object TusHeaders {
    /**
     * The Tus-Max-Size response header MUST be a non-negative integer indicating the maximum allowed size of an
     * entire upload in bytes. The Server SHOULD set this header if there is a known hard limit.
     */
    const val MaxSize = "Tus-Max-Size"

    /**
     * The Tus-Extension response header MUST be a comma-separated list of the extensions supported by the Server.
     * If no extensions are supported, the Tus-Extension header MUST be omitted.
     */
    const val Extension = "Tus-Extension"

    /**
     * The Upload-Offset request and response header indicates a byte offset within a resource. The value MUST be a
     * non-negative integer.
     */
    const val UploadOffset = "Upload-Offset"

    /**
     * The Upload-Length request and response header indicates the size of the entire upload in bytes. The value
     * MUST be a non-negative integer.
     */
    const val UploadLength = "Upload-Length"

    /**
     * The Tus-Resumable header MUST be included in every request and response except for OPTIONS requests.
     * The value MUST be the version of the protocol used by the Client or the Server.
     *
     * If the the version specified by the Client is not supported by the Server, it MUST respond with the 412
     * Precondition Failed status and MUST include the Tus-Version header into the response. In addition, the
     * Server MUST NOT process the request.
     */
    const val Resumable = "Tus-Resumable"

    /**
     * The Tus-Version response header MUST be a comma-separated list of protocol versions supported by the Server.
     * The list MUST be sorted by Server’s preference where the first one is the most preferred one.
     */
    const val Version = "Tus-Version"

    object Creation {
        const val DeferLength = "Upload-Defer-Length"
        const val UploadMetadata = "Upload-Metadata"
    }
}

object TusExtensions {
    const val Creation = "Creation"
    const val SduArchives = "SduArchive"
}
