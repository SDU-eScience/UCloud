package dk.sdu.cloud.file.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.RPCException
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
import dk.sdu.cloud.file.services.background.BackgroundScope
import dk.sdu.cloud.file.services.BulkUploader
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.FSUserContext
import dk.sdu.cloud.file.services.FileAttribute
import dk.sdu.cloud.file.services.FileSensitivityService
import dk.sdu.cloud.file.services.withContext
import dk.sdu.cloud.file.util.FSException
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
        implement(MultiPartUploadDescriptions.simpleUpload) {
            audit(MultiPartUploadAudit(null))
            val owner = ctx.securityPrincipal.username
            val policy = request.policy ?: WriteConflictPolicy.OVERWRITE
            val sensitivity = request.sensitivity

            audit(
                MultiPartUploadAudit(
                    UploadRequestAudit(
                        request.location,
                        sensitivity,
                        owner
                    )
                )
            )

            commandRunnerFactory.withContext(owner) { ctx ->
                log.debug("writing")

                val ingoingRequest = request.file.asIngoing()
                val location = fs.write(ctx, request.location, policy) {
                    ingoingRequest.channel.copyTo(this)
                }

                //handles cancellation of uploads
                if (ingoingRequest.length != null) {
                    val stat = fs.statOrNull(ctx, location, setOf(FileAttribute.SIZE))
                    if (ingoingRequest.length != stat?.size) {
                        fs.delete(ctx, location)
                        throw FSException.BadRequest("File upload aborted")
                    }
                }

                log.debug("done writing")

                if (sensitivity != null) {
                    sensitivityService.setSensitivityLevel(ctx, location, sensitivity, owner)
                }
            }
            ok(Unit)
        }

        implement(MultiPartUploadDescriptions.simpleBulkUpload) {
            val user = ctx.securityPrincipal.username
            audit(BulkUploadAudit(request.location, WriteConflictPolicy.OVERWRITE, user))

            val uploader =
                BulkUploader.fromFormat(request.format, commandRunnerFactory.type)
                    ?: throw RPCException("Unsupported format", HttpStatusCode.BadRequest)

            val archiveName = request.name ?: "upload"
            val policy = request.policy ?: WriteConflictPolicy.RENAME

            audit(BulkUploadAudit(request.location, policy, user))

            val temporaryFile = Files.createTempFile("upload", ".bin").toFile()
            temporaryFile.outputStream().use { outs ->
                request.file.asIngoing().channel.copyTo(outs)
            }

            BackgroundScope.launch {
                try {
                    uploader.upload(
                        serviceCloud,
                        fs,
                        { commandRunnerFactory(user) },
                        request.location,
                        policy,
                        temporaryFile.inputStream(),
                        request.sensitivity,
                        sensitivityService,
                        archiveName
                    )
                } finally {
                    runCatching { temporaryFile.delete() }
                }
            }

            ok(BulkUploadErrorMessage("OK"), HttpStatusCode.Accepted)
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
