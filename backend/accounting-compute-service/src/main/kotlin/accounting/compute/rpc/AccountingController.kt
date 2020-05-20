package dk.sdu.cloud.accounting.compute.rpc

import dk.sdu.cloud.accounting.compute.MachineType
import dk.sdu.cloud.accounting.compute.api.*
import dk.sdu.cloud.accounting.compute.services.Actor
import dk.sdu.cloud.accounting.compute.services.BalanceService
import dk.sdu.cloud.accounting.compute.services.VisualizationService
import dk.sdu.cloud.accounting.compute.services.toActor
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession

class AccountingController(
    private val db: DBContext,
    private val balance: BalanceService,
    private val visualization: VisualizationService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(AccountingCompute.grantCredits) {
            balance.addToBalance(db, ctx.securityPrincipal.toActor(), request.account, request.credits)
            ok(Unit)
        }

        implement(AccountingCompute.reserveCredits) {
            balance.reserveCredits(
                db,
                Actor.SystemOnBehalfOfUser(request.jobInitiatedBy),
                request.account,
                request.jobId,
                request.amount,
                request.expiresAt
            )

            ok(Unit)
        }

        implement(AccountingCompute.chargeReservation) {
            balance.chargeFromReservation(
                db,
                request.name,
                request.amount
            )

            ok(Unit)
        }

        implement(AccountingCompute.retrieveBalance) {
            val balances = db.withSession { session ->
                MachineType.values().map { machineType ->
                    val (balance, _) = balance.getBalance(
                        session,
                        ctx.securityPrincipal.toActor(),
                        CreditsAccount(request.id, request.type, machineType)
                    )

                    ComputeBalance(machineType, balance)
                }
            }

            ok(RetrieveBalanceResponse(balances))
        }

        implement(AccountingCompute.setBalance) {
            balance.setBalance(
                db,
                ctx.securityPrincipal.toActor(),
                request.account,
                request.lastKnownBalance,
                request.newBalance
            )

            ok(Unit)
        }

        implement(AccountingCompute.dailyUsage) {
            ok(
                DailyComputeChart(
                    visualization.dailyUsage(
                        db,
                        ctx.securityPrincipal.toActor(),
                        request.project ?: ctx.securityPrincipal.username,
                        if (request.project != null) AccountType.PROJECT else AccountType.USER,
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
                        if (request.project != null) AccountType.PROJECT else AccountType.USER,
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
                        AccountType.PROJECT,
                        request.group,
                        request.pStart,
                        request.pEnd
                    )
                )
            )
        }

        return@with
    }
}