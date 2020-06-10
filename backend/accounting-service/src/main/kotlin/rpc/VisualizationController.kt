package dk.sdu.cloud.accounting.rpc

import dk.sdu.cloud.accounting.api.Visualization
import dk.sdu.cloud.accounting.api.WalletOwnerType
import dk.sdu.cloud.accounting.services.VisualizationService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.toActor

class VisualizationController(
    private val db: DBContext,
    private val visualization: VisualizationService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Visualization.usage) {
            val project = ctx.project
            val accountId = if (project == null) {
                ctx.securityPrincipal.username
            } else {
                project
            }

            val accountType = if (project == null) {
                WalletOwnerType.USER
            } else {
                WalletOwnerType.PROJECT
            }

            ok(visualization.usage(db, ctx.securityPrincipal.toActor(), accountId, accountType, request))
        }

        return@with
    }
}
