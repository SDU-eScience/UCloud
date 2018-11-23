package dk.sdu.cloud.support

import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.HttpServerProvider
import dk.sdu.cloud.service.KafkaServices
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.support.http.SupportController
import dk.sdu.cloud.support.services.SlackNotifier
import dk.sdu.cloud.support.services.TicketNotifier
import dk.sdu.cloud.support.services.TicketService
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams

class Server(
    override val kafka: KafkaServices,
    private val ktor: HttpServerProvider,
    private val micro: Micro,
    private val config: Configuration
) : CommonServer {
    override lateinit var httpServer: ApplicationEngine
    override val kStreams: KafkaStreams? = null
    private val eventConsumers = ArrayList<EventConsumer<*>>()

    override val log = logger()

    override fun start() {
        val ticketNotifiers = ArrayList<TicketNotifier>().apply {
            config.notifiers.slack?.let {
                add(SlackNotifier(it.hook))
            }
        }.toList()

        val ticketService = TicketService(ticketNotifiers)

        httpServer = ktor {
            installDefaultFeatures(micro)

            routing {
                configureControllers(
                    SupportController(ticketService)
                )
            }
        }

        startServices()
    }

    override fun stop() {
        super.stop()
        eventConsumers.forEach { it.close() }
    }
}
