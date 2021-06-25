package dk.sdu.cloud.accounting.db.migration

import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.ProductType
import dk.sdu.cloud.accounting.api.TransactionType
import dk.sdu.cloud.accounting.api.Wallet
import dk.sdu.cloud.service.Cache
import io.ktor.network.sockets.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.sql.Connection
import java.time.LocalDateTime
import java.time.ZoneOffset

const val SERVICE_ID = "_accounting"
const val TRANSACTION_DESCRIPTION = "Migrated from old structure"


class V15__ComplexMigrateAfterRedesign : BaseJavaMigration() {

    data class OldProduct(
        val productProvider: String,
        val productCategory: String,
        val area: String,
        val name: String,
        val price_per_unit: Long?,
        val description: String?,
        val availability: String?,
        val priority: Long?,
        val cpu: Long?,
        val gpu: Long?,
        val memory: Long?,
        val licenseTags: List<String>?,
        val paymentModel: String?,
        val hiddenFromGrant: Boolean
    )

    data class OldWallet(
        val account_id: String,
        val account_type: String,
        val productCategory: String,
        val productProvider: String,
        val balance: Long,
        val low_funds_notification_send: Boolean,
        val allocated: Long,
        val used: Long
    )

    data class OldTransaction(
        val id: String,
        val account_id: String,
        val original_account_id: String,
        val productCategory: String,
        val productProvider: String,
        val productName: String,
        val initiatedBy: String,
        val completedAt: Long,
        val units: Long,
        val amount: Long,
        val isReserved: Boolean,
        val expiresAt: Long,
        val productType: String
    )

    data class GiftGranted(
        val resourcesOwnedBy: String,
        val productCategory: String,
        val productProvider: String,
        val credits: Long,
        val quota: Long?,
        val userGrantedToId: String,
        val productType: String
    )

    private fun migrateProducts(connection: Connection) {
        val products = mutableListOf<OldProduct>()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT provider, category, area, id, price_per_unit, description, availability, priority, cpu, gpu, " +
                    "memory_in_gigs, license_tags, payment_model, hidden_in_grant_applications FROM accounting.products"
            ).use { row ->
                while (row.next()) {
                    val licenses = dk.sdu.cloud.defaultMapper.decodeFromString<List<String>>(row.getString(11))
                    val area = row.getString(2)
                    if (area == ProductType.STORAGE.name) {
                        //Credits
                        products.add(
                            OldProduct(
                                productProvider = row.getString(0)+"_credits",
                                productCategory = row.getString(1),
                                area = area,
                                name = row.getString(3)+"_credits",
                                price_per_unit = row.getLong(4),
                                description = row.getString(5),
                                availability = row.getString(6),
                                priority = row.getLong(7),
                                cpu = row.getLong(8),
                                gpu = row.getLong(9),
                                memory = row.getLong(10),
                                licenseTags = licenses,
                                paymentModel = row.getString(12),
                                hiddenFromGrant = row.getBoolean(13)
                            )
                        )
                        //Quota
                        products.add(
                            OldProduct(
                                productProvider = row.getString(0)+"_quota",
                                productCategory = row.getString(1),
                                area = area,
                                name = row.getString(3)+"_quota",
                                price_per_unit = row.getLong(4),
                                description = row.getString(5),
                                availability = row.getString(6),
                                priority = row.getLong(7),
                                cpu = row.getLong(8),
                                gpu = row.getLong(9),
                                memory = row.getLong(10),
                                licenseTags = licenses,
                                paymentModel = row.getString(12),
                                hiddenFromGrant = row.getBoolean(13)
                            )
                        )
                    } else {
                        products.add(
                            OldProduct(
                                productProvider = row.getString(0),
                                productCategory = row.getString(1),
                                area = area,
                                name = row.getString(3),
                                price_per_unit = row.getLong(4),
                                description = row.getString(5),
                                availability = row.getString(6),
                                priority = row.getLong(7),
                                cpu = row.getLong(8),
                                gpu = row.getLong(9),
                                memory = row.getLong(10),
                                licenseTags = licenses,
                                paymentModel = row.getString(12),
                                hiddenFromGrant = row.getBoolean(13)
                            )
                        )
                    }
                }
            }
        }
        // Find all old products and migrate to new structure.
        connection.createStatement().use { statement ->
            products.forEach { product ->
                val productCategoryId = getCategoryId(connection, product.productProvider, product.productCategory)
                statement.execute(
                    "INSERT INTO accounting2.product (product_category_id, name, price_per_unit, unit_of_price, " +
                        "product_type, description, version, priority, cpu, gpu, memory_in_gigs, license_tags, " +
                        "free_to_use) " +
                        "(${productCategoryId}, ${product.name}, ${product.price_per_unit}, 'PER_MINUTE', " +
                        "${product.area}, ${product.description}, 1, ${product.priority}, ${product.cpu}, ${product.gpu}," +
                        "${product.memory}, ${product.licenseTags}, false)"
                )
            }
            statement.execute(
                "UPDATE accounting2.product " +
                    "SET unit_of_price = 'PER_UNIT'" +
                    "WHERE product_type = 'INGRESS' OR product_type = 'NETWORK_IP'"
            )
            statement.execute(
                "UPDATE accounting2.product " +
                    "SET free_to_use = true " +
                    "WHERE product_type = 'LICENSE'"
            )
        }
    }

    data class AcceptedGrant(
        val resourcesOwnedBy: String,
        val requestedByWorkspace: String,
        val productProvider: String,
        val productCategory: String,
        val creditsRequested: Long,
        val quotaRequestedInBytes: Long,
        val createdAt: Long,
        val applicationId: Long,
        val requestedByUser: String,
        val approvedBy: String,
        val productType: String
    )

    private fun migrateAcceptedGrants(connection: Connection, acceptedGrant: AcceptedGrant ) {
        connection.createStatement().use { statement ->
            var productCategory = if (acceptedGrant.productType == ProductType.STORAGE.name) {
                acceptedGrant.productCategory + "_credits"
            } else {
                acceptedGrant.productCategory
            }
            var categoryId = getCategoryId(connection, acceptedGrant.productProvider, productCategory)
            var productId = getProductId(connection, categoryId)
            val description = "Granted in application: ${acceptedGrant.applicationId}"
            var targetWalletId = getWalletId(connection, acceptedGrant.requestedByWorkspace, categoryId)
            var sourceWalletId = getWalletId(connection, acceptedGrant.resourcesOwnedBy, categoryId)
            val transactionId = statement.execute(
                """
                        INSERT INTO transaction (transaction_type, target_wallet_id, units, number_of_products, 
                        action_performed_by, action_performed_by_wallet, product_id, transfer_from_wallet_id, description) 
                        values (${TransactionType.DEPOSIT.name}, $targetWalletId, ${acceptedGrant.creditsRequested}, 1,
                        ${acceptedGrant.approvedBy}, null, $productId, $sourceWalletId, $description)
                        RETURNING id
                    """.trimIndent()
            )
            statement.execute(
                """
                        INSERT INTO wallet_allocation (balance, initial_balance, start_date, end_date, parent_wallet_id,
                        transaction_id)  values (${acceptedGrant.creditsRequested}, ${acceptedGrant.creditsRequested}, 
                        ${acceptedGrant.createdAt}::timestamp, null, $sourceWalletId, $transactionId)
                    """.trimIndent()
            )
            if (acceptedGrant.productType == ProductType.STORAGE.name) {
                productCategory = acceptedGrant.productCategory + "_quota"
                categoryId = getCategoryId(connection, acceptedGrant.productProvider, productCategory)
                productId = getProductId(connection, categoryId)
                targetWalletId = getWalletId(connection, acceptedGrant.requestedByWorkspace, categoryId)
                sourceWalletId = getWalletId(connection, acceptedGrant.resourcesOwnedBy, categoryId)
                statement.execute(
                    """
                        INSERT INTO transaction (transaction_type, target_wallet_id, units, number_of_products, 
                        action_performed_by, action_performed_by_wallet, product_id, transfer_from_wallet_id, description) 
                        values (${TransactionType.DEPOSIT.name}, $targetWalletId, ${acceptedGrant.creditsRequested}, 1,
                        ${acceptedGrant.approvedBy}, null, $productId, $sourceWalletId, $description)
                        RETURNING id
                    """.trimIndent()
                )
                statement.execute(
                    """
                        INSERT INTO wallet_allocation (balance, initial_balance, start_date, end_date, parent_wallet_id,
                        transaction_id)  values (${acceptedGrant.creditsRequested}, ${acceptedGrant.creditsRequested}, 
                        ${acceptedGrant.createdAt}::timestamp, null, $sourceWalletId, $transactionId)
                    """.trimIndent()
                )
            }
        }
    }

    /*
     * cache = Pair(productProvider, productCategory) -> categoryId
     */
    private val categoryIdCache = mutableMapOf<Pair<String, String>, Long>()

    private fun getCategoryId(connection: Connection, productProvider: String, productCategory: String): Long {
        val id = categoryIdCache[Pair(productProvider, productCategory)]
        if (id == null ) {
            connection.createStatement().use { statement ->
                val categoryId =  statement.executeQuery(
                    "SELECT id FROM accounting2.product_category WHERE provider_id = '${productProvider}' " +
                        "and product_category = ${productCategory}"
                ).use { row ->
                    row.getLong(0)
                }
                categoryIdCache[Pair(productProvider, productCategory)] = categoryId
                return categoryId
            }
        } else {
            return id
        }
    }

    /*
     * cache = ProductCategory -> productId
     */
    private val productIdCache = mutableMapOf<Long, Long>()
    private fun getProductId(connection: Connection, productCategory: Long): Long {
        val id = productIdCache[productCategory]
        if (id == null) {
            connection.createStatement().use { statement ->
                val productId = statement.executeQuery(
                    "SELECT id FROM accounting2.product WHERE product_category_id = $productCategory"
                ).use { row ->
                    row.getLong(0)
                }
                productIdCache[productCategory] = productId
                return productId
            }
        } else {
            return id
        }
    }

    private val oldWalletOwnerIdCache = mutableMapOf<OldWallet, Long>()
    private fun getOldWalletOwner(connection: Connection, wallet: OldWallet): Long {
        val id = oldWalletOwnerIdCache[wallet]
        if (id == null) {
            connection.createStatement().use { statement ->
                val walletOwnerId = statement.executeQuery(
                    "SELECT id FROM accounting2.wallet_owner WHERE project_id = ${wallet.account_id} " +
                        "or username = ${wallet.account_id}"
                ).use { row ->
                    row.getLong(0)
                }
                oldWalletOwnerIdCache[wallet] = walletOwnerId
                return walletOwnerId
            }
        } else {
            return id
        }
    }

    /*
     * Pair<account_id, categoryId> -> walletId
     */
    private val walletIdCache = mutableMapOf<Pair<String, Long>, Long>()
    private fun getWalletId(connection: Connection, accountId: String, categoryId: Long): Long {
        val id = walletIdCache[Pair(accountId, categoryId)]
        if (id == null) {
            connection.createStatement().use { statement ->
                val walletId = statement.executeQuery(
                    "SELECT id FROM accounting2.wallets WHERE owner_id = $accountId and category_id = $categoryId"
                ).use { row ->
                    row.getLong(0)
                }
                walletIdCache[Pair(accountId, categoryId)] = walletId
                return walletId
            }
        } else {
            return id
        }
    }

    override fun migrate(context: Context) {
        val connection = context.connection
        connection.autoCommit = false

        migrateProducts(connection)

        //maps of Pair<accountId, product category> and OldWallet
        val oldWallets = mutableMapOf<Pair<String, String>, OldWallet>()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT account_id, account_type, product_category, product_provider, balance, " +
                    "low_funds_notifications_send, allocated, used FROM accounting.wallets;"
            ).use { row ->
                while (row.next()) {
                    oldWallets[
                        Pair(row.getString(0), row.getString(2))
                    ] = OldWallet(
                        account_id = row.getString(0),
                        account_type = row.getString(1),
                        productCategory = row.getString(2),
                        productProvider = row.getString(3),
                        balance = row.getLong(4),
                        low_funds_notification_send = row.getBoolean(5),
                        allocated = row.getLong(6),
                        used = row.getLong(7)
                    )
                }
            }
        }

        //Migrate Wallets
        connection.createStatement().use { statement ->
            oldWallets.forEach { (_, wallet) ->
                val categoryId = getCategoryId(connection, wallet.productProvider, wallet.productCategory)

                val walletOwnerId = getOldWalletOwner(connection, wallet)

                statement.execute(
                    "INSERT INTO accounting2.wallets (owner_id, allocation_selector_policy," +
                        " category_id, low_funds_notifications_send) " +
                        " $walletOwnerId, 'EXPIRE_FIRST', $categoryId, false"
                )
            }
        }

        data class BaseProjectWallets(
            val projectId: String,
            val title: String,
            val productCategory: String,
            val productProvider: String,
            val balance: Long,
            val allocated: Long,
            val used: Long,
            val createdAt: LocalDateTime,
            val parent: String?
        )
        //Deposit to base projects.
        val baseProjects = mutableListOf<BaseProjectWallets>()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                """SELECT account_id, title, product_category, product_provider, balance, allocated, used, created_at 
                FROM project.projects INNER JOIN accounting.wallets on account_id = projects.id INNER JOIN grant.is_enabled on projects.id = is_enabled.project_id
                ORDER BY parent DESC"""
            ).use { row ->
                baseProjects.add(
                    BaseProjectWallets(
                        projectId = row.getString(0),
                        title = row.getString(1),
                        productCategory = row.getString(2),
                        productProvider = row.getString(3),
                        balance = row.getLong(4),
                        allocated = row.getLong(5),
                        used = row.getLong(6),
                        createdAt = row.getTimestamp(7).toLocalDateTime(),
                        parent = row.getString(8)
                    )
                )
            }
        }

        connection.createStatement().use { statement ->
            baseProjects.forEach {
                val categoryId = getCategoryId(connection, it.productProvider, it.productCategory)
                val productId = getProductId(connection, categoryId)
                val targetWalletId = getWalletId(connection, it.projectId, categoryId)
                val sourceWalletId = if (it.parent == null ) { null } else {getWalletId(connection, it.parent, categoryId)}
                val transactionId = statement.execute(
                    "INSERT INTO transaction (transaction_type, target_wallet_id, units, number_of_products, " +
                        "action_performed_by, action_performed_by_wallet, product_id, transfer_from_wallet_id, description) " +
                        "values (${TransactionType.DEPOSIT.name}, $targetWalletId, ${it.allocated}, 1, " +
                        "$SERVICE_ID, null , $productId, $sourceWalletId, $TRANSACTION_DESCRIPTION )" +
                        "RETURNING id"
                )
                statement.execute(
                    "INSERT INTO wallet_allocation (associated_wallet, balance, initial_balance, start_date, end_date, " +
                        "parent_wallet_id, transaction_id) " +
                        "values ($targetWalletId, ${it.balance}, ${it.allocated}, ${it.createdAt}, null, null, $transactionId)"
                )
            }
        }

        val acceptedProjectGrants = mutableListOf<AcceptedGrant>()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                """
                    WITH productCat AS (
                        SELECT DISTINCT category, area
                        FROM accounting.products
                    )
                    SELECT resources_owned_by, p.id as requested_by, product_provider, product_category, credits_requested, quota_requested_bytes, applications.created_at, area
                    FROM "grant".applications INNER JOIN "grant".requested_resources rr on applications.id = rr.application_id
                        INNER JOIN "project".projects p on p.title = applications.grant_recipient
                        INNER JOIN productCat pro on pro.category = rr.product_category
                    WHERE applications.status = 'APPROVED';
                """.trimIndent()
            ).use { row ->
                while (row.next()) {
                    acceptedProjectGrants.add(
                        AcceptedGrant(
                            resourcesOwnedBy = row.getString(0),
                            requestedByWorkspace = row.getString(1) ,
                            productProvider = row.getString(2) ,
                            productCategory = row.getString(3) ,
                            creditsRequested = row.getLong(4) ,
                            quotaRequestedInBytes = row.getLong(5),
                            createdAt = row.getTimestamp(6).time,
                            applicationId = row.getLong(7),
                            requestedByUser = row.getString(8),
                            approvedBy = row.getString(9),
                            productType = row.getString(10)
                        )
                    )
                }
            }
        }

        val acceptedUserGrants = mutableListOf<AcceptedGrant>()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                """
                    WITH productCat AS (
                        SELECT DISTINCT category, area
                        FROM accounting.products
                    )
                    SELECT resources_owned_by, p.id as requested_by, product_provider, product_category, credits_requested, quota_requested_bytes, applications.created_at, area
                    FROM "grant".applications INNER JOIN "grant".requested_resources rr on applications.id = rr.application_id
                        INNER JOIN "auth".principals p on p.id = applications.grant_recipient
                        INNER JOIN productCat pro on pro.category = rr.product_category
                    WHERE applications.status = 'APPROVED';
                """.trimIndent()
            ).use { row ->
                while (row.next()) {
                    acceptedUserGrants.add(
                        AcceptedGrant(
                            resourcesOwnedBy = row.getString(0),
                            requestedByWorkspace = row.getString(1) ,
                            productProvider = row.getString(2) ,
                            productCategory = row.getString(3) ,
                            creditsRequested = row.getLong(4) ,
                            quotaRequestedInBytes = row.getLong(5),
                            createdAt = row.getTimestamp(6).time,
                            applicationId = row.getLong(7),
                            requestedByUser = row.getString(8),
                            approvedBy = row.getString(9),
                            productType = row.getString(10)
                        )
                    )
                }
            }
        }

        acceptedProjectGrants.forEach { projectGranted ->
            migrateAcceptedGrants(connection, projectGranted)

        acceptedUserGrants.forEach { userGranted ->
            migrateAcceptedGrants(connection, userGranted)
        }


        //Get claimed gifts which should correspond to new deposit transactions.
        val giftsGranted = mutableListOf<GiftGranted>()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                """
                    SELECT resources_owned_by, product_category, product_provider, credits, quota, user_id 
                    FROM "grant".gifts INNER JOIN "grant".gift_resources gr on gifts.id = gr.gift_id 
                    INNER JOIN "grant".gifts_claimed gc on gifts.id = gc.gift_id 
                    ORDER BY  user_id
                """.trimMargin()
            ).use { row ->

                while (row.next()) {
                    giftsGranted.add(
                        GiftGranted(
                            resourcesOwnedBy = row.getString(0),
                            productCategory = row.getString(1),
                            productProvider = row.getString(2),
                            credits = row.getLong(3),
                            quota = row.getLong(4),
                            userGrantedToId = row.getString(5),
                            productType = row.getString(6)
                        )
                    )
                }
            }
        }


        val payedTransactions = mutableListOf<OldTransaction>()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                """
                    WITH productCat AS (
                        SELECT DISTINCT category, area
                        FROM accounting.products
                    )
                    SELECT id, account_id, original_account_id, product_category, product_provider, product_id,
                    initiated_by, completed_at, units, amount, is_reserved, expires_at,
                    transaction_comment, area 
                    FROM accounting.transactions INNER JOIN productCat pc on product_category = pc.category
                    WHERE transaction_comment not like 'Trans%'
                """
            ).use { row ->
                while (row.next()) {
                    payedTransactions.add(OldTransaction(
                        id = row.getString(0),
                        account_id = row.getString(1),
                        original_account_id = row.getString(2),
                        productCategory = row.getString(3),
                        productProvider = row.getString(4),
                        productName = row.getString(5),
                        initiatedBy = row.getString(6),
                        completedAt = LocalDateTime.parse(row.getString(7)).toEpochSecond(ZoneOffset.UTC),
                        units = row.getLong(8),
                        amount = row.getLong(9),
                        isReserved = row.getBoolean(10),
                        expiresAt = LocalDateTime.parse(row.getString(11)).toEpochSecond(ZoneOffset.UTC),
                        productType = row.getString(12)
                    ))
                }
            }
        }

    }
}


