package dk.sdu.cloud.contact.book

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.contact.book.rpc.*
import dk.sdu.cloud.contact.book.services.ContactBookDAO
import dk.sdu.cloud.contact.book.services.ContactBookElasticDAO
import dk.sdu.cloud.contact.book.services.ContactBookService

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {

        val contactsDAO = ContactBookElasticDAO(micro.elasticHighLevelClient)

        val contactBookService = ContactBookService(contactsDAO)

        /*with(micro.server) {
            configureControllers(
                ContactBookController(contactBookService)
            )
        }

        startServices()*/
        contactsDAO.createIndex()
        println("DONE")
    }

    override fun stop() {
        super.stop()
    }
}
