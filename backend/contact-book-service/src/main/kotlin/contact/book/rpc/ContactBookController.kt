package dk.sdu.cloud.contact.book.rpc

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.contact.book.api.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.contact.book.services.ContactBookService
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode
import java.lang.IllegalArgumentException

class ContactBookController(
    private val contactBookService: ContactBookService
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(ContactBookDescriptions.insert) {
            contactBookService.insertContact(
                request.fromUser,
                request.toUser,
                request.serviceOrigin
            )
            ok(Unit)
        }

        implement(ContactBookDescriptions.delete) {
            contactBookService.deleteContact(
                request.fromUser,
                request.toUser,
                request.serviceOrigin
            )
            ok(Unit)
        }

        implement(ContactBookDescriptions.listAllContactsForUser) {
            ok(QueryContactsResponse(
                contactBookService.listAllContactsForUser(
                    ctx.securityPrincipal.username,
                    request.serviceOrigin
                )
            ))
        }

        implement(ContactBookDescriptions.queryUserContacts) {
            ok(QueryContactsResponse(
                contactBookService.queryUserContacts(
                    ctx.securityPrincipal.username,
                    request.query,
                    request.serviceOrigin
                )
            ))
        }

        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}
