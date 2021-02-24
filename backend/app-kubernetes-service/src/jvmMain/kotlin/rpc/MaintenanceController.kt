package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.app.kubernetes.api.IsPausedResponse
import dk.sdu.cloud.app.kubernetes.api.Maintenance
import dk.sdu.cloud.app.kubernetes.services.MaintenanceService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller

class MaintenanceController(
    private val maintenance: MaintenanceService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Maintenance.drainCluster) {
            maintenance.drainCluster()
            ok(Unit)
        }

        implement(Maintenance.drainNode) {
            maintenance.drainNode(request.node)
            ok(Unit)
        }

        implement(Maintenance.isPaused) {
            ok(IsPausedResponse(maintenance.isPaused()))
        }

        implement(Maintenance.updatePauseState) {
            maintenance.setPauseState(request.paused)
            ok(Unit)
        }

        implement(Maintenance.killJob) {
            maintenance.killJob(request.jobId)
            ok(Unit)
        }
    }
}