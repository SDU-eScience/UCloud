package dk.sdu.cloud.file.http

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.file.services.BulkUploader
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FSUserContext
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.io.jvm.javaio.copyTo
import kotlinx.coroutines.launch
import org.slf4j.Logger
import java.nio.file.Files

class MultiPartUploadController<Ctx : FSUserContext>(
    private val serviceCloud: AuthenticatedClient,
    private val commandRunnerFactory: CommandRunnerFactoryForCalls<Ctx>,
    private val fs: CoreFileSystemService<Ctx>,
    private val backgroundScope: BackgroundScope
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

            commandRunnerFactory.withCtx(this, owner) { ctx ->
                val ingoingRequest = request.file.asIngoing()
                val location = fs.write(ctx, request.location, policy) {
                    ingoingRequest.channel.copyTo(this)
                }

                //handles cancellation of uploads
                if (ingoingRequest.length != null) {
                    val stat = fs.statOrNull(ctx, location, setOf(StorageFileAttribute.size))
                    if (ingoingRequest.length != stat?.size) {
                        fs.delete(ctx, location)
                        throw FSException.BadRequest("File upload aborted")
                    }
                }

                if (sensitivity != null) {
                    fs.setSensitivityLevel(ctx, location, sensitivity)
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

            backgroundScope.launch {
                try {
                    uploader.upload(
                        serviceCloud,
                        fs,
                        { commandRunnerFactory.createContext(this@implement, user) },
                        request.location,
                        policy,
                        temporaryFile.inputStream(),
                        request.sensitivity,
                        archiveName,
                        backgroundScope
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
