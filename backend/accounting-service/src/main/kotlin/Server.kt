package dk.sdu.cloud.accounting

import dk.sdu.cloud.accounting.rpc.AccountingController
import dk.sdu.cloud.accounting.rpc.VisualizationController
import dk.sdu.cloud.accounting.services.*
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.commandLineArguments
import dk.sdu.cloud.micro.databaseConfig
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.startServices
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

class Server(
    override val micro: Micro,
    val config: Configuration
) : CommonServer {
    override val log = logger()

    override fun start() {
        val db = AsyncDBSessionFactory(micro.databaseConfig)
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val projectCache = ProjectCache(client)
        val verificationService = VerificationService(client)
        val balanceService = BalanceService(projectCache, verificationService, client)
        val visualizationService = VisualizationService(balanceService, projectCache)
        val productService = ProductService()

        if (micro.commandLineArguments.contains("--low-funds-check")) {
            val jobs = CronJobs(db, client, config)
            try {
                runBlocking {
                    jobs.notifyLowFundsWallets()
                }
                exitProcess(0)
            } catch (ex: Throwable) {
                log.warn(ex.stackTraceToString())
                exitProcess(1)
            }
        }

        with(micro.server) {
            configureControllers(
                AccountingController(db, balanceService),
                ProductController(db, productService),
                VisualizationController(db, visualizationService)
            )
        }

        startServices()
    }
}
