package dk.sdu.cloud.accounting.compute

import dk.sdu.cloud.accounting.compute.rpc.AccountingController
import dk.sdu.cloud.accounting.compute.services.BalanceService
import dk.sdu.cloud.accounting.compute.services.ProjectCache
import dk.sdu.cloud.accounting.compute.services.VisualizationService
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.databaseConfig
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.startServices

class Server(
    override val micro: Micro
) : CommonServer {
    override val log = logger()

    override fun start() {
        val db = AsyncDBSessionFactory(micro.databaseConfig)
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val projectCache = ProjectCache(client)
        val balanceService = BalanceService(projectCache)
        val visualizationService = VisualizationService(balanceService, projectCache)

        with(micro.server) {
            configureControllers(
                AccountingController(db, balanceService, visualizationService)
            )
        }

        startServices()
    }
}
