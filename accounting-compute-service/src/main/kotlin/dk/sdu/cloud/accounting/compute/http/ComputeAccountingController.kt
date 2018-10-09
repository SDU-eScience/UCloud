package dk.sdu.cloud.accounting.compute.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.accounting.api.AccountingResource
import dk.sdu.cloud.accounting.api.ListResourceResponse
import dk.sdu.cloud.accounting.compute.api.ComputeAccountingDescriptions
import dk.sdu.cloud.accounting.compute.api.ComputeAccountingJobsDescriptions
import dk.sdu.cloud.accounting.compute.api.ComputeAccountingTimeDescriptions
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route

class ComputeAccountingController : Controller {
    override val baseContext: String = ComputeAccountingDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(ComputeAccountingDescriptions.buildReport) { req ->
            logEntry(log, req)
            error(CommonErrorMessage("Bad"), HttpStatusCode.NotFound)
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
