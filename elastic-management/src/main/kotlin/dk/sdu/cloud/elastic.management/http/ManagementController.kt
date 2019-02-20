package dk.sdu.cloud.elastic.management.http

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.elastic.management.api.HelloDescriptions
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable

class ManagementController : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(HelloDescriptions.hello) {
            // Implement call here
            ok(Unit)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
