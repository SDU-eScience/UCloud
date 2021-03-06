package dk.sdu.cloud.provider

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.backgroundScope
import dk.sdu.cloud.micro.databaseConfig
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.provider.rpc.Docs
import dk.sdu.cloud.provider.rpc.ProviderController
import dk.sdu.cloud.provider.services.ProjectCache
import dk.sdu.cloud.provider.services.ProviderDao
import dk.sdu.cloud.provider.services.ProviderService
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.startServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val db = AsyncDBSessionFactory(micro.databaseConfig)
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val projectCache = ProjectCache(serviceClient)
        val providerDao = ProviderDao(projectCache)
        val providerService = ProviderService(db, providerDao, serviceClient)

        micro.backgroundScope.launch {
            while (isActive) {
                try {
                    providerService.claimTheUnclaimed()
                    delay(60_000)
                } catch (ex: Throwable) {
                    log.error(ex.stackTraceToString())
                    exitProcess(1)
                }
            }
        }

        with(micro.server) {
            configureControllers(
                Docs(),
                ProviderController(providerService),
            )
        }

        startServices()
    }
}
