package dk.sdu.cloud.app.orchestrator.rpc

import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.accounting.api.WalletOwnerType
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.services.PublicIPService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Page
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
            ok(
                publicIps.approveApplication(db, request.id)
            )
        }

        implement(PublicIPs.rejectAddress) {
            ok(
                publicIps.rejectApplication(db, request.id)
            )
        }

        implement(PublicIPs.listAddressApplicationsForApproval) {
            ok(Page(1, 10, 1, listOf(
                AddressApplication(
                    42,
                    "This is my application",
                    System.currentTimeMillis() - (1000 * 60 * 60 * 3),
                    "Awesome project name",
                    WalletOwnerType.PROJECT
                )
            )))
        }

        implement(PublicIPs.listAddressApplications) {
            ok(Page(1, 10, 1, listOf(
                AddressApplication(
                    42,
                    "This is my application",
                    System.currentTimeMillis() - (1000 * 60 * 60 * 3),
                    "Awesome project name",
                    WalletOwnerType.PROJECT
                )
            )))
        }

        implement(PublicIPs.listAssignedAddresses) {
            ok(Page(2, 10, 1, listOf(
                PublicIP(
                    42,
                    "10.135.0.142",
                    ctx.securityPrincipal.username,
                    WalletOwnerType.USER,
                    listOf(PortAndProtocol(1111, InternetProtocol.TCP)),
                    null
                ),

                PublicIP(
                    43,
                    "10.135.0.143",
                    ctx.securityPrincipal.username,
                    WalletOwnerType.USER,
                    listOf(PortAndProtocol(1111, InternetProtocol.UDP)),
                    null
                ),
            )))
        }

        implement(PublicIPs.listMyAddresses) {
            ok(Page(4, 10, 1, listOf(
                PublicIP(
                    42,
                    "10.135.0.142",
                    ctx.securityPrincipal.username,
                    WalletOwnerType.USER,
                    listOf(PortAndProtocol(1111, InternetProtocol.TCP)),
                    null
                ),
                PublicIP(
                    43,
                    "10.135.0.143",
                    ctx.securityPrincipal.username,
                    WalletOwnerType.USER,
                    emptyList(),
                    null
                ),
                PublicIP(
                    44,
                    "10.135.0.144",
                    ctx.securityPrincipal.username,
                    WalletOwnerType.USER,
                    (11000..11020).map { PortAndProtocol(it, if (it % 3 == 0) InternetProtocol.UDP else InternetProtocol.TCP) },
                    null
                ),
                PublicIP(
                    45,
                    "10.135.0.145",
                    ctx.securityPrincipal.username,
                    WalletOwnerType.USER,
                    (11000..11020).map { PortAndProtocol(it, if (it % 3 == 0) InternetProtocol.UDP else InternetProtocol.TCP) },
                    "foobar"
                )
            )))
        }

        implement(PublicIPs.updatePorts) {
            ok(Unit)
        }

        implement(PublicIPs.releaseAddress) {
            ok(publicIps.releaseAddress(db, request.id))
        }
    }
}