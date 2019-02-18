package dk.sdu.cloud.project

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.kafka.forStream
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.kafka
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.project.api.ProjectEvents
import dk.sdu.cloud.project.http.ProjectController
import dk.sdu.cloud.project.services.ProjectHibernateDao
import dk.sdu.cloud.project.services.ProjectService
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.installShutdownHandler
import dk.sdu.cloud.service.startServices

class Server(
    override val micro: Micro
) : CommonServer {
    private val eventConsumers = ArrayList<EventConsumer<*>>()

    override val log = logger()

    private fun addConsumers(consumers: List<EventConsumer<*>>) {
        consumers.forEach { it.installShutdownHandler(this) }
        eventConsumers.addAll(consumers)
    }

    override fun start() {
        val kafka = micro.kafka
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)

        val projectDao = ProjectHibernateDao()
        val projectService = ProjectService(
            micro.hibernateDatabase,
            projectDao,
            kafka.producer.forStream(ProjectEvents.events),
            client
        )

        // Initialize consumers here:
        // addConsumers(...)

        // Initialize server
        with(micro.server) {
            configureControllers(
                ProjectController(projectService)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
        eventConsumers.forEach { it.close() }
    }
}
