package dk.sdu.cloud.accounting.compute.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.accounting.compute.api.ComputeAccountingDescriptions
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route

class AccountingController : Controller {
    override val baseContext = ComputeAccountingDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(ComputeAccountingDescriptions.listEvents) { req ->
            logEntry(log, req)
            error(CommonErrorMessage("Not yet implemented"), HttpStatusCode.NotFound)
        }

        implement(ComputeAccountingDescriptions.chart) { req ->
            logEntry(log, req)
            error(CommonErrorMessage("Not yet implemented"), HttpStatusCode.NotFound)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}