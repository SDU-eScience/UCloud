package dk.sdu.cloud.accounting.services.accounting

import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.accounting.util.IdCardService
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.mail.api.Mail
import dk.sdu.cloud.mail.api.MailDescriptions
import dk.sdu.cloud.mail.api.SendRequestItem
import dk.sdu.cloud.project.api.v2.ProjectsSortBy

class LowFundsJob(
    private val system: AccountingSystem,
    private val idCardService: IdCardService,
    private val authenticatedClient: AuthenticatedClient
) {

    suspend fun checkWallets() {
        println("Checking for low funds in wallets")
        val walletsAndUsers = system.sendRequest(
            AccountingRequest.BrowseLowBalanceWallets(
                IdCard.System
            )
        )

        val mailReceiverToWalletInfo =  mutableMapOf<InternalOwner, MutableSet<InternalWallet>>()
        val walletIds = mutableSetOf<Int>()
        walletsAndUsers.forEach {
            val owner = it.first
            val wallet = it.second
            if (!wallet.lowBalanceNotified) {
                walletIds.add(wallet.id)
                val current = mailReceiverToWalletInfo[owner]
                if (current == null) {
                    mailReceiverToWalletInfo[owner] = mutableSetOf(wallet)
                } else {
                    current.add(wallet)
                    mailReceiverToWalletInfo[owner] = current
                }
            }
        }

        val mailsToSend = mutableListOf<SendRequestItem>()

        mailReceiverToWalletInfo.forEach { (owner, wallets) ->
            val productCategories = wallets.map { it.category }
            val pid = idCardService.lookupPidFromProjectId(owner.reference)
            val projectInfo = if (pid != null ) idCardService.lookupProjectInformation(pid) else null
            val receiver = if (owner.isProject()) {
                if (pid == null) {
                    throw RPCException("Project not found", HttpStatusCode.BadRequest)
                }
                else {
                    projectInfo?.pi ?: throw RPCException("Project not found", HttpStatusCode.BadRequest)
                }
            } else {
                owner.toWalletOwner().reference()
            }


            val info = if (owner.isProject()) projectInfo!!.title else "My Workspace"

            val projectTitles = productCategories.map {
                info
            }

            mailsToSend.add(
                SendRequestItem(
                    receiver,
                    Mail.LowFundsMail(
                        categories = productCategories.map { it.name },
                        providers = productCategories.map { it.provider },
                        projectTitles = projectTitles,
                    ),
                    mandatory = true
                )
            )
        }

        mailsToSend.chunked(1000).forEach { chunk ->
            MailDescriptions.sendToUser.call(
                bulkRequestOf(
                    chunk
                ),
                authenticatedClient
            )
        }

        system.sendRequest(AccountingRequest.LowBalanceNotificationUpdate(
            IdCard.System,
            walletIds.toList()
        ))

        println("Successfully updated wallets")
    }
}