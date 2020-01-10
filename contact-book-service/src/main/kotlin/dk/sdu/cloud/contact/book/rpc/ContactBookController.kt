package dk.sdu.cloud.contact.book.rpc

import dk.sdu.cloud.contact.book.api.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.contact.book.services.ContactBookService
import dk.sdu.cloud.service.Loggable

class ContactBookController(
    private val contactBookService: ContactBookService
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(ContactBookDescriptions.insert) {
            ok(Unit)
        }

        implement(ContactBookDescriptions.delete) {
            ok(Unit)
        }

        implement(ContactBookDescriptions.listAllContactsForUser) {
            ok(QueryContactsResponse(emptyList()))
        }

        implement(ContactBookDescriptions.queryUserContacts) {
            ok(QueryContactsResponse(emptyList()))
        }

        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}
