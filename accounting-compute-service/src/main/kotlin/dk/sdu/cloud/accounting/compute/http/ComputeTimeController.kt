package dk.sdu.cloud.accounting.compute.http

import dk.sdu.cloud.accounting.api.ChartDataTypes
import dk.sdu.cloud.accounting.api.ChartResponse
import dk.sdu.cloud.accounting.api.ChartingHelpers
import dk.sdu.cloud.accounting.api.UsageResponse
import dk.sdu.cloud.accounting.compute.api.ComputeAccountingTimeDescriptions
import dk.sdu.cloud.accounting.compute.services.CompletedJobsService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable

class ComputeTimeController<DBSession>(
    private val completedJobsService: CompletedJobsService<DBSession>
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(ComputeAccountingTimeDescriptions.listEvents) {
            ok(
                completedJobsService.listEvents(
                    request.normalize(),
                    request,
                    ctx.securityPrincipal.username,
                    ctx.securityPrincipal.role
                )
            )
        }

        implement(ComputeAccountingTimeDescriptions.chart) {
            val events =
                completedJobsService.listAllEvents(request, ctx.securityPrincipal.username, ctx.securityPrincipal.role)

            ok(
                ChartResponse(
                    chart = ChartingHelpers.sumChartFromEvents(
                        events,
                        dataType = ChartDataTypes.DURATION,
                        dataTitle = "Compute Time Used",
                        dataSelector = { it.totalDuration.toMillis() }
                    ),
                    quota = null
                )
            )
        }

        implement(ComputeAccountingTimeDescriptions.usage) {
            ok(
                UsageResponse(
                    usage = completedJobsService.computeUsage(
                        request,
                        ctx.securityPrincipal.username,
                        ctx.securityPrincipal.role
                    ),
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
