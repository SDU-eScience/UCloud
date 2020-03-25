package dk.sdu.cloud.messages.rpc

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.messages.api.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Loggable

class MessagesController : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(MessagesDescriptions.postMessage) {
            val user = ctx.securityPrincipal.username
            log.info("We automatically log calls and user (but this is how you do it $user")

            ok(Unit)
        }
        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}