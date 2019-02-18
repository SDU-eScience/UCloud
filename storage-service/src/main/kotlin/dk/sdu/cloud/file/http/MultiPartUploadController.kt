package dk.sdu.cloud.file.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.file.api.BulkUploadAudit
import dk.sdu.cloud.file.api.BulkUploadErrorMessage
import dk.sdu.cloud.file.api.MultiPartUploadAudit
import dk.sdu.cloud.file.api.MultiPartUploadDescriptions
import dk.sdu.cloud.file.api.UploadRequestAudit
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.services.BackgroundScope
import dk.sdu.cloud.file.services.BulkUploader
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.FSUserContext
import dk.sdu.cloud.file.services.FileSensitivityService
import dk.sdu.cloud.file.services.withContext
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.io.jvm.javaio.copyTo
import kotlinx.coroutines.launch
import org.slf4j.Logger
import java.nio.file.Files

class MultiPartUploadController<Ctx : FSUserContext>(
    private val serviceCloud: AuthenticatedClient,
    private val commandRunnerFactory: FSCommandRunnerFactory<Ctx>,
    private val fs: CoreFileSystemService<Ctx>,
    private val sensitivityService: FileSensitivityService<Ctx>
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(MultiPartUploadDescriptions.upload) {
            audit(MultiPartUploadAudit(null))

            request.asIngoing().receiveBlocks { req ->
                val sensitivity = req.sensitivity
                val owner = ctx.securityPrincipal.username

                audit(
                    MultiPartUploadAudit(
                        UploadRequestAudit(
                            req.location,
                            sensitivity,
                            owner
                        )
                    )
                )

                val upload = req.upload
                if (upload != null) {
                    commandRunnerFactory.withContext(owner) { ctx ->
                        val policy = req.policy ?: WriteConflictPolicy.OVERWRITE

                        fs.write(ctx, req.location, policy) {
                            val out = this
                            req.upload.channel.copyTo(out)
                        }

                        sensitivityService.setSensitivityLevel(ctx, req.location, req.sensitivity, owner)
                        Unit
                    }
                }
            }
            ok(Unit)
        }

        implement(MultiPartUploadDescriptions.bulkUpload) {
            val user = ctx.securityPrincipal.username

            request.asIngoing().receiveBlocks { req ->
                val uploader =
                    BulkUploader.fromFormat(req.format, commandRunnerFactory.type) ?: return@receiveBlocks error(
                        CommonErrorMessage("Unsupported format '${req.format}'"),
                        HttpStatusCode.BadRequest
                    )

                audit(BulkUploadAudit(req.location, req.policy, req.format))

                val outputFile = Files.createTempFile("upload", ".tar.gz").toFile()
                req.upload.channel.copyTo(outputFile.outputStream())
                BackgroundScope.launch {
                    uploader.upload(
                        serviceCloud,
                        fs,
                        { commandRunnerFactory(user) },
                        req.location,
                        req.policy,
                        outputFile.inputStream()
                    )
                    try {
                        outputFile.delete()
                    } catch (_: Exception) {
                    }
                }
            }

            ok(BulkUploadErrorMessage("OK"), HttpStatusCode.Accepted)
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
