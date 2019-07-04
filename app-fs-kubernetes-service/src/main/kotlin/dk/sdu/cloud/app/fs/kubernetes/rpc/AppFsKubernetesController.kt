package dk.sdu.cloud.app.fs.kubernetes.rpc

import dk.sdu.cloud.app.fs.api.FileSystemCalls
import dk.sdu.cloud.app.fs.kubernetes.api.KubernetesFileSystemCalls
import dk.sdu.cloud.app.fs.kubernetes.services.FileSystemService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable

class AppFsKubernetesController(
    private val fsService: FileSystemService
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(KubernetesFileSystemCalls.create) {
            fsService.create(request.internalId, request.ownerUid)
            ok(FileSystemCalls.Create.Response())
        }

        implement(KubernetesFileSystemCalls.delete) {
            fsService.delete(request.id)
            ok(FileSystemCalls.Delete.Response())
        }

        implement(KubernetesFileSystemCalls.view) {
            ok(FileSystemCalls.View.Response(fsService.calculateSize(request.id)))
        }

        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}
