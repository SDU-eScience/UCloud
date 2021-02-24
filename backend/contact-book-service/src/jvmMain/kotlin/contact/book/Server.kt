package dk.sdu.cloud.contact.book

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.contact.book.rpc.*
import dk.sdu.cloud.contact.book.services.ContactBookElasticDao
import dk.sdu.cloud.contact.book.services.ContactBookService
import java.lang.Exception
import kotlin.system.exitProcess

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {

        val contactsDAO = ContactBookElasticDao(micro.elasticHighLevelClient)

        val contactBookService = ContactBookService(contactsDAO)

        if (micro.commandLineArguments.contains("--createIndex")) {
            try {
                contactsDAO.createIndex()
                log.info("Contactbook index has been created")
                exitProcess(0)
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                exitProcess(1)
            }
        }

        with(micro.server) {
            configureControllers(
                ContactBookController(contactBookService)
            )
        }

        startServices()

    }

    override fun stop() {
        super.stop()
    }
}
