package dk.sdu.cloud.cli

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.DepositNotifications
import dk.sdu.cloud.accounting.api.DepositNotificationsMarkAsReadRequestItem
import dk.sdu.cloud.accounting.api.ProductPriceUnit
import dk.sdu.cloud.accounting.api.ProductType
import dk.sdu.cloud.accounting.api.ProviderWalletSummary
import dk.sdu.cloud.accounting.api.Wallet
import dk.sdu.cloud.accounting.api.WalletOwner
import dk.sdu.cloud.accounting.api.Wallets
import dk.sdu.cloud.accounting.api.WalletsRetrieveProviderSummaryRequest
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.plugins.ipcServer
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.v2.Projects
import dk.sdu.cloud.project.api.v2.ProjectsRetrieveRequest
import dk.sdu.cloud.utils.gmtTime
import dk.sdu.cloud.utils.sendTerminalFrame
import dk.sdu.cloud.utils.sendTerminalMessage
import dk.sdu.cloud.utils.sendTerminalSeparator
import dk.sdu.cloud.utils.sendTerminalTable
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

fun GrantCli(controllerContext: ControllerContext) {
    val pluginContext = controllerContext.pluginContext
    val config = pluginContext.config
    pluginContext.commandLineInterface?.addHandler(CliHandler("grants") { args ->
        fun sendHelp(): Nothing = sendCommandLineUsage("grants", "Tools to review grant allocations for projects") {
            subcommand("ls", "Retrieves a list of grants which has not yet been acknowledged by the provider")
            subcommand("acknowledge", "Acknowledges one or more grants removing them from the output of list") {
                arg("projectId", description = "The project ID to acknowledge grants from")
            }
        }

        val ipcClient = pluginContext.ipcClient
        genericCommandLineHandler {
            when (args.getOrNull(0)) {
                "ls" -> {
                    val grants = ipcClient.sendRequest(GrantsIpc.retrieve, Unit).items

                    if (!isParsable) {
                        if (grants.isEmpty()) {
                            sendTerminalMessage { line("No allocations found") }
                        }

                        for (grant in grants) {
                            sendTerminalFrame {
                                title("Allocations belonging to ${grant.projectTitle}", wide = true)

                                field("ID", grant.projectId)
                                field("Path", grant.projectPath)
                                field("Title", grant.projectTitle)
                                field("PI username", grant.piUsername)
                                field("PI email", grant.piEmail)
                            }

                            sendTerminalSeparator()

                            sendTerminalTable {
                                header("Category", 30)
                                header("Balance", 30)
                                header("Not before", 30)
                                header("Not after", 30)

                                for (alloc in grant.allocations) {
                                    nextRow()
                                    cell(alloc.category)
                                    cell(Wallet.explainBalance(alloc.balance, alloc.type, alloc.unitOfPrice))
                                    cell(gmtTime(alloc.notBefore).simpleString(includeTimeOfDay = false))
                                    cell(
                                        if (alloc.notAfter == null) "Never"
                                        else gmtTime(alloc.notAfter).simpleString(includeTimeOfDay = false)
                                    )
                                }
                            }

                            sendTerminalSeparator(size = 3)
                        }
                    } else {
                        sendTerminalTable {
                            header("Project ID", 30)
                            header("Project path", 30)
                            header("Project title", 30)
                            header("PI username", 30)
                            header("PI email", 30)

                            header("Category", 30)
                            header("Balance", 30)
                            header("Not before", 30)
                            header("Not after", 30)

                            for (grant in grants) {
                                for (alloc in grant.allocations) {
                                    nextRow()
                                    cell(grant.projectId)
                                    cell(grant.projectPath)
                                    cell(grant.projectTitle)
                                    cell(grant.piUsername)
                                    cell(grant.piEmail)

                                    cell(alloc.category)
                                    cell(alloc.balance)
                                    cell(alloc.notBefore)
                                    cell(alloc.notAfter)
                                }
                            }
                        }
                    }
                }

                "acknowledge" -> {
                    val projectId = args.getOrNull(1) ?: sendHelp()
                    ipcClient.sendRequest(GrantsIpc.acknowledge, FindByStringId(projectId))
                    sendTerminalMessage { bold { green { line("Success!") } } }
                }

                else -> sendHelp()
            }
        }
    })

    if (config.shouldRunServerCode()) {
        val rpcClient = pluginContext.rpcClient
        val ipcServer = pluginContext.ipcServer

        ipcServer.addHandler(GrantsIpc.retrieve.handler { user, _ ->
            if (user.uid != 0) throw RPCException("Root is required for this script", HttpStatusCode.Forbidden)
            val items = retrieveSimpleGrants(rpcClient)
            PageV2(items.size, items, next = null)
        })

        ipcServer.addHandler(GrantsIpc.acknowledge.handler { user, request ->
            if (user.uid != 0) throw RPCException("Root is required for this script", HttpStatusCode.Forbidden)

            val batch = DepositNotifications.retrieve.call(Unit, rpcClient).orThrow()
            val responses = batch.responses.filter {
                val owner = it.owner
                owner is WalletOwner.Project && owner.projectId == request.id
            }.takeIf { it.isNotEmpty() } ?: throw RPCException(
                "No such project. You should specify the UCloud project ID in the request!",
                HttpStatusCode.BadRequest
            )

            DepositNotifications.markAsRead.call(
                BulkRequest(responses.map { response -> DepositNotificationsMarkAsReadRequestItem(response.id) }),
                rpcClient
            ).orThrow()
        })
    }
}

private suspend fun retrieveSimpleGrants(rpcClient: AuthenticatedClient): List<SimplifiedGrant> {
    val batch = DepositNotifications.retrieve.call(Unit, rpcClient).orThrow()
    if (batch.responses.isEmpty()) return emptyList()

    val walletSummaryByOwner = HashMap<WalletOwner, ArrayList<ProviderWalletSummary>>()
    for (notification in batch.responses) {
        val combinedProviderSummary = Wallets.retrieveProviderSummary.call(
            WalletsRetrieveProviderSummaryRequest(
                filterOwnerId = when (val owner = notification.owner) {
                    is WalletOwner.User -> owner.username
                    is WalletOwner.Project -> owner.projectId
                },
                filterOwnerIsProject = notification.owner is WalletOwner.Project,
                filterCategory = notification.category.name
            ),
            rpcClient
        ).orThrow().items

        for (providerSummary in combinedProviderSummary) {
            walletSummaryByOwner.getOrPut(providerSummary.owner) { ArrayList() }.add(providerSummary)
        }
    }

    val result = ArrayList<SimplifiedGrant>()
    for ((owner, allocations) in walletSummaryByOwner) {
        if (owner !is WalletOwner.Project) continue

        val projectInfo = Projects.retrieve.call(
            ProjectsRetrieveRequest(owner.projectId, includeMembers = true, includePath = true),
            rpcClient
        ).orThrow()

        val piMember = projectInfo.status.members!!.find { it.role == ProjectRole.PI } ?: continue

        result.add(
            SimplifiedGrant(
                projectInfo.id,
                projectInfo.specification.title,
                projectInfo.status.path!!,
                piMember.username,
                piMember.email,
                allocations.map { alloc ->
                    SimplifiedGrant.Allocation(
                        alloc.categoryId.name,
                        alloc.maxUsableBalance,
                        alloc.productType,
                        alloc.unitOfPrice,
                        alloc.notBefore,
                        alloc.notAfter,
                    )
                }
            )
        )
    }

    return result
}

@Serializable
private data class SimplifiedGrant(
    val projectId: String,
    val projectTitle: String,
    val projectPath: String,
    val piUsername: String,
    val piEmail: String?,
    val allocations: List<Allocation>
) {
    @Serializable
    data class Allocation(
        val category: String,
        val balance: Long,
        val type: ProductType,
        val unitOfPrice: ProductPriceUnit,
        val notBefore: Long,
        val notAfter: Long?
    )
}

private object GrantsIpc : IpcContainer("grants") {
    val retrieve = retrieveHandler(Unit.serializer(), PageV2.serializer(SimplifiedGrant.serializer()))
    val acknowledge = updateHandler("acknowledge", FindByStringId.serializer(), Unit.serializer())
}
