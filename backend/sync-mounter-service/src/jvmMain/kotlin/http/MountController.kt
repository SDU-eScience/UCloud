package dk.sdu.cloud.sync.mounter.http

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.sync.mounter.api.Mounts
import dk.sdu.cloud.sync.mounter.services.MountService

class MountController(
    private val mountService: MountService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Mounts.mount) {
            ok(mountService.mount())
        }

        implement(Mounts.unmount) {
            ok(mountService.unmount())
        }

        implement(Mounts.state) {
            ok(mountService.state())
        }
    }
}
