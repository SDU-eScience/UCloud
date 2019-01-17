package dk.sdu.cloud.file.trash.http

import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.bearerAuth
import dk.sdu.cloud.file.trash.api.FileTrashDescriptions
import dk.sdu.cloud.file.trash.api.TrashResponse
import dk.sdu.cloud.file.trash.services.TrashService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.bearer
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.optionallyCausedBy
import dk.sdu.cloud.service.safeJobId
import dk.sdu.cloud.service.securityPrincipal
import io.ktor.application.ApplicationCall
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route

class FileTrashController(
    private val serviceCloud: AuthenticatedCloud,
    private val trashService: TrashService
) : Controller {
    override val baseContext = FileTrashDescriptions.baseContext
    private val cloudContext = serviceCloud.parent

    override fun configure(routing: Route): Unit = with(routing) {
        implement(FileTrashDescriptions.trash) { req ->
            ok(
                TrashResponse(
                    trashService.moveFilesToTrash(
                        req.files,
                        call.securityPrincipal.username,
                        call.userCloud()
                    )
                )
            )
        }

        implement(FileTrashDescriptions.clear) {
            ok(
                trashService.emptyTrash(
                    call.securityPrincipal.username,
                    call.userCloud()
                ),
                HttpStatusCode.Accepted
            )
        }
    }

    private fun ApplicationCall.userCloud(): AuthenticatedCloud {
        val token = request.bearer ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
        return cloudContext.bearerAuth(token).optionallyCausedBy(request.safeJobId)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
