package dk.sdu.cloud.accounting.rpc

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.services.wallets.AccountingService
import dk.sdu.cloud.accounting.services.wallets.DepositNotificationService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject
import java.lang.System.currentTimeMillis

class AccountingController(
    private val accounting: AccountingService,
    private val notifications: DepositNotificationService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Accounting.charge) {
            ok(accounting.charge(actorAndProject, request))
        }

        implement(Accounting.deposit) {
            val user = ctx.securityPrincipal.username
            println(request)
            request.items.forEach { req ->
                req.transactionId = "${user}-${req.transactionId}"
                req.startDate = req.startDate ?: currentTimeMillis()
            }
            println(request)
            ok(accounting.deposit(actorAndProject, request))
        }

        implement(Accounting.check) {
            ok(accounting.check(actorAndProject, request))
        }

        implement(Accounting.transfer) {
            val user = ctx.securityPrincipal.username
            request.items.forEach { req ->
                req.transactionId = "${user}-${req.transactionId}"
            }
            ok(accounting.transfer(actorAndProject, request))
        }

        implement(Accounting.updateAllocation) {
            val user = ctx.securityPrincipal.username
            request.items.forEach { req ->
                req.startDate = (req.startDate + 60000) //adds a single minute to handle that we divide with 1000 before entering it to the DB (millisecs are cut)
                req.transactionId = "${user}-${req.transactionId}"
            }
            ok(accounting.updateAllocation(actorAndProject, request))
        }

        implement(Accounting.rootDeposit) {
            val user = ctx.securityPrincipal.username
            request.items.forEach { req ->
                req.transactionId = "${user}-${req.transactionId}"
                req.startDate = req.startDate ?: currentTimeMillis()
            }
            ok(accounting.rootDeposit(actorAndProject, request))
        }

        implement(Wallets.browse) {
            ok(accounting.browseWallets(actorAndProject, request))
        }

        implement(Wallets.searchSubAllocations) {
            ok(accounting.browseSubAllocations(actorAndProject, request, request.query))
        }

        implement(Wallets.browseSubAllocations) {
            ok(accounting.browseSubAllocations(actorAndProject, request))
        }

        implement(Wallets.retrieveRecipient) {
            ok(accounting.retrieveRecipient(actorAndProject, request))
        }

        implement(Wallets.push) {
            ok(accounting.pushWallets(actorAndProject, request))
        }

        implement(Wallets.register) {
            ok(accounting.register(actorAndProject, request))
        }

        implement(Wallets.retrieveProviderSummary) {
            ok(accounting.retrieveProviderSummary(actorAndProject, request))
        }

        implement(Visualization.retrieveUsage) {
            ok(accounting.retrieveUsage(actorAndProject, request))
        }

        implement(Visualization.retrieveBreakdown) {
            ok(accounting.retrieveBreakdown(actorAndProject, request))
        }

        implement(Transactions.browse) {
            ok(accounting.browseTransactions(actorAndProject, request))
        }

        implement(DepositNotifications.retrieve) {
            ok(notifications.retrieveNotifications(actorAndProject))
        }

        implement(DepositNotifications.markAsRead) {
            notifications.markAsRead(actorAndProject, request)
            ok(Unit)
        }

        return@with
    }
}
