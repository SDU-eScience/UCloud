package dk.sdu.cloud.accounting.compute.http

import dk.sdu.cloud.accounting.api.AccountingResource
import dk.sdu.cloud.accounting.api.BuildReportResponse
import dk.sdu.cloud.accounting.api.ListResourceResponse
import dk.sdu.cloud.accounting.compute.api.ComputeAccountingDescriptions
import dk.sdu.cloud.accounting.compute.api.ComputeAccountingJobsDescriptions
import dk.sdu.cloud.accounting.compute.api.ComputeAccountingTimeDescriptions
import dk.sdu.cloud.accounting.compute.services.CompletedJobsService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.service.securityPrincipal
import io.ktor.routing.Route

class ComputeAccountingController<DBSession>(
    private val completedJobsService: CompletedJobsService<DBSession>
) : Controller {
    override val baseContext: String = ComputeAccountingDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(ComputeAccountingDescriptions.buildReport) { req ->
            logEntry(log, req)

            val computeTime = completedJobsService.computeBillableItems(
                req.periodStartMs,
                req.periodEndMs,
                call.securityPrincipal.username
            )

            ok(BuildReportResponse(items = computeTime))
        }

        implement(ComputeAccountingDescriptions.listResources) { req ->
            logEntry(log, req)

            ok(
                ListResourceResponse(
                    listOf(
                        AccountingResource(ComputeAccountingTimeDescriptions.resourceType),
                        AccountingResource(ComputeAccountingJobsDescriptions.resourceType)
                    )
                )
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
