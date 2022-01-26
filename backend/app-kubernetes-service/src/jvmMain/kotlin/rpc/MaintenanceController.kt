package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.Roles
import dk.sdu.cloud.app.kubernetes.api.IsPausedResponse
import dk.sdu.cloud.app.kubernetes.api.Maintenance
import dk.sdu.cloud.app.kubernetes.services.MaintenanceService
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.validateAndDecodeOrNull
import io.ktor.http.*

class MaintenanceController(
    private val maintenance: MaintenanceService,
    private val tokenValidation: TokenValidation<*>,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Maintenance.drainCluster) {
            verifyUser()

            maintenance.drainCluster()
            ok(Unit)
        }

        implement(Maintenance.drainNode) {
            verifyUser()

            maintenance.drainNode(request.node)
            ok(Unit)
        }

        implement(Maintenance.isPaused) {
            verifyUser()

            ok(IsPausedResponse(maintenance.isPaused()))
        }

        implement(Maintenance.updatePauseState) {
            verifyUser()

            maintenance.setPauseState(request.paused)
            ok(Unit)
        }

        implement(Maintenance.killJob) {
            verifyUser()

            maintenance.killJob(request.jobId)
            ok(Unit)
        }
    }

    private fun CallHandler<*, *, *>.verifyUser() {
        withContext<HttpCall> {
            val bearer = ctx.context.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
                ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
            val principal = tokenValidation.validateAndDecodeOrNull(bearer)?.principal
                ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
            if (principal.role !in Roles.PRIVILEGED) throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
        }
    }
}