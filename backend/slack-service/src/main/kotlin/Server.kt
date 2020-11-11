package dk.sdu.cloud.slack 

import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.slack.rpc.SlackController
import dk.sdu.cloud.slack.services.AlertSlackService
import dk.sdu.cloud.slack.services.SlackNotifier
import dk.sdu.cloud.slack.services.SupportSlackService

class Server(
    override val micro: Micro,
    private val alertConfiguration: Configuration,
    private val supportConfiguration: Configuration
) : CommonServer {
    override val log = logger()
    
    override fun start() {
        val alertSlackService = AlertSlackService(listOf(SlackNotifier(alertConfiguration.notifiers.slack?.hook!!)))
        val supportSlackService = SupportSlackService(listOf(SlackNotifier(supportConfiguration.notifiers.slack?.hook!!)))
        with(micro.server) {
            configureControllers(
                SlackController(alertSlackService, supportSlackService)
            )
        }
        
        startServices()
    }
}
