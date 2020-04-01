package dk.sdu.cloud.activity

import dk.sdu.cloud.activity.http.ActivityController
import dk.sdu.cloud.activity.services.ActivityEventElasticDao
import dk.sdu.cloud.activity.services.ActivityService
import dk.sdu.cloud.activity.services.FileLookupService
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.elasticHighLevelClient
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices

class Server(
    override val micro: Micro
) : CommonServer {
    override val log = logger()

    override fun start() {
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val elasticClient = micro.elasticHighLevelClient

        log.info("Creating core services")
        val activityElasticDao = ActivityEventElasticDao(elasticClient)
        val fileLookupService = FileLookupService(client)
        val activityService = ActivityService(activityElasticDao, fileLookupService, client)
        log.info("Core services constructed")

        with(micro.server) {
            configureControllers(
                ActivityController(activityService)
            )
        }

        startServices()
    }
}
