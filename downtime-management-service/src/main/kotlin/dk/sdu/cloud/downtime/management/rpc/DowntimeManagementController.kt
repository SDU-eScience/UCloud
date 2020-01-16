package dk.sdu.cloud.downtime.management.rpc

import dk.sdu.cloud.downtime.management.api.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.downtime.management.services.DowntimeManagementService
import dk.sdu.cloud.service.Loggable

class DowntimeManagementController<DBSession>(
    private val downtimeManagementService: DowntimeManagementService<DBSession>
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {

        implement(DowntimeManagementDescriptions.listAll) {
            ok(downtimeManagementService.listAll(ctx.securityPrincipal, request.normalize()))
        }

        implement(DowntimeManagementDescriptions.listPending) {
            ok(downtimeManagementService.listPending(request.normalize()))
        }

        implement(DowntimeManagementDescriptions.listUpcoming) {
            ok(downtimeManagementService.listPending(request.normalize()))
        }

        implement(DowntimeManagementDescriptions.add) {
            ok(downtimeManagementService.add(ctx.securityPrincipal, request))
        }

        implement(DowntimeManagementDescriptions.remove) {
            ok(downtimeManagementService.remove(ctx.securityPrincipal, request.id))
        }

        implement(DowntimeManagementDescriptions.removeExpired) {
            ok(downtimeManagementService.removeExpired(ctx.securityPrincipal))
        }

        implement(DowntimeManagementDescriptions.getById) {
            ok(downtimeManagementService.getById(request.id))
        }

        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}