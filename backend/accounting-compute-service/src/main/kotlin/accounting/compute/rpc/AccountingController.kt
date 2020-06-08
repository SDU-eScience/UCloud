package dk.sdu.cloud.accounting.compute.rpc

import dk.sdu.cloud.accounting.compute.api.*
import dk.sdu.cloud.accounting.compute.services.BalanceService
import dk.sdu.cloud.accounting.compute.services.VisualizationService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.toActor

class AccountingController(
    private val db: DBContext,
    private val balance: BalanceService,
    private val visualization: VisualizationService
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
                request.account,
                request.jobId,
                request.amount,
                request.expiresAt,
                request.productId,
                request.productUnits
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
            ok(
                RetrieveBalanceResponse(
                    balance.getWalletsForAccount(db, ctx.securityPrincipal.toActor(), request.id, request.type)
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

        /*
        implement(AccountingCompute.dailyUsage) {
            ok(
                DailyComputeChart(
                    visualization.dailyUsage(
                        db,
                        ctx.securityPrincipal.toActor(),
                        request.project ?: ctx.securityPrincipal.username,
                        if (request.project != null) WalletOwnerType.PROJECT else WalletOwnerType.USER,
                        request.group,
                        request.pStart,
                        request.pEnd
                    )
                )
            )
        }

        implement(AccountingCompute.cumulativeUsage) {
            ok(
                CumulativeUsageChart(
                    visualization.cumulativeUsage(
                        db,
                        ctx.securityPrincipal.toActor(),
                        request.project ?: ctx.securityPrincipal.username,
                        if (request.project != null) WalletOwnerType.PROJECT else WalletOwnerType.USER,
                        request.group,
                        request.pStart,
                        request.pEnd
                    )
                )
            )
        }

        implement(AccountingCompute.breakdown) {
            ok(
                BreakdownResponse(
                    visualization.usageBreakdown(
                        db,
                        ctx.securityPrincipal.toActor(),
                        request.project,
                        WalletOwnerType.PROJECT,
                        request.group,
                        request.pStart,
                        request.pEnd
                    )
                )
            )
        }
         */

        return@with
    }
}
