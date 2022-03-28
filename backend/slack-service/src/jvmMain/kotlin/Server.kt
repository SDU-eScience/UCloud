package dk.sdu.cloud.slack 

import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.slack.services.*
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.slack.rpc.SlackController

class Server(
    override val micro: Micro,
    private val alertConfiguration: Configuration,
    private val supportConfiguration: Configuration
) : CommonServer {
    override val log = logger()
    
    override fun start() {
        val db = AsyncDBSessionFactory(micro)
        val alertSlackService = if (alertConfiguration.notifiers.slack != null) {
            AlertSlackService(listOf(SlackNotifier(alertConfiguration.notifiers.slack.hook, db)))
        } else {
            log.warn("No alert channel given")
            AlertSlackService(emptyList())
        }
        val supportSlackService = if (supportConfiguration.notifiers.slack != null) {
            SupportSlackService(listOf(SlackNotifier(supportConfiguration.notifiers.slack.hook, db)))
        } else {
            log.warn("No support channel given")
            SupportSlackService(emptyList())
        }

        with(micro.server) {
            configureControllers(
                SlackController(alertSlackService, supportSlackService)
            )
        }
        
        startServices()
    }
}
