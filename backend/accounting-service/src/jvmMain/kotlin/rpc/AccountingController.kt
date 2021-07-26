package dk.sdu.cloud.accounting.rpc

import dk.sdu.cloud.accounting.api.Accounting
import dk.sdu.cloud.accounting.services.wallets.AccountingService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject

class AccountingController(
    private val accounting: AccountingService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Accounting.charge) {
            ok(accounting.charge(actorAndProject, request))
        }

        implement(Accounting.deposit) {
            ok(accounting.deposit(actorAndProject, request))
        }

        implement(Accounting.check) {
            ok(accounting.check(actorAndProject, request))
        }

        implement(Accounting.transfer) {
            ok(accounting.transfer(actorAndProject, request))
        }

        implement(Accounting.updateAllocation) {
            ok(accounting.updateAllocation(actorAndProject, request))
        }
        return@with
    }
}
