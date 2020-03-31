package dk.sdu.cloud.accounting.compute.http

import dk.sdu.cloud.accounting.api.ChartDataTypes
import dk.sdu.cloud.accounting.api.UsageResponse
import dk.sdu.cloud.accounting.compute.api.ComputeAccountingTimeDescriptions
import dk.sdu.cloud.accounting.compute.services.CompletedJobsService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable

class ComputeTimeController(
    private val completedJobsService: CompletedJobsService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(ComputeAccountingTimeDescriptions.usage) {
            ok(
                UsageResponse(
                    usage = completedJobsService.computeUsage(ctx.securityPrincipal.username),
                    quota = null,
                    dataType = ChartDataTypes.DURATION,
                    title = "Compute Time Used"
                )
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
