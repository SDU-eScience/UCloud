package dk.sdu.cloud.accounting.rpc

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.services.wallets.AccountingService
import dk.sdu.cloud.accounting.services.wallets.DepositNotificationService
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.actorAndProject


class AccountingController(
    private val accounting: AccountingService,
    private val notifications: DepositNotificationService,
    private val client: AuthenticatedClient,
) : Controller {
    private fun <R : Any, S : Any, E : Any> RpcServer.implementOrDispatch(
        call: CallDescription<R, S, E>,
        handler: suspend CallHandler<R, S, E>.() -> Unit,
    ) {
        implement(call) {
            val activeProcessor = accounting.retrieveActiveProcessorAddress()
            if (activeProcessor == null) {
                handler()
            } else {
                ok(
                    call.call(
                        request,
                        client.withFixedHost(HostInfo(activeProcessor, "http", 8080))
                    ).orThrow()
                )
            }
        }
    }
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implementOrDispatch(Accounting.findRelevantProviders) {
            val responses = request.items.map {
                val providers = accounting.findRelevantProviders(
                    actorAndProject,
                    it.username,
                    it.project,
                    it.useProject
                )
                FindRelevantProvidersResponse(providers)
            }
            ok(BulkResponse(responses))
        }

        implementOrDispatch(AccountingV2.findRelevantProviders) {
            val responses = request.items.map {
                val providers = accounting.findRelevantProviders(
                    actorAndProject,
                    it.username,
                    it.project,
                    it.useProject
                )
                FindRelevantProvidersResponse(providers)
            }
            ok(BulkResponse(responses))
        }

        implementOrDispatch(Accounting.charge) {
            ok(accounting.charge(actorAndProject, request, dryRun = false))
        }

        implementOrDispatch(AccountingV2.reportTotalUsage){
            ok(accounting.chargeTotal(actorAndProject, request, false))
        }

        implementOrDispatch(AccountingV2.reportDelta){
            ok(accounting.chargeDelta(actorAndProject, request, false))
        }

        implementOrDispatch(Accounting.deposit) {
            val newRequestType = bulkRequestOf(
                request.items.map { oldRequest ->
                    SubAllocationRequestItem(
                        oldRequest.sourceAllocation,
                        oldRequest.recipient,
                        oldRequest.amount,
                        oldRequest.startDate ?: Time.now(),
                        oldRequest.endDate ?: Long.MAX_VALUE,
                        oldRequest.dry,
                        oldRequest.grantedIn
                    )
                }
            )
            accounting.subAllocate(actorAndProject, newRequestType)
            ok(Unit)
        }

        implementOrDispatch(AccountingV2.subAllocate){
            val response = accounting.subAllocate(actorAndProject, request)
            ok(BulkResponse(response))
        }

        implementOrDispatch(Accounting.check) {
            ok(accounting.charge(actorAndProject, request, dryRun = true))
        }

        implementOrDispatch(Accounting.updateAllocation) {
            val newTypeRequests = request.items.map { req ->
                UpdateAllocationV2RequestItem(
                    req.id,
                    req.balance,
                    req.startDate,
                    req.endDate,
                    req.reason
                )
            }
            ok(accounting.updateAllocation(actorAndProject, BulkRequest(newTypeRequests)))
        }

        implementOrDispatch(AccountingV2.updateAllocation){
            ok(accounting.updateAllocation(actorAndProject, request))
        }

        implementOrDispatch(Accounting.rootDeposit) {
            val newTypeRequest = request.items.map {
                RootAllocationRequestItem(
                    it.recipient,
                    ProductCategoryIdV2(it.categoryId.name, it.categoryId.provider),
                    it.amount,
                    it.startDate ?: Time.now(),
                    it.endDate ?: 4102444800000, //Long.MaxValue is to large for Postgres so we give the timestamp to 1/1/2100
                    deicAllocationId = null,
                    forcedSync = it.forcedSync
                )
            }
            accounting.rootAllocate(actorAndProject, BulkRequest(newTypeRequest))
            ok(Unit)
        }

        implementOrDispatch(AccountingV2.rootAllocate){
            val response = accounting.rootAllocate(actorAndProject, request)
            ok(BulkResponse(response))
        }

        implement(Wallets.browse) {
            ok(accounting.browseWallets(actorAndProject, request))
        }

        implement(WalletsV2.browse) {
            ok(accounting.browseWallets(actorAndProject, request))
        }

        implementOrDispatch(Wallets.retrieveWalletsInternal) {
            val walletOwner = request.owner
            val response = accounting.retrieveWalletsInternal(
                ActorAndProject(Actor.System, null),
                walletOwner
            ).map { wallet ->
                wallet.toV1()
            }
            ok(WalletsInternalRetrieveResponse(response))
        }

        implementOrDispatch(WalletsV2.retrieveWalletsInternal) {
            ok(WalletsInternalV2RetrieveResponse(accounting.retrieveWalletsInternal(ActorAndProject(Actor.System, null), request.owner)))
        }

        implementOrDispatch(Wallets.retrieveAllocationsInternal) {
            val walletOwner = request.owner
            val category = ProductCategoryIdV2(request.categoryId.name, request.categoryId.provider)
            ok(
                WalletAllocationsInternalRetrieveResponse(
                    accounting.retrieveAllocationsInternal(
                        ActorAndProject(Actor.System, null),
                        walletOwner,
                        category
                    ).map {
                        it.toV1()
                    }
                )
            )
        }

        implementOrDispatch(WalletAllocationsV2.retrieveAllocationsInternal) {
            ok(
                WalletAllocationsV2InternalRetrieveResponse(
                    accounting.retrieveAllocationsInternal(
                        ActorAndProject(Actor.System, null),
                        request.owner,
                        request.categoryId
                    )
                )
            )
        }

        implementOrDispatch(Wallets.resetState) {
            ok(accounting.resetState())
        }

        implement(Wallets.searchSubAllocations) {
            val newResponseType = accounting.browseSubAllocations(actorAndProject, request, request.query)
            val oldResponseType = WalletsBrowseSubAllocationsResponse(
                items = newResponseType.items.map {
                    it.toV1()
                },
                itemsPerPage = newResponseType.itemsPerPage,
                next = newResponseType.next
            )
            ok(oldResponseType)
        }

        implement(WalletAllocationsV2.searchSubAllocations) {
            ok(accounting.browseSubAllocations(actorAndProject, request, request.query))
        }

        implement(Wallets.browseSubAllocations) {
            val newResponseType = accounting.browseSubAllocations(actorAndProject, request)
            val oldResponseType = WalletsBrowseSubAllocationsResponse(
                items = newResponseType.items.map {
                    it.toV1()
                },
                itemsPerPage = newResponseType.itemsPerPage,
                next = newResponseType.next
            )
            ok(oldResponseType)
        }

        implement(WalletAllocationsV2.browseSubAllocations) {
            ok(accounting.browseSubAllocations(actorAndProject, request))
        }

        implement(Wallets.retrieveRecipient) {
            ok(accounting.retrieveRecipient(actorAndProject, request))
        }

        implement(WalletAllocationsV2.retrieveRecipient) {
            ok(accounting.retrieveRecipient(actorAndProject, request))
        }

        implement(Wallets.register) {
            ok(accounting.register(actorAndProject, request))
        }

        implement(WalletAllocationsV2.register) {
            ok(accounting.register(actorAndProject, request))
        }

        implementOrDispatch(Wallets.retrieveProviderSummary) {
            ok(accounting.retrieveProviderSummary(actorAndProject, request))
        }

        implementOrDispatch(WalletAllocationsV2.retrieveProviderSummary) {
            ok(accounting.retrieveProviderSummary(actorAndProject, request))
        }

        implement(Visualization.retrieveUsage) {
            val charts = accounting.retrieveUsageV2(actorAndProject, request)

            ok(charts)
        }

        implement(Visualization.retrieveBreakdown) {
            ok(accounting.retrieveBreakdownV2(actorAndProject, request))
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

    companion object : Loggable {
        override val log = logger()
    }
}
