package dk.sdu.cloud.storage.http

import dk.sdu.cloud.auth.api.currentUsername
import dk.sdu.cloud.client.RESTCallDescription
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.service.KafkaHttpRouteLogger
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.service.securityPrincipal
import dk.sdu.cloud.storage.services.*
import dk.sdu.cloud.storage.util.joinPath
import dk.sdu.cloud.tus.api.TusDescriptions
import dk.sdu.cloud.tus.api.TusExtensions
import dk.sdu.cloud.tus.api.TusHeaders
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.pipeline.PipelineContext
import io.ktor.request.header
import io.ktor.response.ApplicationResponse
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.method
import io.ktor.routing.route
import kotlinx.coroutines.experimental.io.readAvailable
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory
import java.util.*

// TODO FIXME HANDLE EXCEPTIONS
// TODO FIXME HANDLE EXCEPTIONS
// TODO FIXME HANDLE EXCEPTIONS
// TODO FIXME HANDLE EXCEPTIONS
class TusController<DBSession, Ctx : FSUserContext>(
    private val db: DBSessionFactory<DBSession>,
    private val tusDao: TusDAO<DBSession>,

    private val commandRunnerFactory: FSCommandRunnerFactory<Ctx>,
    private val fs: CoreFileSystemService<Ctx>
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
                method(HttpMethod.Head) {
                    install(KafkaHttpRouteLogger) {
                        @Suppress("UNCHECKED_CAST")
                        description = TusDescriptions.findUploadStatusById as RESTCallDescription<*, *, *, Any>
                    }

                    handle {
                        logEntry(log, parameterIncludeFilter = { it == "id" })
                        val id = call.parameters["id"]?.toLongOrNull()
                                ?: return@handle call.respond(HttpStatusCode.BadRequest)

                        val summary = db.withTransaction { tusDao.findUpload(it, call.securityPrincipal.username, id) }

                        // Disable cache
                        call.response.header(HttpHeaders.CacheControl, "no-store")

                        // Write current transfer state
                        call.response.tusVersion(serverConfiguration.tusVersion)
                        call.response.tusLength(summary.sizeInBytes)
                        call.response.tusOffset(summary.progress)
                        call.response.tusFileLocation(summary.uploadPath)

                        // Response contains no body
                        call.respond(HttpStatusCode.NoContent)
                    }
                }

                method(HttpMethod.Post) {
                    install(KafkaHttpRouteLogger) {
                        @Suppress("UNCHECKED_CAST")
                        description = TusDescriptions.uploadChunkViaPost as RESTCallDescription<*, *, *, Any>
                    }

                    handle {
                        logEntry(log, parameterIncludeFilter = { it == "id" })
                        if (call.request.header("X-HTTP-Method-Override").equals("PATCH", ignoreCase = true)) {
                            upload()
                        } else {
                            call.respond(HttpStatusCode.MethodNotAllowed)
                        }
                    }
                }

                method(HttpMethod.Patch) {
                    install(KafkaHttpRouteLogger) {
                        @Suppress("UNCHECKED_CAST")
                        description = TusDescriptions.uploadChunk as RESTCallDescription<*, *, *, Any>
                    }

                    handle {
                        logEntry(log, parameterIncludeFilter = { it == "id" })
                        upload()
                    }
                }
            }

            method(HttpMethod.Post) {
                install(KafkaHttpRouteLogger) {
                    @Suppress("UNCHECKED_CAST")
                    description = TusDescriptions.create as RESTCallDescription<*, *, *, Any>
                }

                handle {
                    logEntry(log, headerIncludeFilter = { it in TusHeaders.KnownHeaders })

                    val user = call.securityPrincipal.username

                    val length = call.request.headers[TusHeaders.UploadLength]?.toLongOrNull() ?: return@handle run {
                        log.debug("Missing upload length")
                        call.respond(HttpStatusCode.BadRequest)
                    }

                    if (serverConfiguration.maxSizeInBytes != null && length > serverConfiguration.maxSizeInBytes) {
                        log.debug(
                            "Resource of length $length is larger than allowed maximum " +
                                    "${serverConfiguration.maxSizeInBytes}"
                        )
                        return@handle call.respond(HttpStatusCode(413, "Request Entity Too Large"))
                    }

                    val metadataHeader = call.request.headers[TusHeaders.UploadMetadata] ?: return@handle run {
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
                    } ?: return@handle run {
                        log.debug("Unknown or missing value for sensitive metadata")
                        call.respond(HttpStatusCode.BadRequest)
                    }

                    val location = metadata["location"] ?: "/home/$user/Uploads"
                    val fileName = metadata["filename"] ?: metadata["name"]
                    ?: return@handle call.respond(HttpStatusCode.BadRequest)

                    val id = db.withTransaction {
                        tusDao.create(
                            it, user, TusUploadCreationCommand(
                                length,
                                joinPath(location, fileName),
                                if (sensitive) SensitivityLevel.SENSITIVE else SensitivityLevel.CONFIDENTIAL
                            )
                        )
                    }

                    // TODO We should check if we are allowed to write?
                    call.response.header(HttpHeaders.Location, "${serverConfiguration.prefix}/$id")
                    call.respond(HttpStatusCode.Created)
                }
            }

            method(HttpMethod.Options) {
                install(KafkaHttpRouteLogger) {
                    @Suppress("UNCHECKED_CAST")
                    description = TusDescriptions.probeTusConfiguration as RESTCallDescription<*, *, *, Any>
                }

                handle {
                    // Probes about the server's configuration
                    logEntry(log)
                    with(serverConfiguration) {
                        call.response.tusSupportedVersions(supportedVersions)
                        call.response.tusMaxSize(maxSizeInBytes)
                        call.response.tusExtensions(listOf(TusExtensions.Creation))
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.upload() {
        log.debug("Handling incoming upload request")
        // Check and retrieve transfer state
        val id = call.parameters["id"]?.toLongOrNull() ?: return run {
            log.debug("Missing ID parameter")
            call.respond(HttpStatusCode.BadRequest)
        }

        val owner = call.request.currentUsername
        val initialState = db.withTransaction {
            tusDao.findUpload(it, owner, id)
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

        if (claimedOffset != initialState.progress) {
            log.debug("Claimed offset was $claimedOffset but expected ${initialState.progress}")
            return call.respond(HttpStatusCode.Conflict)
        }

        var wrote = 0L
        if (claimedOffset != initialState.sizeInBytes) {
            if (claimedOffset != 0L) {
                log.info("Not yet implemented (offset != 0)")
                call.respond(HttpStatusCode.InternalServerError)
                return
            }

            log.info("Starting upload for: $initialState")
            // Start reading some contents
            val channel = call.request.receiveChannel()
            val internalBuffer = ByteArray(1024 * 32)
            commandRunnerFactory.withContext(initialState.owner) { ctx ->
                fs.write(
                    ctx,
                    initialState.uploadPath,
                    WriteConflictPolicy.OVERWRITE
                ) {
                    runBlocking {
                        var read = 0
                        while (read != -1) {
                            read = channel.readAvailable(internalBuffer)

                            if (read != -1) {
                                write(internalBuffer, 0, read)
                                wrote += read
                            }
                        }
                    }
                }
            }

        } else {
            log.info("Skipping upload. We are already done")
        }

        // TODO Make resumable
        val offset = when {
            initialState.progress == initialState.sizeInBytes -> initialState.progress
            wrote == initialState.sizeInBytes -> initialState.sizeInBytes
            else -> 0L
        }

        db.withTransaction { tusDao.updateProgress(it, owner, id, offset) }

        log.info("Upload complete! Offset is: $offset $wrote. ${initialState.sizeInBytes}")

        call.response.tusOffset(offset)
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

    private fun ApplicationResponse.tusFileLocation(savedAs: String?) {
        if (savedAs != null) header("File-Location", savedAs)
    }

    companion object {
        private val log = LoggerFactory.getLogger(TusController::class.java)
    }
}
