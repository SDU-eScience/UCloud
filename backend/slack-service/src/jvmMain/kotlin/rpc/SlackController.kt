package dk.sdu.cloud.slack.rpc

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.slack.api.SlackDescriptions
import dk.sdu.cloud.slack.services.AlertSlackService
import dk.sdu.cloud.slack.services.SupportSlackService

class SlackController(
    private val alertSlackService: AlertSlackService,
    private val supportSlackService: SupportSlackService
): Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(SlackDescriptions.sendAlert) {
            ok(alertSlackService.createAlert(request))
        }

        implement(SlackDescriptions.sendSupport) {
            ok(supportSlackService.createTicket(request))
        }
    }
}
