package dk.sdu.cloud.accounting.services.serviceJobs

import dk.sdu.cloud.accounting.Configuration
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.mail.api.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*

class LowFundsJob(
    private val db: DBContext,
    private val serviceClient: AuthenticatedClient,
    private val config: Configuration
) {
    suspend fun checkWallets() {
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("computeCreditsLimit", config.computeCreditsNotificationLimit)
                    setParameter("storageCreditsLimit", config.storageCreditsNotificationLimit)
                    setParameter("computeUnitsLimit", config.computeUnitsNotificationLimit)
                    setParameter("storageQuotaLimit", config.storageQuotaNotificationLimitInGB)
                    setParameter("storageUnitsLimit", config.storageUnitsNotificationLimitInGB)
                },
                """
                    select accounting.low_funds_wallets(
                        'curs',
                        :computeCreditsLimit,
                        :storageCreditsLimit,
                        :computeUnitsLimit,
                        :storageQuotaLimit,
                        :storageUnitsLimit
                    )
                """
            )

            class LowBalanceWallet(
                val walletId: Long,
                val username: String,
                val projectId: String?,
                val projectTitle: String?,
                val email: String,
                val provider: String,
                val category: String,
                val productType: String,
                val chargeType: String,
                val unitOfPrice: String,
                val freeToUse: Boolean,
                val notificationSent: Boolean,
                val balance: Long
            )

            //TODO is this a good idea or can it become to big in a single run?
            val userAndWallets = mutableMapOf<String, List<LowBalanceWallet>>()

            while (true) {
                val rows = session
                    .sendPreparedStatement(
                        """
                            FETCH FORWARD 1000 FROM curs  
                        """
                    ).rows

                if (rows.isEmpty()) {
                    break
                }

                val walletsToNotify = rows.map {
                    LowBalanceWallet(
                        walletId = it.getLong(0)!!,
                        username = it.getString(1)!!,
                        projectId = it.getString(2),
                        projectTitle = it.getString(3),
                        email = it.getString(4)!!,
                        provider = it.getString(5)!!,
                        category = it.getString(6)!!,
                        productType = it.getString(7)!!,
                        chargeType = it.getString(8)!!,
                        unitOfPrice = it.getString(9)!!,
                        freeToUse = it.getBoolean(10)!!,
                        notificationSent = it.getBoolean(11)!!,
                        balance = it.getLong(12)!!
                    )
                }

                walletsToNotify.forEach { wallet ->
                    val currentEntry = userAndWallets[wallet.username]
                    if (currentEntry == null) {
                        userAndWallets[wallet.username] = listOf(wallet)
                    }
                    else {
                        userAndWallets[wallet.username] = listOf(wallet) + currentEntry
                    }
                }

            }

            val sendRequests = mutableListOf<SendRequestItem>()
            userAndWallets.forEach { (user, walletList) ->
                val categories = walletList.map { it.category }
                val providers = walletList.map { it.provider }
                val projectIds = walletList.map { it.projectTitle }
                sendRequests.add(
                    SendRequestItem(
                        user,
                        Mail.LowFundsMail(
                            categories,
                            providers,
                            projectIds
                        ),
                        receivingEmail = walletList.firstOrNull()?.email
                    )
                )
            }

            sendRequests.chunked(100).forEach {
                try {
                    MailDescriptions.sendToUser.call(
                        bulkRequestOf(it),
                        serviceClient
                    ).orThrow()
                } catch(ex: Exception) {
                    log.info("Exception caught: ${ex.stackTraceToString()}")
                    log.info("Continuing")
                }
            }

            session.sendPreparedStatement(
                """
                    CLOSE curs
                """
            )

            val walletIds = mutableSetOf<Long>()
            userAndWallets.forEach { user, wallet ->
                wallet.forEach {
                    walletIds.add(it.walletId)
                }
            }

            session.sendPreparedStatement(
                {
                    setParameter("ids", walletIds.toList())
                },
                """
                    UPDATE accounting.wallets
                    SET low_funds_notifications_send = true
                    WHERE id in (select * from unnest(:ids::bigint[]))
                """
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
