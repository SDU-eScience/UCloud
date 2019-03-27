package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.app.kubernetes.api.AppKubernetesDescriptions
import dk.sdu.cloud.app.kubernetes.services.PodService
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode

class AppKubernetesController(
    private val podService: PodService
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(AppKubernetesDescriptions.cleanup) {}
        implement(AppKubernetesDescriptions.follow) {}

        implement(AppKubernetesDescriptions.jobVerified) {
            podService.create(request.id)
            ok(Unit)
        }

        implement(AppKubernetesDescriptions.submitFile) {
            request.asIngoing().receiveBlocks { block ->
                val file = block.job.files.find { it.id == block.parameterName } ?: throw RPCException(
                    "Bad request. File with id '${block.parameterName}' does not exist!",
                    HttpStatusCode.BadRequest
                )
                val relativePath =
                    if (file.destinationPath.startsWith("/")) ".${file.destinationPath}" else file.destinationPath

                podService.submitFile(
                    /*
                    block.job,
                    block.parameterName,
                    block.fileData
                    */
                    block.job.id,
                    relativePath,
                    block.fileData
                )
            }
            ok(Unit)
        }

        implement(AppKubernetesDescriptions.jobPrepared) {
//            podService.startContainer(request)
            ok(Unit)
        }
        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}
