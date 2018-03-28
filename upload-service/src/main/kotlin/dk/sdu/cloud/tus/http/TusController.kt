package dk.sdu.cloud.tus.http

import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.principalRole
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.service.KafkaHttpRouteLogger
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.storage.ext.irods.ICAT
import dk.sdu.cloud.storage.ext.irods.ICATAccessEntry
import dk.sdu.cloud.storage.ext.irods.ICatDatabaseConfig
import dk.sdu.cloud.tus.api.TusDescriptions
import dk.sdu.cloud.tus.api.TusExtensions
import dk.sdu.cloud.tus.api.TusHeaders
import dk.sdu.cloud.tus.services.*
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.pipeline.PipelineContext
import io.ktor.request.header
import io.ktor.request.receiveChannel
import io.ktor.response.ApplicationResponse
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.method
import io.ktor.routing.route
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.*

class TusController(
    private val config: ICatDatabaseConfig,
    private val rados: RadosStorage,
    private val transferState: TransferStateService,
    private val checksumService: ChecksumService,
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

                method(HttpMethod.Head) {
                    TusDescriptions.findUploadStatusById.fullName?.let { reqName ->
                        install(KafkaHttpRouteLogger) { requestName = reqName }
                    }

                    handle {
                        logEntry(log, parameterIncludeFilter = { it == "id" })
                        val id = call.parameters["id"] ?: return@handle call.respond(HttpStatusCode.BadRequest)
                        val ownerParamForState =
                            if (call.isPrivileged) null
                            else call.request.validatedPrincipal.subject

                        val summary = transferState.retrieveSummary(id, ownerParamForState)
                                ?: return@handle call.respond(HttpStatusCode.NotFound)

                        // Disable cache
                        call.response.header(HttpHeaders.CacheControl, "no-store")

                        // Write current transfer state
                        call.response.tusVersion(serverConfiguration.tusVersion)
                        call.response.tusLength(summary.length)
                        call.response.tusOffset(summary.offset)
                        call.response.tusFileLocation(summary.savedAs)

                        // Response contains no body
                        call.respond(HttpStatusCode.NoContent)
                    }
                }

                method(HttpMethod.Post) {
                    TusDescriptions.uploadChunkViaPost.fullName?.let { reqName ->
                        install(KafkaHttpRouteLogger) { requestName = reqName }
                    }

                    handle {
                        logEntry(log, parameterIncludeFilter = { it == "id" })
                        if (call.request.header("X-HTTP-Method-Override").equals("PATCH", ignoreCase = true)) {
                            upload(transferState)
                        } else {
                            call.respond(HttpStatusCode.MethodNotAllowed)
                        }
                    }
                }

                method(HttpMethod.Patch) {
                    TusDescriptions.uploadChunk.fullName?.let { reqName ->
                        install(KafkaHttpRouteLogger) { requestName = reqName }
                    }

                    handle {
                        logEntry(log, parameterIncludeFilter = { it == "id" })
                        upload(transferState)
                    }
                }
            }

            method(HttpMethod.Post) {
                TusDescriptions.create.fullName?.let { reqName ->
                    install(KafkaHttpRouteLogger) { requestName = reqName }
                }

                handle {
                    logEntry(log, headerIncludeFilter = { it in TusHeaders.KnownHeaders })

                    if (!protect()) return@handle

                    val principal = call.request.validatedPrincipal
                    val isPrivileged = call.isPrivileged

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

                    val owner = metadata["owner"]?.takeIf { isPrivileged } ?: principal.subject
                    val location = metadata["location"]?.takeIf { isPrivileged }
                            ?: "/${config.defaultZone}/home/${principal.subject}/Uploads"

                    val canWrite = icat.useConnection {
                        userHasWriteAccess(owner, config.defaultZone, location).first
                    }

                    if (!canWrite) {
                        log.debug("User ($owner) is not authorized to create file at this location ($location)")
                        call.respond(HttpStatusCode.Unauthorized)
                        return@handle
                    }

                    val id = UUID.randomUUID().toString()
                    val fileName = metadata["filename"] ?: metadata["name"] ?: id

                    transaction {
                        UploadDescriptions.insert {
                            it[UploadDescriptions.id] = id
                            it[UploadDescriptions.sizeInBytes] = length
                            it[UploadDescriptions.owner] = owner
                            it[UploadDescriptions.zone] = config.defaultZone
                            it[UploadDescriptions.targetCollection] = location
                            it[UploadDescriptions.targetName] = fileName
                            it[UploadDescriptions.doChecksum] = false
                            it[UploadDescriptions.sensitive] = sensitive
                        }

                        UploadProgress.insert {
                            it[UploadProgress.id] = id
                            it[UploadProgress.numChunksVerified] = 0
                        }
                    }

                    call.response.header(HttpHeaders.Location, "${serverConfiguration.prefix}/$id")
                    call.respond(HttpStatusCode.Created)
                }
            }

            method(HttpMethod.Options) {
                TusDescriptions.probeTusConfiguration.fullName?.let { reqName ->
                    install(KafkaHttpRouteLogger) { requestName = reqName }
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

    private val ApplicationCall.isPrivileged: Boolean
        get() {
            val principalRole = request.principalRole
            return principalRole == Role.SERVICE || principalRole == Role.ADMIN
        }

    private suspend fun PipelineContext<Unit, ApplicationCall>.upload(transferStateService: TransferStateService) {
        log.debug("Handling incoming upload request")
        // Check and retrieve transfer state
        val id = call.parameters["id"] ?: return run {
            log.debug("Missing ID parameter")
            call.respond(HttpStatusCode.BadRequest)
        }

        val ownerParamForState = if (call.isPrivileged) null else call.request.validatedPrincipal.subject
        val initialState = transferState.retrieveState(id, ownerParamForState) ?: return run {
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

        if (claimedOffset != initialState.offset) {
            log.debug("Claimed offset was $claimedOffset but expected ${initialState.offset}")
            return call.respond(HttpStatusCode.Conflict)
        }

        log.info("Starting upload for: $initialState")
        // Start reading some contents
        val channel = call.receiveChannel()
        val internalBuffer = ByteBuffer.allocate(1024 * 32)
        val wrappedChannel = object : IReadChannel {
            var shouldRead = true

            override suspend fun read(dst: ByteArray, offset: Int): Int {
                val maxSize = dst.size - offset
                assert(maxSize > 0)

                if (shouldRead) {
                    val read = channel.readAvailable(internalBuffer)
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
            }
        }

        val task = rados.createUpload(id, wrappedChannel, claimedOffset, initialState.length)
        task.onProgress = { chunk ->
            val oneIndexedChunk = chunk + 1
            val numberOfChunks = Math.ceil(initialState.length / RadosStorage.BLOCK_SIZE.toDouble()).toLong()

            if (oneIndexedChunk == numberOfChunks) {
                val state = transferStateService.retrieveState(id)!!
                val irodsUser = state.user
                val irodsZone = state.zone

                val irodsCollection = state.targetCollection
                val irodsFileName = state.targetName

                // Finalize upload
                log.debug("Upload ${state.id} has been completed!")
                icat.useConnection {
                    val checksumJob = launch {
                        checksumService.computeAndAttachChecksumAndFileSize(id)
                    }

                    autoCommit = false
                    log.debug("Registration of object...")

                    val resource = findResourceByNameAndZone("child_01", "tempZone") ?: return@useConnection
                    log.debug("Using resource $resource. $irodsUser, $irodsZone, $irodsCollection")

                    val (_, entry) = userHasWriteAccess(irodsUser, irodsZone, irodsCollection)
                    if (entry != null) {
                        val availableName = findAvailableIRodsFileName(entry.objectId, irodsFileName)
                        val objectId = registerDataObject(
                            entry.objectId,
                            state.id,
                            state.length,
                            availableName,
                            irodsUser,
                            irodsZone,
                            resource
                        ) ?: return@useConnection run {
                            log.warn("Was unable to register data object! Issue in $id")
                            rollback()
                        }

                        log.debug("Auto-generated ID is $objectId")
                        val now = System.currentTimeMillis()

                        registerAccessEntry(ICATAccessEntry(objectId, entry.userId, 1200, now, now))
                        commit()

                        // Cannot do this in a single update, since we have multiple DBs and APIs. We should
                        // really agree on a single one......
                        transaction {
                            UploadDescriptions.update({ UploadDescriptions.id eq state.id }) {
                                it[UploadDescriptions.savedAs] =
                                        "${irodsCollection.removePrefix("/tempZone").removeSuffix("/")}/$availableName"
                            }
                        }

                        log.info("Object has been registered: $id")
                    } else {
                        log.info("User does not have permission to upload file to target resource!")
                    }

                    runBlocking { checksumJob.join() }
                    log.info("Checksum and file size attached successfully to file!")
                }

                transaction {
                    UploadProgress.update({ UploadProgress.id eq id }) {
                        it[numChunksVerified] = oneIndexedChunk
                    }
                }
            }
        }
        task.upload()

        log.info("Upload complete! Offset is: ${task.offset}. ${initialState.length}")

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

    private fun ApplicationResponse.tusFileLocation(savedAs: String?) {
        if (savedAs != null) header("File-Location", savedAs)
    }

    companion object {
        private val log = LoggerFactory.getLogger(TusController::class.java)
    }
}
