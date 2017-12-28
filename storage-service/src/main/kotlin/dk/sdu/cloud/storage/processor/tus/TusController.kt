package dk.sdu.cloud.storage.processor.tus

import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.pipeline.PipelineContext
import io.ktor.request.header
import io.ktor.request.receiveChannel
import io.ktor.response.ApplicationResponse
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.*
import dk.sdu.cloud.storage.StorageRestServer
import dk.sdu.cloud.storage.TusConfiguration
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.util.*

class TusController(config: TusConfiguration) {
    // TODO Store this in a persistent store
    // (We can use something simple for this initially, just be careful not to bottleneck)
    private val activeTransfers = HashMap<String, InitiatedTransferState>()

    private val log = LoggerFactory.getLogger("TUS")
    private val rados = RadosStorage("client.irods", File("ceph.conf"), "irods")
    private val icat = ICAT(config)

    fun registerTusEndpoint(routing: Route, contextPath: String) {
        routing.apply {
            val serverConfiguration = InternalConfig(
                    prefix = contextPath,
                    tusVersion = SimpleSemanticVersion(1, 0, 0),
                    supportedVersions = listOf(SimpleSemanticVersion(1, 0, 0)), maxSizeInBytes = null
            )

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

                post("{id}") {
                    if (call.request.header("X-HTTP-Method-Override").equals("PATCH", ignoreCase = true)) {
                        upload()
                    } else {
                        call.respond(HttpStatusCode.MethodNotAllowed)
                    }
                }

                patch("{id}") {
                    upload()
                }

                post {
                    val connection = call.attributes[StorageRestServer.StorageSession]

                    // Create a new resource for uploading. This is non-standard as we will need to know where
                    // this resource is to be stored and such.
                    val id = UUID.randomUUID().toString()

                    val length = call.request.headers[TusHeaders.UploadLength]?.toLongOrNull() ?:
                            return@post call.respond(HttpStatusCode.BadRequest)

                    if (serverConfiguration.maxSizeInBytes != null && length > serverConfiguration.maxSizeInBytes) {
                        return@post call.respond(HttpStatusCode(413, "Request Entity Too Large"))
                    }

                    activeTransfers[id] = InitiatedTransferState(id, length, connection.connectedUser.displayName,
                            connection.connectedUser.zone, connection.paths.homeDirectory.toString(), id)
                    call.response.header(HttpHeaders.Location, "${serverConfiguration.prefix}/$id")
                    call.respond(HttpStatusCode.Created)
                }

                options {
                    // Probes about the server's configuration
                    with(serverConfiguration) {
                        call.response.tusSupportedVersions(supportedVersions)
                        call.response.tusMaxSize(maxSizeInBytes)
                        call.response.tusExtensions(listOf(TusExtensions.SduArchives))
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.upload() {
        // TODO For deferred lengths we should accept the length header here.
        log.info("Hi, we are going!")
        // Check and retrieve transfer state
        val id = call.parameters["id"] ?: return call.respond(HttpStatusCode.BadRequest)
        val transferState = activeTransfers[id] ?: return call.respond(HttpStatusCode.NotFound)

        // Check content type
        val contentType = call.request.header(HttpHeaders.ContentType) ?:
                return call.respond(HttpStatusCode.BadRequest)
        if (contentType != "application/offset+octet-stream") {
            return call.respondText("Invalid content type", status = HttpStatusCode.BadRequest)
        }

        // Check that claimed offset matches internal state. These must match without partial extension
        // support
        val claimedOffset = call.request.header(TusHeaders.UploadOffset)?.toLongOrNull() ?:
                return call.respond(HttpStatusCode.BadRequest)
        if (claimedOffset != transferState.offset) {
            return call.respond(HttpStatusCode.Conflict)
        }

        // Start reading some contents
        val channel = call.receiveChannel()
        val internalBuffer = ByteBuffer.allocate(1024 * 32)
        val wrappedChannel = object : IReadChannel {
            suspend override fun read(dst: ByteArray): Int {
                val read = channel.read(internalBuffer)
                if (read != -1) {
                    internalBuffer.flip()
                    internalBuffer.get(dst, 0, read)
                    internalBuffer.clear()
                }
                return read
            }

            override fun close() {
                channel.close()
            }
        }

        val task = rados.createUpload(id, wrappedChannel, claimedOffset, transferState.length)
        task.onProgress = { transferState.set(it) }
        task.upload()

        if (transferState.offset == transferState.length) {
            val irodsUser = transferState.irodsUser
            val irodsZone = transferState.irodsZone

            val uri = URI(transferState.targetCollection)
            val irodsCollection = "/${uri.host}${uri.path}"
            val irodsFileName = transferState.targetName

            // Finalize upload
            log.info("Upload ${transferState.id} has been completed!")
            icat.useConnection {
                autoCommit = false
                log.info("Registration of object...")
                val resource = findResourceByNameAndZone("child_01", "tempZone") ?: return@useConnection
                log.info("Using resource $resource. $irodsUser, $irodsZone, $irodsCollection")
                val entry = findAccessRightForUserInCollection(irodsUser, irodsZone, irodsCollection)
                log.info("ACL Entry: $entry")
                // TODO This is really primitive and even worse, potentially wrong
                if (entry != null && (entry.accessType == 1200L || entry.accessType == 1120L)) {
                    val objectId = registerDataObject(entry.objectId, transferState.id, transferState.length,
                            irodsFileName, irodsUser, irodsZone, resource) ?:
                            return@useConnection run { rollback() }

                    log.info("Auto-generated ID is $objectId")
                    val now = System.currentTimeMillis()

                    registerAccessEntry(ICATAccessEntry(objectId, entry.userId, 1200, now, now))
                    log.info("Done!")
                    commit()
                }
            }
        }

        call.response.tusOffset(transferState.offset)
        call.response.tusVersion(SimpleSemanticVersion(1, 0, 0))
        call.respond(HttpStatusCode.NoContent)
    }

    private data class SimpleSemanticVersion(val major: Int, val minor: Int, val patch: Int) {
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

    private data class InternalConfig(
            val prefix: String,
            val tusVersion: SimpleSemanticVersion,
            val supportedVersions: List<SimpleSemanticVersion>,
            val maxSizeInBytes: Long?
    )

    private fun ApplicationResponse.tusMaxSize(sizeInBytes: Long?) {
        val size = sizeInBytes ?: return
        assert(size >= 0)
        header(TusHeaders.MaxSize, size)
    }

    private fun ApplicationResponse.tusSupportedVersions(supportedVersions: List<SimpleSemanticVersion>) {
        header(TusHeaders.Version, supportedVersions.joinToString(",") { it.toString() })
    }

    private fun ApplicationResponse.tusExtensions(supportedExtensions: List<String>) {
        header(TusHeaders.Extension, supportedExtensions.joinToString(","))
    }

    private fun ApplicationResponse.tusOffset(currentOffset: Long) {
        assert(currentOffset >= 0)
        header(TusHeaders.UploadOffset, currentOffset)
    }

    private fun ApplicationResponse.tusLength(length: Long) {
        assert(length >= 0)
        header(TusHeaders.UploadLength, length)
    }

    private fun ApplicationResponse.tusVersion(currentVersion: SimpleSemanticVersion) {
        header(TusHeaders.Resumable, currentVersion.toString())
    }

    private object TusHeaders {
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

    private object TusExtensions {
        const val SduArchives = "SduArchive"
    }

    private class InitiatedTransferState(val id: String, val length: Long, val irodsUser: String,
                                         val irodsZone: String, val targetCollection: String,
                                         val targetName: String) {
        var offset: Long = 0
            private set

        fun set(newOffset: Long) {
            assert(newOffset >= offset)
            offset = newOffset
        }

        fun advance(byBytes: Long) {
            assert(byBytes >= 0)
            offset += byBytes
        }
    }
}
