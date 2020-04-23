package dk.sdu.cloud.accounting.compute.http

import dk.sdu.cloud.accounting.api.ChartDataTypes
import dk.sdu.cloud.accounting.api.UsageResponse
import dk.sdu.cloud.accounting.compute.api.ComputeAccountingTimeDescriptions
import dk.sdu.cloud.accounting.compute.services.CompletedJobsService
import dk.sdu.cloud.accounting.compute.services.ComputeUser
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable

class ComputeTimeController(
    private val completedJobsService: CompletedJobsService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(ComputeAccountingTimeDescriptions.usage) {
            val user = run {
                val projectId = ctx.project
                if (projectId != null) {
                    ComputeUser.Project(projectId)
                } else {
                    ComputeUser.User(ctx.securityPrincipal.username)
                }
            }

            ok(
                UsageResponse(
                    usage = completedJobsService.computeUsage(user),
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
