package dk.sdu.cloud.mail.rpc

import dk.sdu.cloud.Role
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.mail.api.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.mail.services.MailService
import dk.sdu.cloud.mail.services.SettingsService
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*

class MailController(
    private val mailService: MailService,
    private val settingsService: SettingsService,
    private val db: DBContext
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(MailDescriptions.sendSupport) {
            ok(mailService.sendSupportTicket(request.fromEmail, request.subject, request.message))
        }

        implement(MailDescriptions.sendToUser) {
            request.items.forEach {
                val allowedToSend = mailService.allowedToSend(it.receiver)
                if (allowedToSend) {
                    mailService.send(ctx.securityPrincipal, it.receiver, it.mail, it.mandatory, testMail = it.testMail)
                }
            }
            ok(Unit)
        }

        implement(MailDescriptions.retrieveEmailSettings) {
            val user = if (ctx.securityPrincipal.role == Role.SERVICE) {
                request.username
            } else {
                ctx.securityPrincipal.username
            }
            if (user == null) {
                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Missing username")
            }
            ok(RetrieveEmailSettingsResponse(
                settingsService.getEmailSettings(user)
            ))
        }

        implement(MailDescriptions.toggleEmailSettings) {
            db.withSession { session ->
                request.items.forEach { request ->
                    val user = if (ctx.securityPrincipal.role == Role.SERVICE) {
                        request.username
                    } else {
                        ctx.securityPrincipal.username
                    }
                    if (user == null) {
                        throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Missing username")
                    }
                    settingsService.updateEmailSettings(session, request.settings, user)
                }
            }
            ok(Unit)
        }

        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}
