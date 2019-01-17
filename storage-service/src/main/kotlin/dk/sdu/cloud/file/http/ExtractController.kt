package dk.sdu.cloud.file.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.AuthenticatedCloud
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
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.securityPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import kotlinx.coroutines.launch

class ExtractController<Ctx : FSUserContext>(
    private val serviceCloud: AuthenticatedCloud,
    private val coreFs: CoreFileSystemService<Ctx>,
    private val fileLookupService: FileLookupService<Ctx>,
    private val commandRunnerFactory: FSCommandRunnerFactory<Ctx>
) : Controller {
    override val baseContext: String = FileDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(FileDescriptions.extract) { req ->
            audit(SingleFileAudit(null, req))
            val user = call.securityPrincipal.username
            tryWithFS(commandRunnerFactory, user) { ctx ->
                val fileID = fileLookupService.stat(ctx, req.path).fileId
                audit(SingleFileAudit(fileID, req))
            }

            val extractor = when {
                req.path.endsWith(".tar.gz") -> BulkUploader.fromFormat("tgz", commandRunnerFactory.type)
                req.path.endsWith(".zip") -> BulkUploader.fromFormat("zip", commandRunnerFactory.type)
                else -> null
            } ?: return@implement error(
                CommonErrorMessage("Unsupported format"),
                HttpStatusCode.BadRequest
            )

            BackgroundScope.launch {
                commandRunnerFactory.withContext(user) { readContext ->
                    coreFs.read(readContext, req.path) {
                        val fileInputStream = this

                        extractor.upload(
                            serviceCloud,
                            coreFs,
                            { commandRunnerFactory(user) },
                            req.path.parent(),
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
