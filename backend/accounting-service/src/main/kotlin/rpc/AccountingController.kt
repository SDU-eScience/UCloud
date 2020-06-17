package dk.sdu.cloud.accounting.rpc

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.services.BalanceService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.toActor

class AccountingController(
    private val db: DBContext,
    private val balance: BalanceService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Wallets.grantCredits) {
            balance.addToBalance(db, ctx.securityPrincipal.toActor(), request.wallet, request.credits)
            ok(Unit)
        }

        implement(Wallets.reserveCredits) {
            balance.reserveCredits(
                db,
                Actor.SystemOnBehalfOfUser(request.jobInitiatedBy),
                request
            )

            ok(Unit)
        }

        implement(Wallets.chargeReservation) {
            balance.chargeFromReservation(
                db,
                request.name,
                request.amount,
                request.productUnits
            )

            ok(Unit)
        }

        implement(Wallets.retrieveBalance) {
            val project = ctx.project
            val accountId = request.id ?: (project ?: ctx.securityPrincipal.username)
            val accountType = request.type ?: if (project != null) WalletOwnerType.PROJECT else WalletOwnerType.USER

            ok(
                RetrieveBalanceResponse(
                    balance.getWalletsForAccount(
                        db,
                        ctx.securityPrincipal.toActor(),
                        accountId,
                        accountType,
                        request.includeChildren ?: false
                    )
                )
            )
        }

        implement(Wallets.setBalance) {
            balance.setBalance(
                db,
                ctx.securityPrincipal.toActor(),
                request.wallet,
                request.lastKnownBalance,
                request.newBalance
            )

            ok(Unit)
        }

        return@with
    }
}
