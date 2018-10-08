package dk.sdu.cloud.accounting.compute.http

import dk.sdu.cloud.accounting.api.Chart
import dk.sdu.cloud.accounting.api.ChartResponse
import dk.sdu.cloud.accounting.api.CurrentUsageResponse
import dk.sdu.cloud.accounting.api.SimpleDataPoint
import dk.sdu.cloud.accounting.compute.api.ComputeAccountingJobsDescriptions
import dk.sdu.cloud.app.api.JobCompletedEvent
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.service.*
import io.ktor.routing.Route

class JobsStartedController : Controller {
    override val baseContext = ComputeAccountingJobsDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(ComputeAccountingJobsDescriptions.listEvents) { req ->
            logEntry(log, req)
            // TODO All events should have a timestamp
            ok(
                (0 until 10).map {
                    JobCompletedEvent(
                        "job-$it",
                        "you",
                        SimpleDuration(1, 0, 0),
                        NameAndVersion("abc", "1.0.0"),
                        it % 3 == 0
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