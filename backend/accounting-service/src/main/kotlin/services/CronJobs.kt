package dk.sdu.cloud.accounting.services

import dk.sdu.cloud.accounting.Configuration
import dk.sdu.cloud.accounting.Utils.lowResourcesTemplate
import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.Wallet
import dk.sdu.cloud.accounting.api.WalletOwnerType
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.mail.api.MailDescriptions
import dk.sdu.cloud.mail.api.SendBulkRequest
import dk.sdu.cloud.mail.api.SendRequest
import dk.sdu.cloud.project.api.LookupAdminsBulkRequest
import dk.sdu.cloud.project.api.LookupAdminsRequest
import dk.sdu.cloud.project.api.LookupByIdBulkRequest
import dk.sdu.cloud.project.api.ProjectMembers
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.escapeHtml
import io.ktor.http.HttpStatusCode


class CronJobs(
    private val db: DBContext,
    private val serviceClient: AuthenticatedClient,
    private val config: Configuration
) {
    private val LOW_FUNDS_SUBJECT = "Project low on resource"

    suspend fun notifyLowFundsWallets() {
        db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("sent", false)
                        setParameter("limit", config.notificationLimit)
                        setParameter("type", WalletOwnerType.PROJECT.toString())
                    },
                    """
                        DECLARE curs NO SCROLL CURSOR WITH HOLD
                        FOR SELECT * FROM wallets 
                        WHERE low_funds_notifications_send = :sent AND balance < :limit AND account_type = :type
                        GROUP BY account_id, account_type, product_provider, product_category
                    """
                )
            while (true) {
                val rows = session
                    .sendPreparedStatement(
                        {

                        },
                        """
                        FETCH FORWARD 1000 FROM curs
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
                            it.getField(WalletTable.productCategory),
                            it.getField(WalletTable.productProvider)
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
                                    LOW_FUNDS_SUBJECT,
                                    lowResourcesTemplate(
                                        admin.username,
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
                    if (projects[projectAndAdmins.first]?.parent != null) {
                        val parentAdmins = ProjectMembers.lookupAdmins.call(
                            LookupAdminsRequest(projects[projectAndAdmins.first]?.parent!!),
                            serviceClient
                        ).orThrow()
                        parentAdmins.admins.forEach { parentAdmin ->
                            projectWalletsBelowLimit.forEach { wallet ->
                                sendRequests.add(
                                    SendRequest(
                                        parentAdmin.username,
                                        LOW_FUNDS_SUBJECT,
                                        lowResourcesTemplate(
                                            parentAdmin.username,
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
                }
                MailDescriptions.sendBulk.call(
                    SendBulkRequest(sendRequests),
                    serviceClient
                ).orThrow()

            }

            session
                .sendPreparedStatement(
                    {},
                    """
                        CLOSE curs
                    """
                )
            //set sent status
            session
                .sendPreparedStatement(
                    {
                        setParameter("state", true)
                        setParameter("oldstate", false)
                        setParameter("limit", config.notificationLimit)
                    },
                    """
                        UPDATE wallets
                        SET low_funds_notifications_send = :state
                        WHERE low_funds_notifications_send = :oldstate AND balance < :limit
                    """
                )
        }
    }
}
