package dk.sdu.cloud.accounting.rpc

import dk.sdu.cloud.Actor
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.services.BalanceService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.toActor

class AccountingController(
    private val db: DBContext,
    private val balance: BalanceService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Accounting.charge) {

        }

        implement(Accounting.deposit) {

        }

        implement(Accounting.transfer) {

        }

        implement(Wallets.push) {

        }

        implement(Wallets.browseWallets) {

        }

        return@with
    }
}
