package dk.sdu.cloud.accounting.compute.http

import dk.sdu.cloud.accounting.api.ChartDataTypes
import dk.sdu.cloud.accounting.api.ChartResponse
import dk.sdu.cloud.accounting.api.ChartingHelpers
import dk.sdu.cloud.accounting.api.UsageResponse
import dk.sdu.cloud.accounting.compute.api.ComputeAccountingTimeDescriptions
import dk.sdu.cloud.accounting.compute.services.CompletedJobsService
import dk.sdu.cloud.accounting.compute.services.toMillis
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.securityPrincipal
import io.ktor.routing.Route

class ComputeTimeController<DBSession>(
    private val completedJobsService: CompletedJobsService<DBSession>
) : Controller {
    override val baseContext: String = ComputeAccountingTimeDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(ComputeAccountingTimeDescriptions.listEvents) { req ->
            ok(completedJobsService.listEvents(req.normalize(), req, call.securityPrincipal.username))
        }

        implement(ComputeAccountingTimeDescriptions.chart) { req ->
            val events = completedJobsService.listAllEvents(req, call.securityPrincipal.username)

            ok(
                ChartResponse(
                    chart = ChartingHelpers.absoluteChartFromEvents(
                        events,
                        dataType = ChartDataTypes.DURATION,
                        dataTitle = "Compute Time Used",
                        dataSelector = { it.totalDuration.toMillis() }
                    ),
                    quota = null
                )
            )
        }

        implement(ComputeAccountingTimeDescriptions.usage) { req ->
            ok(
                UsageResponse(
                    usage = completedJobsService.computeUsage(req, call.securityPrincipal.username),
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
