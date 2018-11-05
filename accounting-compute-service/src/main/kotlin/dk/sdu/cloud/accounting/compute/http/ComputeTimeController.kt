package dk.sdu.cloud.accounting.compute.http

import dk.sdu.cloud.accounting.api.ChartResponse
import dk.sdu.cloud.accounting.api.ChartingHelpers
import dk.sdu.cloud.accounting.api.CurrentUsageResponse
import dk.sdu.cloud.accounting.compute.api.ComputeAccountingTimeDescriptions
import dk.sdu.cloud.accounting.compute.services.CompletedJobsService
import dk.sdu.cloud.accounting.compute.services.toMillis
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.service.securityPrincipal
import io.ktor.routing.Route

class ComputeTimeController<DBSession>(
    private val completedJobsService: CompletedJobsService<DBSession>
) : Controller {
    override val baseContext: String = ComputeAccountingTimeDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(ComputeAccountingTimeDescriptions.listEvents) { req ->
            logEntry(log, req)

            ok(completedJobsService.listEvents(req.normalize(), req, call.securityPrincipal.username))
        }

        implement(ComputeAccountingTimeDescriptions.chart) { req ->
            logEntry(log, req)

            val events = completedJobsService.listAllEvents(req, call.securityPrincipal.username)

            ok(
                ChartResponse(
                    chart = ChartingHelpers.basicChartFromEvents(events, yAxisLabel = "Compute time used") {
                        it.totalDuration.toMillis()
                    },
                    quota = null
                )
            )
        }

        implement(ComputeAccountingTimeDescriptions.currentUsage) { req ->
            logEntry(log, req)

            ok(
                CurrentUsageResponse(
                    usage = completedJobsService.computeUsage(req, call.securityPrincipal.username),
                    quota = null
                )
            )
        }
    }

    // Could be used in the Page library
    private inline fun <E> retrieveAllPages(pageRetriever: (NormalizedPaginationRequest) -> Page<E>): List<E> {
        val result = ArrayList<E>()
        var currentPage = NormalizedPaginationRequest(100, 0)
        while (true) {
            val page = pageRetriever(currentPage)
            result.addAll(page.items)

            if (page.pagesInTotal > currentPage.page) {
                currentPage = NormalizedPaginationRequest(currentPage.itemsPerPage, currentPage.page + 1)
            } else {
                break
            }
        }
        return result
    }

    companion object : Loggable {
        override val log = logger()
    }
}
