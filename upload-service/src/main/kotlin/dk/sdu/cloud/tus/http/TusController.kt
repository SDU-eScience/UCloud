package dk.sdu.cloud.tus.http

import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.principalRole
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.tus.ICatDatabaseConfig
import dk.sdu.cloud.tus.api.TusExtensions
import dk.sdu.cloud.tus.api.TusHeaders
import dk.sdu.cloud.tus.api.TusUploadEvent
import dk.sdu.cloud.tus.api.internal.UploadEventProducer
import dk.sdu.cloud.tus.services.ICAT
import dk.sdu.cloud.tus.services.IReadChannel
import dk.sdu.cloud.tus.services.RadosStorage
import dk.sdu.cloud.tus.services.TransferStateService
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
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.*

class TusController(
        private val config: ICatDatabaseConfig,
        private val rados: RadosStorage,
        private val producer: UploadEventProducer,
        private val transferState: TransferStateService,
        private val icat: ICAT
) {
    fun registerTusEndpoint(routing: Route, contextPath: String) {
        routing.apply {
            val serverConfiguration = InternalConfig(
                    prefix = contextPath,
                    tusVersion = SimpleSemanticVersion(1, 0, 0),
                    supportedVersions = listOf(SimpleSemanticVersion(1, 0, 0)), maxSizeInBytes = null
            )

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
            route("{id}") {
                protect()

                head {
                    val id = call.parameters["id"] ?: return@head call.respond(HttpStatusCode.BadRequest)
                    val summary = transferState.retrieveSummary(id, call.request.validatedPrincipal.subject) ?:
                            return@head call.respond(HttpStatusCode.NotFound)

                    // Disable cache
                    call.response.header(HttpHeaders.CacheControl, "no-store")

                    // Write current transfer state
                    call.response.tusVersion(serverConfiguration.tusVersion)
                    call.response.tusLength(summary.length)
                    call.response.tusOffset(summary.offset)

                    // Response contains no body
                    call.respond(HttpStatusCode.NoContent)
                }

                post {
                    if (call.request.header("X-HTTP-Method-Override").equals("PATCH", ignoreCase = true)) {
                        upload()
                    } else {
                        call.respond(HttpStatusCode.MethodNotAllowed)
                    }
                }

                patch {
                    upload()
                }
            }

            post {
                log.info("Received request to create upload: ${call.request.headers}")
                if (!protect()) return@post

                val principal = call.request.validatedPrincipal
                val isPrivileged = run {
                    val principalRole = call.request.principalRole
                    principalRole == Role.SERVICE || principalRole == Role.ADMIN
                }

                val length = call.request.headers[TusHeaders.UploadLength]?.toLongOrNull() ?: return@post run {
                    log.debug("Missing upload length")
                    call.respond(HttpStatusCode.BadRequest)
                }

                if (serverConfiguration.maxSizeInBytes != null && length > serverConfiguration.maxSizeInBytes) {
                    log.debug("Resource of length $length is larger than allowed maximum " +
                            "${serverConfiguration.maxSizeInBytes}")
                    return@post call.respond(HttpStatusCode(413, "Request Entity Too Large"))
                }

                val metadataHeader = call.request.headers[TusHeaders.UploadMetadata] ?: return@post run {
                    log.debug("Missing metadata")
                    call.respond(HttpStatusCode.BadRequest)
                }

                val metadata = metadataHeader.split(",").map { it.split(" ") }.map {
                    val key = it.first().toLowerCase()
                    if (it.size != 2) {
                        Pair(key, "")
                    } else {
                        val data = String(Base64.getDecoder().decode(it[1]))
                        Pair(key, data)
                    }
                }.toMap()

                val sensitive = metadata["sensitive"]?.let {
                    when (it) {
                        "true" -> true
                        "false" -> false
                        else -> null
                    }
                } ?: return@post run {
                    log.debug("Unknown or missing value for sensitive metadata")
                    call.respond(HttpStatusCode.BadRequest)
                }

                val owner = metadata["owner"]?.takeIf { isPrivileged } ?: principal.subject
                val location = metadata["location"]?.takeIf { isPrivileged } ?:
                        "/${config.defaultZone}/home/${principal.subject}/Uploads"

                val canWrite = icat.useConnection {
                    userHasWriteAccess(owner, config.defaultZone, location).first
                }

                if (!canWrite) {
                    log.debug("User is not authorized to create file at this location")
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                val id = UUID.randomUUID().toString()
                val fileName = metadata["filename"] ?: id

                val createdEvent = TusUploadEvent.Created(
                        id = id,
                        sizeInBytes = length,
                        owner = owner,
                        zone = config.defaultZone,
                        targetCollection = location,
                        sensitive = sensitive,
                        targetName = fileName,
                        doChecksum = false
                )
                producer.emit(createdEvent)
                log.info("Created upload: $createdEvent")

                call.response.header(HttpHeaders.Location, "${serverConfiguration.prefix}/$id")
                call.respond(HttpStatusCode.Created)
            }

            options {
                // Probes about the server's configuration
                with(serverConfiguration) {
                    call.response.tusSupportedVersions(supportedVersions)
                    call.response.tusMaxSize(maxSizeInBytes)
                    call.response.tusExtensions(listOf(TusExtensions.Creation))
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.upload() {
        log.debug("Handling incoming upload request")
        // Check and retrieve transfer state
        val id = call.parameters["id"] ?: return run {
            log.debug("Missing ID parameter")
            call.respond(HttpStatusCode.BadRequest)
        }

        val state = transferState.retrieveState(id, call.request.validatedPrincipal.subject) ?: return run {
            log.debug("Missing upload state for transfer with id: $id")
            call.respond(HttpStatusCode.NotFound)
        }

        // Check content type
        val contentType = call.request.header(HttpHeaders.ContentType) ?: return run {
            log.debug("Missing ContentType header")
            call.respond(HttpStatusCode.BadRequest)
        }

        if (contentType != "application/offset+octet-stream") {
            return call.respondText("Invalid content type", status = HttpStatusCode.BadRequest)
        }

        // Check that claimed offset matches internal state. These must match without partial extension
        // support
        val claimedOffset = call.request.header(TusHeaders.UploadOffset)?.toLongOrNull() ?: return run {
            log.debug("Missing upload offset header")
            call.respond(HttpStatusCode.BadRequest)
        }

        if (claimedOffset != state.offset) {
            log.debug("Claimed offset was $claimedOffset but expected ${state.offset}")
            return call.respond(HttpStatusCode.Conflict)
        }

        log.info("Starting upload for: $state")
        // Start reading some contents
        val channel = call.receiveChannel()
        val internalBuffer = ByteBuffer.allocate(1024 * 32)
        val wrappedChannel = object : IReadChannel {
            var shouldRead = true

            suspend override fun read(dst: ByteArray, offset: Int): Int {
                val maxSize = dst.size - offset
                assert(maxSize > 0)

                if (shouldRead) {
                    val read = channel.read(internalBuffer)
                    if (read != -1) {
                        internalBuffer.flip()
                        return if (maxSize > read) {
                            internalBuffer.get(dst, offset, read)
                            internalBuffer.clear()
                            read
                        } else {
                            internalBuffer.get(dst, offset, maxSize)

                            // We need to deposit the remainder of our internal buffer
                            // So we don't clear and set the state to shouldRead = false
                            shouldRead = false
                            maxSize
                        }
                    } else {
                        // Input channel has no more data
                        return -1
                    }
                } else {
                    val depositSize = Math.min(internalBuffer.remaining(), maxSize)
                    internalBuffer.get(dst, offset, depositSize)

                    if (!internalBuffer.hasRemaining()) {
                        // We have deposited what we had left in the buffer. Go back to reading state
                        internalBuffer.clear()
                        shouldRead = true
                    }
                    return depositSize
                }
            }

            override fun close() {
                channel.close()
            }
        }

        val task = rados.createUpload(id, wrappedChannel, claimedOffset, state.length)
        task.onProgress = {
            runBlocking {
                producer.emit(TusUploadEvent.ChunkVerified(
                        id = id,
                        chunk = it + 1, // Chunks are 1-indexed, callbacks are 0-indexed
                        numChunks = Math.ceil(state.length / RadosStorage.BLOCK_SIZE.toDouble()).toLong()
                ))
            }
        }
        task.upload()

        log.info("Upload complete! Offset is: ${task.offset}. ${state.length}")

        call.response.tusOffset(task.offset)
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


    companion object {
        private val log = LoggerFactory.getLogger(TusController::class.java)
    }
}
