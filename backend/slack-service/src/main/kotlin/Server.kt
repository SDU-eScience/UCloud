package dk.sdu.cloud.slack 

import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.slack.rpc.SlackController
import dk.sdu.cloud.slack.services.SlackNotifier
import dk.sdu.cloud.slack.services.SlackService

class Server(
    override val micro: Micro,
    private val configuration: Configuration
) : CommonServer {
    override val log = logger()
    
    override fun start() {
        val slackService = SlackService(listOf(SlackNotifier(configuration.notifiers.slack?.hook!!)))

        with(micro.server) {
            configureControllers(
                SlackController(slackService)
            )
        }
        
        startServices()
    }
}
