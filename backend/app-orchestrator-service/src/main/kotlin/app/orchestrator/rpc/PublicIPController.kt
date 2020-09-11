package dk.sdu.cloud.app.orchestrator.rpc

import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.accounting.api.WalletOwnerType
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.services.PublicIPService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.DBContext

class PublicIPController(
    private val db: DBContext,
    private val publicIps: PublicIPService
) : Controller {
    // NOTE(Dan): All of this is dummy data. Feel free to replace it with a proper implementation ;)
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(PublicIPs.addToPool) {
            ok(Unit)
        }

        implement(PublicIPs.removeFromPool) {
            ok(Unit)
        }

        implement(PublicIPs.applyForAddress) {
            ok(FindByLongId(42L))
        }

        implement(PublicIPs.approveAddress) {
            ok(Unit)
        }

        implement(PublicIPs.rejectAddress) {
            ok(Unit)
        }

        implement(PublicIPs.listAddressApplications) {
            ok(Page(1, 10, 1, listOf(AddressApplication(42, "This is my application"))))
        }

        implement(PublicIPs.listAssignedAddresses) {
            ok(Page(2, 10, 1, listOf(
                PublicIP(
                    42,
                    "10.135.0.142",
                    ctx.securityPrincipal.username,
                    WalletOwnerType.USER,
                    listOf(PortAndProtocol(1111, InternetProtocol.TCP))
                ),

                PublicIP(
                    43,
                    "10.135.0.143",
                    ctx.securityPrincipal.username,
                    WalletOwnerType.USER,
                    listOf(PortAndProtocol(1111, InternetProtocol.UDP))
                ),
            )))
        }

        implement(PublicIPs.listMyAddresses) {
            ok(Page(1, 10, 1, listOf(
                PublicIP(
                    42,
                    "10.135.0.142",
                    ctx.securityPrincipal.username,
                    WalletOwnerType.USER,
                    listOf(PortAndProtocol(1111, InternetProtocol.TCP))
                )
            )))
        }

        implement(PublicIPs.updatePorts) {
            ok(Unit)
        }

        implement(PublicIPs.releaseAddress) {
            ok(Unit)
        }
    }
}