package dk.sdu.cloud.indexing.http

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.indexing.api.Subscriptions
import dk.sdu.cloud.indexing.services.SubscriptionService
import dk.sdu.cloud.service.Controller

class SubscriptionController(
    private val subscriptionService: SubscriptionService<*>
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(Subscriptions.addSubscription) {
            subscriptionService.addSubscription(ctx.securityPrincipal, request.fileIds)
            ok(Unit)
        }

        implement(Subscriptions.removeSubscription) {
            subscriptionService.removeSubscription(ctx.securityPrincipal, request.fileIds)
            ok(Unit)
        }
    }
}
