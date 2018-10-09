package dk.sdu.cloud.accounting.compute.http

import dk.sdu.cloud.accounting.api.Chart
import dk.sdu.cloud.accounting.api.ChartResponse
import dk.sdu.cloud.accounting.api.CurrentUsageResponse
import dk.sdu.cloud.accounting.api.SimpleDataPoint
import dk.sdu.cloud.accounting.compute.api.AccountingJobCompletedEvent
import dk.sdu.cloud.accounting.compute.api.ComputeAccountingJobsDescriptions
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.service.paginate
import io.ktor.routing.Route

@Suppress("MagicNumber")
class JobsStartedController : Controller {
    override val baseContext = ComputeAccountingJobsDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(ComputeAccountingJobsDescriptions.listEvents) { req ->
            logEntry(log, req)
            ok(
                (0 until 10).map {
                    AccountingJobCompletedEvent(
                        NameAndVersion("abc", "1.0.0"),
                        it,
                        SimpleDuration(1, 0, 0),
                        "user",
                        "job-$it",
                        1000L * it
                    )
                }.paginate(req.normalize())
            )
        }

        implement(ComputeAccountingJobsDescriptions.chart) { req ->
            logEntry(log, req)

            ok(
                ChartResponse(
                    chart = Chart(
                        xAxisLabel = "Time",
                        yAxisLabel = "Total usage",
                        data = (0 until 10).map {
                            SimpleDataPoint(
                                x = 1000L * it,
                                y = 1000L * 60 * 60 * it,
                                label = SimpleDuration(it, 0, 0).toString()
                            )
                        }
                    ),

                    quota = null
                )
            )
        }

        implement(ComputeAccountingJobsDescriptions.currentUsage) { req ->
            logEntry(log, req)

            ok(CurrentUsageResponse(1000L * 60 * 60, null))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
