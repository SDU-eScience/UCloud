package dk.sdu.cloud.activity

import dk.sdu.cloud.activity.http.ActivityController
import dk.sdu.cloud.activity.processor.StorageAuditProcessor
import dk.sdu.cloud.activity.processor.StorageEventProcessor
import dk.sdu.cloud.activity.services.ActivityService
import dk.sdu.cloud.activity.services.FileLookupService
import dk.sdu.cloud.activity.services.HibernateActivityEventDao
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

    private val allProcessors = ArrayList<EventConsumer<*>>()

    private fun addProcessors(processors: List<EventConsumer<*>>) {
        processors.forEach { it.installShutdownHandler(this) }
        allProcessors.addAll(processors)
    }

    override fun start() {
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val kafka = micro.kafka
        val db = micro.hibernateDatabase

        log.info("Creating core services")
        val activityEventDao = HibernateActivityEventDao()
        val fileLookupService = FileLookupService(client)
        val activityService = ActivityService(db, activityEventDao, fileLookupService)
        log.info("Core services constructed")

        log.info("Creating stream processors")
        addProcessors(StorageAuditProcessor(kafka, activityService).init())
        addProcessors(StorageEventProcessor(kafka, activityService).init())
        log.info("Stream processors constructed")

        with(micro.server) {
            configureControllers(
                ActivityController(activityService)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
        allProcessors.forEach { it.close() }
    }
}
