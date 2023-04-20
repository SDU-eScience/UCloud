package dk.sdu.cloud.accounting.rpc

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.services.wallets.AccountingService
import dk.sdu.cloud.accounting.services.wallets.DepositNotificationService
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.actorAndProject
import dk.sdu.cloud.service.db.async.mapItems


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
            ok(accounting.charge(actorAndProject, request))
        }

        implementOrDispatch(AccountingV2.reportTotalUsage){
            //TODO(HENRIK)
        }

        implementOrDispatch(AccountingV2.reportDelta){
            //TODO(HENRIK)
        }

        implementOrDispatch(Accounting.deposit) {
            val newRequestType = bulkRequestOf(
                request.items.mapNotNull { oldRequest ->
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
            ok(BulkResponse<FindByStringId>(response))
        }

        implementOrDispatch(Accounting.check) {
            ok(accounting.check(actorAndProject, request))
        }

        implementOrDispatch(Accounting.updateAllocation) {
            val user = ctx.securityPrincipal.username
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
                    it.amount,
                    it.startDate ?: Time.now(),
                    it.endDate ?: Long.MAX_VALUE,
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
            ok(WalletsInternalRetrieveResponse(accounting.retrieveWalletsInternal(ActorAndProject(Actor.System, null), walletOwner)))
        }

        implementOrDispatch(WalletsV2.retrieveWalletsInternal) {
            ok(WalletsInternalRetrieveResponse(accounting.retrieveWalletsInternal(ActorAndProject(Actor.System, null), request.owner)))
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
                        WalletAllocation(
                            id = it.id,
                            allocationPath = it.allocationPath,
                            balance = it.quota - (it.treeUsage ?: it.localUsage ),
                            initialBalance = it.quota,
                            localBalance = it.quota - it.localUsage,
                            startDate = it.startDate,
                            endDate = it.endDate,
                            grantedIn = it.grantedIn,
                            maxUsableBalance = it.treeUsage,
                            canAllocate = it.canAllocate,
                            allowSubAllocationsToAllocate = it.allowSubAllocationsToAllocate
                        )
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
                    SubAllocation(
                        id = it.id,
                        path = it.path,
                        startDate = it.startDate,
                        endDate = it.endDate,
                        productCategoryId = ProductCategoryId(it.productCategoryId.name, it.productCategoryId.provider),
                        productType = it.productCategoryId.productType,
                        chargeType = if (it.productCategoryId.accountingFrequency == AccountingFrequency.ONCE) ChargeType.DIFFERENTIAL_QUOTA else ChargeType.ABSOLUTE,
                        //TODO(HENRIK) MOST LIKELY NOT CORRECT
                        unit = if (it.productCategoryId.accountingFrequency == AccountingFrequency.ONCE) ProductPriceUnit.PER_UNIT else ProductPriceUnit.CREDITS_PER_MINUTE,
                        workspaceId = it.workspaceId,
                        workspaceTitle = it.workspaceTitle,
                        workspaceIsProject = it.workspaceIsProject,
                        projectPI = it.projectPI,
                        remaining = it.remaining,
                        initialBalance = it.initialBalance,
                        grantedIn = it.grantedIn
                    )
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
                    SubAllocation(
                        id = it.id,
                        path = it.path,
                        startDate = it.startDate,
                        endDate = it.endDate,
                        productCategoryId = ProductCategoryId(it.productCategoryId.name, it.productCategoryId.provider),
                        productType = it.productCategoryId.productType,
                        chargeType = if (it.productCategoryId.accountingFrequency == AccountingFrequency.ONCE) ChargeType.DIFFERENTIAL_QUOTA else ChargeType.ABSOLUTE,
                        //TODO(HENRIK) MOST LIKELY NOT CORRECT
                        unit = if (it.productCategoryId.accountingFrequency == AccountingFrequency.ONCE) ProductPriceUnit.PER_UNIT else ProductPriceUnit.CREDITS_PER_MINUTE,
                        workspaceId = it.workspaceId,
                        workspaceTitle = it.workspaceTitle,
                        workspaceIsProject = it.workspaceIsProject,
                        projectPI = it.projectPI,
                        remaining = it.remaining,
                        initialBalance = it.initialBalance,
                        grantedIn = it.grantedIn
                    )
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

    companion object : Loggable {
        override val log = logger()
    }
}
