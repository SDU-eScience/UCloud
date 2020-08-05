package dk.sdu.cloud.app.kubernetes.watcher.rpc

import dk.sdu.cloud.app.kubernetes.watcher.api.AppKubernetesWatcher
import dk.sdu.cloud.app.kubernetes.watcher.services.JobWatcher
import dk.sdu.cloud.app.kubernetes.watcher.services.ReloadableKubernetesClient
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller

class ReloadController(
    private val client: ReloadableKubernetesClient,
    private val watcher: JobWatcher
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        if (client.allowReloading) {
            implement(AppKubernetesWatcher.reload) {
                client.reload()
                watcher.startWatch()
                ok(Unit)
            }
        }
        return@with
    }
}
