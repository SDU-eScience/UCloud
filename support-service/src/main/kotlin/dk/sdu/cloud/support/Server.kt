package dk.sdu.cloud.support

import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.support.http.SupportController
import dk.sdu.cloud.support.services.SlackNotifier
import dk.sdu.cloud.support.services.TicketNotifier
import dk.sdu.cloud.support.services.TicketService

class Server(
    override val micro: Micro,
    private val config: Configuration
) : CommonServer {
    private val eventConsumers = ArrayList<EventConsumer<*>>()

    override val log = logger()

    override fun start() {
        val ticketNotifiers = ArrayList<TicketNotifier>().apply {
            config.notifiers.slack?.let {
                add(SlackNotifier(it.hook))
            }
        }.toList()

        val ticketService = TicketService(ticketNotifiers)
        log.info("service instansiated!")
        with(micro.server) {
            configureControllers(
                SupportController(ticketService)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
        eventConsumers.forEach { it.close() }
    }
}
