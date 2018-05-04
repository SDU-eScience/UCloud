package dk.sdu.cloud.storage.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.principalRole
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.storage.api.BulkUploadErrorMessage
import dk.sdu.cloud.storage.api.BulkUploadOverwritePolicy
import dk.sdu.cloud.storage.api.MultiPartUploadDescriptions
import dk.sdu.cloud.storage.api.SensitivityLevel
import dk.sdu.cloud.storage.services.UploadService
import dk.sdu.cloud.storage.services.tryWithFS
import io.ktor.content.PartData
import io.ktor.content.forEachPart
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveMultipart
import io.ktor.routing.Route
import io.ktor.routing.route
import org.slf4j.LoggerFactory

class MultiPartUploadController(private val uploadService: UploadService) {
    fun configure(routing: Route) = with(routing) {
        route("upload") {
            protect()

            implement(MultiPartUploadDescriptions.upload) {
                logEntry(log, it)

                // TODO Support in RESTDescriptions for multi-parts would be nice
                val multipart = call.receiveMultipart()
                var location: String? = null
                var sensitivity: SensitivityLevel = SensitivityLevel.CONFIDENTIAL
                var owner: String = call.request.validatedPrincipal.subject

                multipart.forEachPart { part ->
                    log.debug("Received part ${part.name}")
                    when (part) {
                        is PartData.FormItem -> {
                            when (part.name) {
                                "location" -> location = part.value

                                "sensitivity" -> {
                                    // If we can parse it, set it
                                    SensitivityLevel.values().find { it.name == part.value }?.let { sensitivity = it }
                                }

                                "owner" -> {
                                    if (call.request.principalRole.isPrivileged()) {
                                        owner = part.value
                                    }
                                }
                            }
                        }

                        is PartData.FileItem -> {
                            if (part.name == "upload") {
                                if (location == null) {
                                    error(
                                        CommonErrorMessage("Bad request. Missing location or upload"),
                                        HttpStatusCode.BadRequest
                                    )
                                    return@forEachPart
                                }

                                assert(
                                    owner == call.request.validatedPrincipal.subject ||
                                            call.request.principalRole.isPrivileged()
                                )

                                uploadService.upload(owner, location!!) {
                                    val out = this
                                    part.streamProvider().use { it.copyTo(out) }
                                }

                                ok(Unit)
                            }
                        }
                    }

                    part.dispose()
                }
            }

            implement(MultiPartUploadDescriptions.bulkUpload) {
                logEntry(log, it)

                var policy: BulkUploadOverwritePolicy? = null
                var path: String? = null
                var format: String? = null
                var error = false

                val user = call.request.validatedPrincipal.subject

                tryWithFS {
                    val multipart = call.receiveMultipart()
                    multipart.forEachPart { part ->
                        log.debug("Received part ${part.name}")

                        when (part) {
                            is PartData.FormItem -> {
                                when (part.name) {
                                    "policy" -> {
                                        try {
                                            policy = BulkUploadOverwritePolicy.valueOf(part.value)
                                        } catch (ex: Exception) {
                                            error(
                                                CommonErrorMessage("Bad request"),
                                                HttpStatusCode.BadRequest
                                            )

                                            error = true
                                        }
                                    }

                                    "path" -> path = part.value

                                    "format" -> {
                                        format = part.value

                                        if (format != "tgz") {
                                            error(
                                                CommonErrorMessage("Unsupported format '$format'"),
                                                HttpStatusCode.BadRequest
                                            )

                                            error = true
                                        }
                                    }
                                }
                            }

                            is PartData.FileItem -> {
                                if (part.name == "upload") {
                                    if (error || path == null || policy == null || format == null) return@forEachPart

                                    @Suppress("NAME_SHADOWING") val path = path!!
                                    @Suppress("NAME_SHADOWING") val policy = policy!!
                                    @Suppress("NAME_SHADOWING") val format = format!!

                                    val rejectedFiles =
                                        uploadService.bulkUpload(user, path, format, policy, part.streamProvider())

                                    ok(BulkUploadErrorMessage("OK", rejectedFiles))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun Role.isPrivileged(): Boolean = when (this) {
        Role.SERVICE, Role.ADMIN -> true
        else -> false
    }

    companion object {
        private val log = LoggerFactory.getLogger(MultiPartUploadController::class.java)
    }
}