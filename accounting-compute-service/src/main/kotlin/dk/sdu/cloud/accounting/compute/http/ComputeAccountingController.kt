package dk.sdu.cloud.accounting.compute.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.accounting.api.AccountingResource
import dk.sdu.cloud.accounting.api.BuildReportResponse
import dk.sdu.cloud.accounting.api.ListResourceResponse
import dk.sdu.cloud.accounting.compute.api.ComputeAccountingDescriptions
import dk.sdu.cloud.accounting.compute.api.ComputeAccountingTimeDescriptions
import dk.sdu.cloud.accounting.compute.services.CompletedJobsService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode

class ComputeAccountingController<DBSession>(
    private val completedJobsService: CompletedJobsService<DBSession>
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(ComputeAccountingDescriptions.buildReport) {
            if (ctx.securityPrincipal.username != "_accounting") {
                return@implement (error(CommonErrorMessage("User Not Allowed"), HttpStatusCode.Unauthorized))
            } else {
                val computeTime = completedJobsService.computeBillableItems(
                    request.periodStartMs,
                    request.periodEndMs,
                    request.user,
                    null
                )

                return@implement (ok(BuildReportResponse(items = computeTime)))
            }
        }

        implement(ComputeAccountingDescriptions.listResources) {
            ok(
                ListResourceResponse(
                    listOf(
                        AccountingResource(ComputeAccountingTimeDescriptions.resourceType)
                    )
                )
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
