package dk.sdu.cloud.storage.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.principalRole
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.storage.services.BulkUploadService
import dk.sdu.cloud.storage.services.CoreFileSystemService
import dk.sdu.cloud.storage.services.FSCommandRunnerFactory
import dk.sdu.cloud.storage.services.FSUserContext
import dk.sdu.cloud.storage.util.tryWithFS
import dk.sdu.cloud.upload.api.BulkUploadAudit
import dk.sdu.cloud.upload.api.BulkUploadErrorMessage
import dk.sdu.cloud.upload.api.MultiPartUploadAudit
import dk.sdu.cloud.upload.api.MultiPartUploadDescriptions
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.request.receiveMultipart
import io.ktor.routing.Route
import kotlinx.coroutines.experimental.launch
import org.slf4j.LoggerFactory
import java.nio.file.Files

class MultiPartUploadController<Ctx : FSUserContext>(
    private val commandRunnerFactory: FSCommandRunnerFactory<Ctx>,
    private val fs: CoreFileSystemService<Ctx>,
    private val bulkUploadService: BulkUploadService<Ctx>
): Controller {
    override val baseContext = MultiPartUploadDescriptions.baseContext

    override fun configure(routing: Route):Unit = with(routing) {
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
                                SensitivityLevel.values().find { it.name == part.value }
                                    ?.let { sensitivity = it }
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

                            audit(MultiPartUploadAudit(location!!, sensitivity, owner))
                            okContentDeliveredExternally()

                            assert(
                                owner == call.request.validatedPrincipal.subject ||
                                        call.request.principalRole.isPrivileged()
                            )

                            commandRunnerFactory.withContext(owner) { ctx ->
                                fs.write(ctx, location!!, WriteConflictPolicy.OVERWRITE) {
                                    val out = this
                                    part.streamProvider().use { it.copyTo(out) }
                                }

                                ok(Unit)
                            }
                        }
                    }

                    else -> {
                        // Do nothing
                    }
                }
                part.dispose()
            }
        }

        implement(MultiPartUploadDescriptions.bulkUpload) {
            logEntry(log, it)

            var policy: WriteConflictPolicy? = null
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
                                        policy = WriteConflictPolicy.valueOf(part.value)
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

                                audit(BulkUploadAudit(path, policy, format))
                                okContentDeliveredExternally()

                                val outputFile = Files.createTempFile("upload", ".tar.gz").toFile()
                                part.streamProvider().copyTo(outputFile.outputStream())
                                launch {
                                    commandRunnerFactory.withContext(user) {
                                        bulkUploadService.bulkUpload(
                                            it,
                                            path,
                                            format,
                                            policy,
                                            outputFile.inputStream()
                                        )
                                    }
                                    try {
                                        outputFile.delete()
                                    } catch (_: Exception) {
                                    }
                                }

                                ok(BulkUploadErrorMessage("OK", emptyList()), HttpStatusCode.Accepted)
                            }
                        }

                        else -> {
                            // Do nothing
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