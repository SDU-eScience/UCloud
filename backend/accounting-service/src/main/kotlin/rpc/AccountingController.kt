package dk.sdu.cloud.accounting.rpc

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.PageV2
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
import dk.sdu.cloud.calls.server.responseAllocator
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.ServerFeature
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.feature
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.actorAndProject
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AccountingController(
    private val micro: Micro,
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

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implementOrDispatch(Accounting.findRelevantProviders) {
            val responses = request.items.map {
                val providers = accounting.findRelevantProviders(
                    actorAndProject,
                    it.username,
                    it.project,
                    it.useProject,
                    it.filterProductType,
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

        implementOrDispatch(AccountingV2.reportTotalUsage) {
            ok(accounting.chargeTotal(actorAndProject, request, false))
        }

        implementOrDispatch(AccountingV2.reportDelta) {
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

        implementOrDispatch(AccountingV2.subAllocate) {
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

        implementOrDispatch(AccountingV2.updateAllocation) {
            ok(accounting.updateAllocation(actorAndProject, request))
        }

        implementOrDispatch(Accounting.rootDeposit) {
            val newTypeRequest = request.items.map {
                RootAllocationRequestItem(
                    it.recipient,
                    ProductCategoryIdV2(it.categoryId.name, it.categoryId.provider),
                    it.amount,
                    it.startDate ?: Time.now(),
                    it.endDate
                        ?: 4102444800000, //Long.MaxValue is to large for Postgres so we give the timestamp to 1/1/2100
                    deicAllocationId = null,
                    forcedSync = it.forcedSync
                )
            }
            accounting.rootAllocate(actorAndProject, BulkRequest(newTypeRequest))
            ok(Unit)
        }

        implementOrDispatch(AccountingV2.rootAllocate) {
            val response = accounting.rootAllocate(actorAndProject, request)
            ok(BulkResponse(response))
        }

        implement(Wallets.browse) {
            ok(accounting.browseWallets(actorAndProject, request))
        }

        implement(AccountingV2.browseWallets) {
            val owner = WalletOwner.fromActorAndProject(actorAndProject)
            val items = accounting.retrieveWalletsInternal(actorAndProject, owner).filter {
                request.filterType == null || request.filterType == it.paysFor.productType
            }

            ok(PageV2.of(items))
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

        implementOrDispatch(AccountingV2.browseWalletsInternal) {
            ok(
                WalletsInternalV2RetrieveResponse(
                    accounting.retrieveWalletsInternal(
                        ActorAndProject(Actor.System, null),
                        request.owner
                    )
                )
            )
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

        implementOrDispatch(AccountingV2.browseAllocationsInternal) {
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

        implement(AccountingV2.searchSubAllocations) {
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

        implement(AccountingV2.browseSubAllocations) {
            ok(accounting.browseSubAllocations(actorAndProject, request))
        }

        implement(Wallets.retrieveRecipient) {
            ok(accounting.retrieveRecipient(actorAndProject, request))
        }

        implementOrDispatch(AccountingV2.browseProviderAllocations) {
            ok(accounting.retrieveProviderAllocations(actorAndProject, request))
        }

        implement(DepositNotifications.retrieve) {
            ok(notifications.retrieveNotifications(actorAndProject))
        }

        implement(DepositNotifications.markAsRead) {
            notifications.markAsRead(actorAndProject, request)
            ok(Unit)
        }

        implement(VisualizationV2.retrieveCharts) {
            ok(accounting.retrieveChartsV2(ctx.responseAllocator, actorAndProject, request))
        }

        if (micro.developmentModeEnabled) {
            GlobalScope.launch {
                while (true) {
                    val success = runCatching {
                        val appEngine = micro.feature(ServerFeature).ktorApplicationEngine!!
                        appEngine.application.routing {
                            webSocket("/accountingDevConsole") {
                                suspend fun sendMessage(message: String) {
                                    message.lines().forEach {
                                        if (it.isNotBlank()) outgoing.send(Frame.Text(it))
                                    }
                                }

                                sendMessage("Ready to accept queries!")
                                for (frame in incoming) {
                                    if (frame !is Frame.Text) continue

                                    val text = frame.readText().trim()
                                    val split = text.split(" ")
                                    val command = split.firstOrNull()
                                    val args = split.drop(1)

                                    when (command) {
                                        "test-data" -> {
                                            sendMessage("Ready! End with EOF.")
                                            val data = ArrayList<AccountingService.TestDataObject>()
                                            for (innerFrame in incoming) {
                                                if (innerFrame !is Frame.Text) continue
                                                val textFrame = innerFrame.readText().trim()
                                                if (textFrame.equals("EOF", ignoreCase = true)) break
                                                val innerSplit = textFrame.split(" ")

                                                val projectId = innerSplit.getOrNull(0)
                                                val categoryName = innerSplit.getOrNull(1)
                                                val provider = innerSplit.getOrNull(2)
                                                val usage = innerSplit.getOrNull(3)?.toLongOrNull()

                                                if (projectId == null || categoryName == null || provider == null || usage == null) {
                                                    sendMessage("usage: test-data <projectId> <categoryName> <provider> <usage>")
                                                } else {
                                                    data.add(AccountingService.TestDataObject(projectId, categoryName, provider, usage))
                                                }
                                            }

                                            accounting.generateTestData(data)
                                            sendMessage("OK")
                                        }
                                    }
                                }
                            }
                        }
                    }.isSuccess

                    if (success) {
                        break
                    }

                    delay(100)
                }
            }
        }

        return@with
    }

    companion object : Loggable {
        override val log = logger()
    }
}
