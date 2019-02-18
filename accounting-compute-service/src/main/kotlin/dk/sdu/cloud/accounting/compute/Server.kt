package dk.sdu.cloud.accounting.compute

import dk.sdu.cloud.accounting.compute.http.ComputeAccountingController
import dk.sdu.cloud.accounting.compute.http.ComputeTimeController
import dk.sdu.cloud.accounting.compute.processor.JobCompletedProcessor
import dk.sdu.cloud.accounting.compute.services.CompletedJobsHibernateDao
import dk.sdu.cloud.accounting.compute.services.CompletedJobsService
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.kafka
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.installShutdownHandler
import dk.sdu.cloud.service.startServices

class Server(
    override val micro: Micro
) : CommonServer {
    override val log = logger()

    private val processors = ArrayList<EventConsumer<*>>()

    override fun start() {
        val db = micro.hibernateDatabase
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val kafka = micro.kafka

        // Services
        val completedJobsDao = CompletedJobsHibernateDao()
        val completedJobsService = CompletedJobsService(db, completedJobsDao, client)

        // Processors
        JobCompletedProcessor(kafka, completedJobsService, client).init().let { batch ->
            batch.forEach { it.installShutdownHandler(this) }
            processors.addAll(batch)
        }

        // HTTP
        with(micro.server) {
            configureControllers(
                ComputeTimeController(completedJobsService),
                ComputeAccountingController(completedJobsService)
            )
        }

        log.info("Server is ready!")

        startServices()
    }

    override fun stop() {
        super.stop()
        processors.forEach { it.close() }
    }
}
