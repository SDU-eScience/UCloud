package dk.sdu.cloud.app.orchestrator.rpc

import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.services.PublicIPService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.toActor

class PublicIPController(
    private val db: DBContext,
    private val publicIps: PublicIPService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(PublicIPs.addToPool) {
            ok(publicIps.addToPool(db, request.addressesCovered()))
        }

        implement(PublicIPs.removeFromPool) {
            ok(publicIps.removeFromPool(db, request.addressesCovered()))
        }

        implement(PublicIPs.applyForAddress) {
            ok(
                FindByLongId(
                    publicIps.applyForAddress(db, ctx.securityPrincipal.toActor(), ctx.project, request.application)
                )
            )
        }

        implement(PublicIPs.approveAddress) {
            ok(publicIps.approveApplication(db, request.id))
        }

        implement(PublicIPs.rejectAddress) {
            ok(publicIps.rejectApplication(db, request.id))
        }

        implement(PublicIPs.listAddressApplicationsForApproval) {
            ok(publicIps.listAddressApplicationsForApproval(db, NormalizedPaginationRequest(request.itemsPerPage, request.page)))
        }

        implement(PublicIPs.listAddressApplications) {
            ok(publicIps.listAddressApplications(db, ctx.securityPrincipal.toActor(), ctx.project, request.pending, NormalizedPaginationRequest(request.itemsPerPage, request.page)))
        }

        implement(PublicIPs.listAssignedAddresses) {
            ok(publicIps.listAssignedAddresses(db, NormalizedPaginationRequest(request.itemsPerPage, request.page)))
        }

        implement(PublicIPs.listAvailableAddresses) {
            ok(publicIps.listAvailableAddresses(db, NormalizedPaginationRequest(request.itemsPerPage, request.page)))
        }

        implement(PublicIPs.listMyAddresses) {
            ok(publicIps.listMyAddresses(db, ctx.securityPrincipal.toActor(), ctx.project, NormalizedPaginationRequest(request.itemsPerPage, request.page)))
        }

        implement(PublicIPs.openPorts) {
            ok(publicIps.openPorts(db, request.id, request.portList))
        }

        implement(PublicIPs.closePorts) {
            ok(publicIps.closePorts(db, request.id, request.portList))
        }

        implement(PublicIPs.releaseAddress) {
            ok(publicIps.releaseAddress(db, request.id))
        }
    }
}