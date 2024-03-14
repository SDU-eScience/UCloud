package dk.sdu.cloud.accounting.rpc

import dk.sdu.cloud.*
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.services.accounting.AccountingRequest
import dk.sdu.cloud.accounting.services.accounting.AccountingSystem
import dk.sdu.cloud.accounting.services.wallets.DepositNotificationService
import dk.sdu.cloud.accounting.util.IdCardService
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.responseAllocator
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.service.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*

class AccountingController(
    private val accounting: AccountingSystem,
    private val notifications: DepositNotificationService,
    private val idCards: IdCardService,
) : Controller {
    private fun <R : Any, S : Any, E : Any> RpcServer.implementOrDispatch(
        call: CallDescription<R, S, E>,
        handler: suspend CallHandler<R, S, E>.() -> Unit,
    ) {
        implement(call) {
//            val activeProcessor = accounting.retrieveActiveProcessorAddress()
//            if (activeProcessor == null) {
            handler()
//            } else {
//                ok(
//                    call.call(
//                        request,
//                        client.withFixedHost(HostInfo(activeProcessor, "http", 8080))
//                    ).orThrow()
//                )
//            }
        }
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implementOrDispatch(AccountingV2.findRelevantProviders) {
            TODO()
            /*
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
             */
        }

        implementOrDispatch(AccountingV2.reportUsage) {
            val idCard = idCards.fetchIdCard(actorAndProject)
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

        implementOrDispatch(AccountingV2.updateAllocation) {
//            ok(accounting.updateAllocation(actorAndProject, request))
            TODO()
        }

        implementOrDispatch(AccountingV2.rootAllocate) {
            val idCard = idCards.fetchIdCard(actorAndProject)

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

        implement(AccountingV2.browseWallets) {
            val idCard = idCards.fetchIdCard(actorAndProject)
            ok(PageV2.of(
                accounting.sendRequest(
                    AccountingRequest.BrowseWallets(
                        idCard,
                        request.includeChildren,
                        request.childrenQuery,
                        request.filterType,
                    )
                )
            ))
        }

        implementOrDispatch(AccountingV2.browseProviderAllocations) {
//            ok(accounting.retrieveProviderAllocations(actorAndProject, request))
            TODO()
        }

        implement(DepositNotifications.retrieve) {
            ok(notifications.retrieveNotifications(actorAndProject))
        }

        implement(DepositNotifications.markAsRead) {
            notifications.markAsRead(actorAndProject, request)
            ok(Unit)
        }

        implement(VisualizationV2.retrieveCharts) {
            TODO()
        }

        return@with
    }

    companion object : Loggable {
        override val log = logger()
    }
}
