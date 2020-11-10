package dk.sdu.cloud.slack.rpc

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.slack.api.SlackDescriptions
import dk.sdu.cloud.slack.services.SlackService

class SlackController(
    private val slackService: SlackService
): Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(SlackDescriptions.sendMessage) {
            ok(slackService.createAlert(request))
        }
    }
}
