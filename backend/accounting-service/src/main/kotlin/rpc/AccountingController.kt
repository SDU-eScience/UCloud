package dk.sdu.cloud.accounting.rpc

import dk.sdu.cloud.*
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.services.accounting.AccountingRequest
import dk.sdu.cloud.accounting.services.accounting.AccountingSystem
import dk.sdu.cloud.accounting.services.accounting.DataVisualization
import dk.sdu.cloud.accounting.services.grants.GrantsV2Service
import dk.sdu.cloud.accounting.services.notifications.ApmNotificationService
import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.accounting.util.IdCardService
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.ServerFeature
import dk.sdu.cloud.micro.feature
import dk.sdu.cloud.service.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*

class AccountingController(
    private val micro: Micro,
    private val accounting: AccountingSystem,
    private val dataVisualization: DataVisualization,
    private val idCards: IdCardService,
    private val apmNotifications: ApmNotificationService,
    private val grants: GrantsV2Service,
) : Controller {
    private fun <R : Any, S : Any, E : Any> RpcServer.implementOrDispatch(
        call: CallDescription<R, S, E>,
        handler: suspend CallHandler<R, S, E>.() -> Unit,
    ) {
        implement(call) {
            val activeProcessor = accounting.retrieveActiveProcessor()
            if (activeProcessor == null) {
                handler()
            } else {
                // TODO(Dan): There is no current way of doing this correctly. Please keep in mind that
                //  PersonalProviderProjects currently also depends on a single APM instance.
                error("not yet implemented")
            }
        }
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implementOrDispatch(AccountingV2.findRelevantProviders) {
            val idCard = idCards.fetchIdCard(actorAndProject)
            val responses = request.items.map { req ->
                accounting.sendRequest(
                    AccountingRequest.FindRelevantProviders(
                        idCard,
                        req.username,
                        req.project,
                        req.useProject,
                        req.filterProductType,
                        req.includeFreeToUse,
                    )
                ).let { AccountingV2.FindRelevantProviders.Response(it.toList()) }
            }
            ok(BulkResponse(responses))
        }

        implementOrDispatch(AccountingV2.findAllProviders) {
            val idCard = idCards.fetchIdCard(actorAndProject)
            val responses = request.items.map { req ->
                accounting.sendRequest(
                    AccountingRequest.FindAllProviders(
                        idCard,
                        req.filterProductType,
                        req.includeFreeToUse,
                    )
                ).let { AccountingV2.FindAllProviders.Response(it.toList()) }
            }

            ok(BulkResponse(responses))
        }

        implementOrDispatch(AccountingV2.reportUsage) {
            val isService = (actorAndProject.actor as? Actor.User)?.principal?.role == Role.SERVICE
            val idCard = if (isService) IdCard.System else idCards.fetchIdCard(actorAndProject)
            val shouldContinue = ArrayList<Boolean>()
            for (req in request.items) {
                shouldContinue.add(
                    accounting.sendRequestNoUnwrap(
                        AccountingRequest.Charge(
                            idCard,
                            req.owner.reference(),
                            req.categoryIdV2,
                            req.usage,
                            req.isDeltaCharge,
                            req.description.scope,
                            req.description.description,
                        )
                    ) == null
                )
            }

            ok(BulkResponse(shouldContinue))
        }

        implementOrDispatch(AccountingV2.checkProviderUsable) {
            val isService = (actorAndProject.actor as? Actor.User)?.principal?.role == Role.SERVICE
            val idCard = if (isService) IdCard.System else idCards.fetchIdCard(actorAndProject)
            val response = ArrayList<AccountingV2.CheckProviderUsable.ResponseItem>()
            for (req in request.items) {
                response.add(
                    AccountingV2.CheckProviderUsable.ResponseItem(
                        accounting.sendRequest(
                            AccountingRequest.ProviderCheckUsable(
                                idCard,
                                req.category,
                                req.owner.reference(),
                            )
                        )
                    )
                )
            }
            ok(BulkResponse(response))
        }

        implementOrDispatch(AccountingV2.retrieveScopedUsage) {
            val isService = (actorAndProject.actor as? Actor.User)?.principal?.role == Role.SERVICE
            val idCard = if (isService) IdCard.System else throw RPCException("Forbidden", HttpStatusCode.Forbidden)
            val response = ArrayList<AccountingV2.RetrieveScopedUsage.ResponseItem>()
            for (req in request.items) {
                response.add(
                    AccountingV2.RetrieveScopedUsage.ResponseItem(
                        accounting.sendRequest(
                            AccountingRequest.RetrieveScopedUsage(
                                idCard,
                                req.owner,
                                req.chargeId,
                            )
                        )
                    )
                )
            }
            ok(BulkResponse(response))
        }

        implementOrDispatch(AccountingV2.updateAllocation) {
            val idCard = idCards.fetchIdCard(actorAndProject)
            for (req in request.items) {
                accounting.sendRequest(
                    AccountingRequest.UpdateAllocation(
                        idCard = idCard,
                        allocationId = req.allocationId.toInt(),
                        newQuota = req.newQuota,
                        newStart = req.newStart,
                        newEnd = req.newEnd,
                    )
                )
            }

            ok(Unit)
        }

        implementOrDispatch(AccountingV2.rootAllocate) {
            val idCard = idCards.fetchIdCard(actorAndProject, allowCached = false)

            val allocationIds = ArrayList<FindByStringId>()
            for (req in request.items) {
                allocationIds.add(
                    accounting.sendRequest(
                        AccountingRequest.RootAllocate(
                            idCard,
                            req.category,
                            req.quota,
                            req.start,
                            req.end
                        )
                    ).let { FindByStringId(it.toString()) }
                )
            }

            ok(BulkResponse(allocationIds))
        }

        implementOrDispatch(AccountingV2.browseWallets) {
            val idCard = idCards.fetchIdCard(actorAndProject)
            ok(
                PageV2.of(
                    accounting.sendRequest(
                        AccountingRequest.BrowseWallets(
                            idCard,
                            request.includeChildren,
                            request.childrenQuery,
                            request.filterType,
                        )
                    )
                )
            )
        }

        implementOrDispatch(AccountingV2.browseWalletsInternal) {
            val idCard = when (val owner = request.owner) {
                is WalletOwner.User -> {
                    idCards.fetchIdCard(ActorAndProject(Actor.SystemOnBehalfOfUser(owner.username), null))
                }

                is WalletOwner.Project -> {
                    val pid = idCards.lookupPidFromProjectId(owner.projectId)
                        ?: throw RPCException("Unknown project: ${owner.projectId}", HttpStatusCode.NotFound)
                    IdCard.User(0, IntArray(0), IntArray(pid), pid)
                }
            }

            ok(
                AccountingV2.BrowseWalletsInternal.Response(
                    accounting.sendRequest(
                        AccountingRequest.BrowseWallets(
                            idCard,
                        )
                    )
                )
            )
        }

        implementOrDispatch(AccountingV2.browseProviderAllocations) {
            val idCard = idCards.fetchIdCard(actorAndProject)
            val withoutGrantInformation = accounting.sendRequest(
                AccountingRequest.RetrieveProviderAllocations(
                    idCard,
                    request.itemsPerPage,
                    request.next,
                    request.consistency,
                    request.itemsToSkip,
                    request.filterOwnerId,
                    request.filterOwnerIsProject,
                    request.filterCategory,
                )
            )

            val grantIds = withoutGrantInformation.items.mapNotNull { it.grant?.grantId }.toSet().toList()
            val infoByGrant = grants.retrieveGrantInformation(grantIds).associateBy { it.grantId }
            val newItems = withoutGrantInformation.items.map { item ->
                item.copy(
                    grant = item.grant?.let { infoByGrant[it.grantId] }
                )
            }

            ok(withoutGrantInformation.copy(items = newItems))
        }

        implement(VisualizationV2.retrieveCharts) {
            ok(dataVisualization.retrieveChartsV2(idCards.fetchIdCard(actorAndProject), request))
        }

        implementOrDispatch(AccountingV2.adminDebug) {
            val graph = accounting.sendRequest(
                AccountingRequest.DebugState(
                    IdCard.System,
                    listOf(request.walletId)
                )
            )

            val internalState = accounting.sendRequest(
                AccountingRequest.DebugWallet(
                    IdCard.System,
                    request.walletId
                )
            )

            ok(AccountingV2.AdminDebug.Response(graph, internalState))
        }

        implementOrDispatch(AccountingV2.adminCharge) {
            val error = accounting.sendRequestNoUnwrap(
                AccountingRequest.DebugCharge(
                    IdCard.System,
                    request.walletId,
                    request.amount,
                    request.isDeltaCharge
                )
            )

            ok(AccountingV2.AdminCharge.Response(error))
        }

        implementOrDispatch(AccountingV2.registerProviderGift) {
            for (reqItem in request.items) {
                accounting.sendRequestNoUnwrap(
                    AccountingRequest.RegisterProviderGift(
                        idCards.fetchIdCard(actorAndProject),
                        reqItem.ownerUsername,
                        reqItem.category,
                        reqItem.quota,
                        reqItem.expiresAt,
                    )
                )
            }

            ok(Unit)
        }

        return@with
    }

    fun onKtorReady() {
        val ktor = micro.feature(ServerFeature).ktorApplicationEngine?.application ?: return
        runCatching {
            ktor.install(WebSockets) {
                pingPeriod = null
            }
        }

        ktor.routing {
            webSocket(ApmNotifications.PATH) {
                apmNotifications.handleClient(this)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
