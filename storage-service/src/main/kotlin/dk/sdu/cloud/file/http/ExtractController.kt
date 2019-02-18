package dk.sdu.cloud.file.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.SingleFileAudit
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.api.parent
import dk.sdu.cloud.file.services.BackgroundScope
import dk.sdu.cloud.file.services.BulkUploader
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.FSUserContext
import dk.sdu.cloud.file.services.FileLookupService
import dk.sdu.cloud.file.services.withContext
import dk.sdu.cloud.file.util.tryWithFS
import dk.sdu.cloud.service.Controller
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.launch

class ExtractController<Ctx : FSUserContext>(
    private val serviceCloud: AuthenticatedClient,
    private val coreFs: CoreFileSystemService<Ctx>,
    private val fileLookupService: FileLookupService<Ctx>,
    private val commandRunnerFactory: FSCommandRunnerFactory<Ctx>
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(FileDescriptions.extract) {
            audit(SingleFileAudit(null, request))
            val user = ctx.securityPrincipal.username
            tryWithFS(commandRunnerFactory, user) { ctx ->
                val fileID = fileLookupService.stat(ctx, request.path).fileId
                audit(SingleFileAudit(fileID, request))
            }

            val extractor = when {
                request.path.endsWith(".tar.gz") -> BulkUploader.fromFormat("tgz", commandRunnerFactory.type)
                request.path.endsWith(".zip") -> BulkUploader.fromFormat("zip", commandRunnerFactory.type)
                else -> null
            } ?: return@implement error(
                CommonErrorMessage("Unsupported format"),
                HttpStatusCode.BadRequest
            )

            BackgroundScope.launch {
                commandRunnerFactory.withContext(user) { readContext ->
                    coreFs.read(readContext, request.path) {
                        val fileInputStream = this

                        extractor.upload(
                            serviceCloud,
                            coreFs,
                            { commandRunnerFactory(user) },
                            request.path.parent(),
                            WriteConflictPolicy.RENAME,
                            fileInputStream
                        )
                    }
                }
            }

            ok(Unit)
        }
    }
}
