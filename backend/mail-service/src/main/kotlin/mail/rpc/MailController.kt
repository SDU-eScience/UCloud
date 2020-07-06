package dk.sdu.cloud.mail.rpc

import dk.sdu.cloud.mail.api.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.mail.services.MailService
import dk.sdu.cloud.service.Loggable

class MailController(private val mailService: MailService) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(MailDescriptions.send) {
            ok(mailService.send(ctx.securityPrincipal, request.userId, request.subject, request.message, request.mandatory!!))
        }

        implement(MailDescriptions.sendBulk) {
            request.messages.forEach {
                mailService.send(ctx.securityPrincipal, it.userId, it.subject, it.message, it.mandatory!!)
            }
            ok(Unit)
        }

        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}
