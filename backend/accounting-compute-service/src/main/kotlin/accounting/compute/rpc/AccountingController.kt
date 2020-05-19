package dk.sdu.cloud.accounting.compute.rpc

import dk.sdu.cloud.accounting.compute.MachineType
import dk.sdu.cloud.accounting.compute.api.AccountingCompute
import dk.sdu.cloud.accounting.compute.api.ComputeBalance
import dk.sdu.cloud.accounting.compute.api.CreditsAccount
import dk.sdu.cloud.accounting.compute.api.RetrieveBalanceResponse
import dk.sdu.cloud.accounting.compute.services.BalanceService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession

class AccountingController(
    private val db: DBContext,
    private val balance: BalanceService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(AccountingCompute.grantCredits) {
            balance.addToBalance(db, ctx.securityPrincipal.username, request.account, request.credits)
            ok(Unit)
        }

        implement(AccountingCompute.reserveCredits) {
            balance.reserveCredits(
                db,
                request.jobInitiatedBy,
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
                    val balance = balance.getBalance(
                        session,
                        ctx.securityPrincipal.username,
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
                ctx.securityPrincipal.username,
                request.account,
                request.lastKnownBalance,
                request.newBalance
            )

            ok(Unit)
        }

        return@with
    }
}