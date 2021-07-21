package dk.sdu.cloud.accounting.services.wallets

import dk.sdu.cloud.accounting.Configuration
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.service.db.async.DBContext

class LowFundsJob(
    private val db: DBContext,
    private val serviceClient: AuthenticatedClient,
    private val config: Configuration
) {
    /*
    fun notifyLowFundsWallets(): Unit = runBlocking {
        db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("sent", false)
                        setParameter("limit", config.notificationLimit)
                        setParameter("type", WalletOwnerType.PROJECT.toString())
                    },
                    """
                        declare curs no scroll cursor with hold for 
                        select w.*, pc.category, pc.provider 
                        from
                            accounting.wallets w join
                            accounting.product_categories pc on w.category = pc.id
                        where
                            low_funds_notifications_send = :sent and 
                            balance < :limit and 
                            account_type = :type
                        group by account_id, account_type, pc.category, pc.provider
                    """
                )
            while (true) {
                val rows = session
                    .sendPreparedStatement(
                        //language=sql
                        """
                        fetch forward 1000 from curs
                        """
                    ).rows

                if (rows.isEmpty()) {
                    break
                }

                val projectIDs = rows.map { it.getField(WalletTable.accountId) }.toSet().toList()
                val projects = Projects.lookupByIdBulk.call(
                    LookupByIdBulkRequest(projectIDs),
                    serviceClient
                ).orThrow().associateBy { it.id }

                val adminsAndPI = ProjectMembers.lookupAdminsBulk.call(
                    LookupAdminsBulkRequest(projectIDs),
                    serviceClient
                ).orThrow()
                val wallets = rows.map {
                    Wallet(
                        it.getField(WalletTable.accountId),
                        WalletOwnerType.valueOf(it.getField(WalletTable.accountType)),
                        ProductCategoryId(
                            it.getField(ProductCategoryTable.category),
                            it.getField(ProductCategoryTable.provider)
                        )
                    )
                }

                val sendRequests = mutableListOf<SendRequest>()

                //For each project send a mail for each wallet below limit to each admin
                adminsAndPI.admins.forEach { projectAndAdmins ->
                    val projectWalletsBelowLimit = wallets.filter { it.id == projectAndAdmins.first }
                    projectAndAdmins.second.forEach { admin ->
                        projectWalletsBelowLimit.forEach { wallet ->
                            sendRequests.add(
                                SendRequest(
                                    admin.username,
                                    Mail.LowFundsMail(
                                        wallet.paysFor.id,
                                        wallet.paysFor.provider,
                                        projects[projectAndAdmins.first]?.title ?: throw RPCException.fromStatusCode(
                                            HttpStatusCode.InternalServerError,
                                            "No project found"
                                        )
                                    )
                                )
                            )
                        }
                    }
                }
                try {
                    MailDescriptions.sendBulk.call(
                        SendBulkRequest(sendRequests),
                        serviceClient
                    ).orThrow()
                } catch (ex: Exception) {
                    log.info("Exception caught: ${ex.stackTraceToString()}")
                    log.info("Continuing")
                }

            }

            session
                .sendPreparedStatement(
                    //language=sql
                    """
                        close curs
                    """
                )
            //set sent status
            session
                .sendPreparedStatement(
                    {
                        setParameter("limit", config.notificationLimit)
                    },
                    """
                        update accounting.wallets
                        set low_funds_notifications_send = true
                        where low_funds_notifications_send = false and balance < :limit
                    """
                )
        }
        Unit
    }

    companion object : Loggable {
        override val log = logger()
    }
     */
}
