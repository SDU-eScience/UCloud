package dk.sdu.cloud.accounting.services.products

import com.github.jasync.sql.db.RowData
import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.services.wallets.BalanceService
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

object ProductCategoryTable : SQLTable("accounting.product_categories") {
    val provider = text("provider", notNull = true)
    val category = text("category", notNull = true)
    val area = text("area", notNull = true)
}

object ProductTable : SQLTable("accounting.products") {
    val provider = text("provider", notNull = true)
    val category = text("category", notNull = true)
    val area = text("area", notNull = true)

    val pricePerUnit = long("price_per_unit", notNull = true)
    val id = text("id", notNull = true)
    val description = text("description", notNull = true)
    val availability = text("availability", notNull = false)
    val priority = int("priority", notNull = true)
    val hiddenInGrantApplications = bool("hidden_in_grant_applications", notNull = true)

    val cpu = int("cpu", notNull = false)
    val gpu = int("gpu", notNull = false)
    val memoryInGigs = int("memory_in_gigs", notNull = false)

    val licenseTags = jsonb("license_tags", notNull = false)

    val paymentModel = text("payment_model", notNull = false)
}

class ProductService(
    private val balanceService: BalanceService,
) {
    suspend fun create(
        ctx: DBContext,
        actor: Actor,
        product: Product
    ) {
        ctx.withSession { session ->
            requirePermission(session, actor, product.category.provider, readOnly = false)
            createProductCategoryIfNotExists(session, product.category.provider, product.category.id, product.area)
            try {
                session.insert(ProductTable) {
                    set(ProductTable.provider, product.category.provider)
                    set(ProductTable.category, product.category.id)
                    set(ProductTable.area, product.area.name)
                    set(ProductTable.pricePerUnit, product.pricePerUnit)
                    set(ProductTable.id, product.id)
                    set(ProductTable.description, product.description)
                    set(ProductTable.hiddenInGrantApplications, product.hiddenInGrantApplications)
                    set(ProductTable.priority, product.priority)
                    when (val availability = product.availability) {
                        is ProductAvailability.Available -> {
                            set(ProductTable.availability, null)
                        }

                        is ProductAvailability.Unavailable -> {
                            set(ProductTable.availability, availability.reason)
                        }
                    }

                    when (product) {
                        is Product.Storage -> {
                            // No more attributes
                        }

                        is Product.Compute -> {
                            set(ProductTable.cpu, product.cpu)
                            set(ProductTable.gpu, product.gpu)
                            set(ProductTable.memoryInGigs, product.memoryInGigs)
                        }

                        is Product.License -> {
                            set(ProductTable.licenseTags, defaultMapper.encodeToString(product.tags))
                            set(ProductTable.paymentModel, product.paymentModel.name)
                        }

                        is Product.Ingress -> {
                            set(ProductTable.paymentModel, product.paymentModel.name)
                        }

                        is Product.NetworkIP -> {
                            set(ProductTable.paymentModel, product.paymentModel.name)
                        }
                    }
                    set(ProductTable.pricePerUnit, product.pricePerUnit)
                }
            } catch (ex: GenericDatabaseException) {
                if (ex.errorCode == PostgresErrorCodes.UNIQUE_VIOLATION) {
                    throw RPCException.fromStatusCode(HttpStatusCode.Conflict)
                }
                throw ex
            }
        }
    }

    suspend fun update(
        ctx: DBContext,
        actor: Actor,
        product: Product
    ) {
        ctx.withSession { session ->
            requirePermission(session, actor, product.category.provider, readOnly = false)

            val productRow = findProductCategory(session, product.category.provider, product.category.id)
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            if (productRow.getField(ProductCategoryTable.area).let { ProductArea.valueOf(it) } != product.area) {
                throw RPCException("Product cannot change category", HttpStatusCode.BadRequest)
            }

            val success = session
                .sendPreparedStatement(
                    {
                        setParameter("provider", product.category.provider)
                        setParameter("category", product.category.id)
                        setParameter("pricePerUnit", product.pricePerUnit)
                        setParameter("id", product.id)
                        setParameter("description", product.description)
                        setParameter("hiddenInGrantApplications", product.hiddenInGrantApplications)
                        setParameter(
                            "availability", when (val availability = product.availability) {
                                is ProductAvailability.Available -> null
                                is ProductAvailability.Unavailable -> availability.reason
                                else -> error("unknown availability")
                            }
                        )
                        setParameter(
                            "cpu", when (product) {
                                is Product.Compute -> product.cpu
                                else -> null
                            }
                        )
                        setParameter(
                            "gpu", when (product) {
                                is Product.Compute -> product.gpu
                                else -> null
                            }
                        )
                        setParameter(
                            "memoryInGigs", when (product) {
                                is Product.Compute -> product.memoryInGigs
                                else -> null
                            }
                        )
                        setParameter(
                            "tags", when (product) {
                                is Product.License -> defaultMapper.encodeToString(product.tags)
                                else -> null
                            }
                        )
                        setParameter("paymentModel", when (product) {
                            is Product.License -> product.paymentModel.name
                            is Product.Ingress -> product.paymentModel.name
                            else -> null
                        })
                    },

                    """
                        update accounting.products 
                        set
                            price_per_unit = :pricePerUnit,
                            description = :description,
                            hidden_in_grant_applications = :hiddenInGrantApplications,
                            availability = :availability,
                            cpu = :cpu,
                            gpu = :gpu,
                            memory_in_gigs = :memoryInGigs,
                            license_tags = :tags::jsonb,
                            payment_model = :paymentModel::text
                        where 
                            provider = :provider and 
                            category = :category and 
                            id = :id
                    """
                )
                .rowsAffected > 0L

            if (!success) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }

    suspend fun find(
        ctx: DBContext,
        actor: Actor,
        product: FindProductRequest
    ): Product {
        return ctx.withSession { session ->
            requirePermission(session, actor, product.provider, readOnly = true)

            session
                .sendPreparedStatement(
                    {
                        setParameter("category", product.productCategory)
                        setParameter("provider", product.provider)
                        setParameter("id", product.product)
                    },
                    """
                        select *
                        from accounting.products
                        where
                            category = :category and
                            provider = :provider and
                            id = :id
                    """
                )
                .rows
                .singleOrNull()
                ?.toProduct()
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }

    suspend fun listAllByProvider(
        ctx: DBContext,
        actor: Actor,
        provider: String,
        showHidden: Boolean
    ): List<Product> {
        return ctx.withSession { session ->
            requirePermission(session, actor, provider, readOnly = true)

            session
                .sendPreparedStatement(
                    {
                        setParameter("provider", provider)
                        setParameter("showHidden", showHidden)
                    },
                    """
                        select * from accounting.products
                        where provider = :provider and
                            (hidden_in_grant_applications is false or :showHidden is true)
                        order by priority, id
                    """
                )
                .rows
                .map { it.toProduct() }
        }
    }

    suspend fun list(
        ctx: DBContext,
        actor: Actor,
        provider: String,
        paging: NormalizedPaginationRequest
    ): Page<Product> {
        return ctx.withSession { session ->
            requirePermission(session, actor, provider, readOnly = true)

            session
                .paginatedQuery(
                    paging,
                    {
                        setParameter("provider", provider)
                    },
                    """
                        from accounting.products
                        where provider = :provider
                    """,
                    "order by priority, id"
                )
                .mapItems { it.toProduct() }
        }
    }

    suspend fun listByArea(
        ctx: DBContext,
        actor: Actor,
        area: ProductArea,
        provider: String,
        paging: NormalizedPaginationRequest,
        showHidden: Boolean
    ): Page<Product> {
        return ctx.withSession { session ->
            requirePermission(session, actor, provider, readOnly = true)

            session
                .paginatedQuery(
                    paging,
                    {
                        setParameter("provider", provider)
                        setParameter("area", area.name)
                        setParameter("showHidden", showHidden)
                    },
                    """
                        from accounting.products
                        where provider = :provider and area = :area and
                            (hidden_in_grant_applications is false or :showHidden is true)
                    """,
                    "order by priority, id"
                )
                .mapItems { it.toProduct() }
        }
    }

    suspend fun browse(
        ctx: AsyncDBSessionFactory,
        actor: Actor,
        project: String?,
        request: ProductsBrowseRequest
    ): PageV2<Product> {
        balanceService.requirePermissionToReadBalance(
            actor,
            project ?: actor.safeUsername(),
            if (project != null) WalletOwnerType.PROJECT else WalletOwnerType.USER
        )

        return ctx.paginateV2(
            actor,
            request.normalize(),
            create = { session ->
                val params: EnhancedPreparedStatement.() -> Unit = {
                    with(request) {
                        setParameter("filterCategory", filterCategory)
                        setParameter("filterProvider", filterProvider)
                        setParameter("filterArea", filterArea?.name)
                    }
                }

                if (request.filterUsable != true && request.includeBalance != true) {
                    session.sendPreparedStatement(
                        params,
                        """
                            declare c cursor for
                            select *
                            from accounting.products p
                            where
                                (:filterCategory::text is null or p.category = :filterCategory) and
                                (:filterProvider::text is null or p.provider = :filterProvider) and
                                (:filterArea::text is null or p.area = :filterArea)
                            order by p.provider, p.priority, p.id
                        """
                    )
                } else {
                    session.sendPreparedStatement(
                        {
                            params()
                            setParameter("accountId", project ?: actor.safeUsername())
                            setParameter(
                                "accountType",
                                (if (project != null) WalletOwnerType.PROJECT else WalletOwnerType.USER).name
                            )
                            setParameter("filterUsable", request.filterUsable == true)
                            setParameter("requireCredits", PaymentModel.FREE_BUT_REQUIRE_BALANCE.name)
                        },
                        """
                            declare c cursor for
                            with my_wallets as(
                                select *
                                from accounting.wallets
                                where
                                    account_id = :accountId and
                                    account_type = :accountType and
                                    (:filterCategory::text is null or product_category = :filterCategory) and
                                    (:filterProvider::text is null or product_provider = :filterProvider)
                            )
                            select p.*, w.balance
                            from 
                                accounting.products p left outer join my_wallets w 
                                    on (p.category = w.product_category and p.provider = w.product_provider)
                            where
                                (:filterCategory::text is null or p.category = :filterCategory) and
                                (:filterProvider::text is null or p.provider = :filterProvider) and
                                (:filterArea::text is null or p.area = :filterArea) and
                                (
                                    not :filterUsable or 
                                    (w.balance is not null and w.balance > 0) or 
                                    (p.price_per_unit = 0 and p.payment_model != :requireCredits)
                                )
                            order by p.provider, p.priority, p.id
                        """
                    )
                }
            },
            mapper = { _, rows ->
                rows.map {
                    val product = it.toProduct()
                    if (request.includeBalance == true) {
                        product.balance = it.getLong("balance")
                    }
                    product
                }
            }
        )
    }

    private suspend fun createProductCategoryIfNotExists(
        ctx: DBContext,
        provider: String,
        category: String,
        area: ProductArea
    ) {
        ctx.withSession { session ->
            val existing = findProductCategory(session, provider, category)

            if (existing != null) {
                val existingArea = existing.getField(ProductCategoryTable.area).let { ProductArea.valueOf(it) }
                if (existingArea != area) {
                    throw RPCException(
                        "Product category already exists with a different area ($existingArea)",
                        HttpStatusCode.BadRequest
                    )
                }
            } else {
                session.insert(ProductCategoryTable) {
                    set(ProductCategoryTable.area, area.name)
                    set(ProductCategoryTable.category, category)
                    set(ProductCategoryTable.provider, provider)
                }
            }
        }
    }

    private suspend fun findProductCategory(
        ctx: DBContext,
        provider: String,
        category: String
    ): RowData? {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("provider", provider)
                        setParameter("category", category)
                    },
                    """
                        select * 
                        from accounting.product_categories 
                        where 
                            provider = :provider and
                            category = :category
                    """
                )
                .rows
                .singleOrNull()
        }
    }

    private fun requirePermission(ctx: DBContext, actor: Actor, providerId: String, readOnly: Boolean) {
        if (readOnly) return
        if (actor is Actor.System) return
        if (actor is Actor.User && actor.principal.role in Roles.PRIVILEGED) return
        if (actor is Actor.User && actor.principal.role == Role.PROVIDER &&
            actor.username.removePrefix(AuthProviders.PROVIDER_PREFIX) == providerId) return

        throw RPCException("Forbidden", HttpStatusCode.Forbidden)
    }

    private fun RowData.toProduct(): Product {
        return when (ProductArea.valueOf(getField(ProductTable.area))) {
            ProductArea.COMPUTE -> {
                Product.Compute(
                    getField(ProductTable.id),
                    getField(ProductTable.pricePerUnit),
                    ProductCategoryId(
                        getField(ProductTable.category),
                        getField(ProductTable.provider)
                    ),
                    getField(ProductTable.description),
                    getField(ProductTable.hiddenInGrantApplications),
                    when (val reason = getFieldNullable(ProductTable.availability)) {
                        null -> ProductAvailability.Available()
                        else -> ProductAvailability.Unavailable(reason)
                    },
                    getField(ProductTable.priority),
                    getFieldNullable(ProductTable.cpu),
                    getFieldNullable(ProductTable.memoryInGigs),
                    getFieldNullable(ProductTable.gpu)
                )
            }

            ProductArea.STORAGE -> {
                Product.Storage(
                    getField(ProductTable.id),
                    getField(ProductTable.pricePerUnit),
                    ProductCategoryId(
                        getField(ProductTable.category),
                        getField(ProductTable.provider)
                    ),
                    getField(ProductTable.description),
                    getField(ProductTable.hiddenInGrantApplications),
                    when (val reason = getFieldNullable(ProductTable.availability)) {
                        null -> ProductAvailability.Available()
                        else -> ProductAvailability.Unavailable(reason)
                    },
                    getField(ProductTable.priority)
                )
            }
            ProductArea.INGRESS -> {
                Product.Ingress(
                    getField(ProductTable.id),
                    getField(ProductTable.pricePerUnit),
                    ProductCategoryId(
                        getField(ProductTable.category),
                        getField(ProductTable.provider)
                    ),
                    getField(ProductTable.description),
                    getField(ProductTable.hiddenInGrantApplications),
                    when (val reason = getFieldNullable(ProductTable.availability)) {
                        null -> ProductAvailability.Available()
                        else -> ProductAvailability.Unavailable(reason)
                    },
                    getField(ProductTable.priority),
                    getFieldNullable(ProductTable.paymentModel)?.let { PaymentModel.valueOf(it) }
                        ?: PaymentModel.PER_ACTIVATION
                )
            }
            ProductArea.LICENSE -> {
                Product.License(
                    getField(ProductTable.id),
                    getField(ProductTable.pricePerUnit),
                    ProductCategoryId(
                        getField(ProductTable.category),
                        getField(ProductTable.provider)
                    ),
                    getField(ProductTable.description),
                    getField(ProductTable.hiddenInGrantApplications),
                    when (val reason = getFieldNullable(ProductTable.availability)) {
                        null -> ProductAvailability.Available()
                        else -> ProductAvailability.Unavailable(reason)
                    },
                    getField(ProductTable.priority),
                    getFieldNullable(ProductTable.licenseTags)?.let { defaultMapper.decodeFromString(it) } ?: emptyList(),
                    getFieldNullable(ProductTable.paymentModel)?.let { PaymentModel.valueOf(it) }
                        ?: PaymentModel.PER_ACTIVATION,
                )
            }

            ProductArea.NETWORK_IP -> {
                Product.NetworkIP(
                    getField(ProductTable.id),
                    getField(ProductTable.pricePerUnit),
                    ProductCategoryId(
                        getField(ProductTable.category),
                        getField(ProductTable.provider)
                    ),
                    getField(ProductTable.description),
                    getField(ProductTable.hiddenInGrantApplications),
                    when (val reason = getFieldNullable(ProductTable.availability)) {
                        null -> ProductAvailability.Available()
                        else -> ProductAvailability.Unavailable(reason)
                    },
                    getField(ProductTable.priority),
                    getFieldNullable(ProductTable.paymentModel)?.let { PaymentModel.valueOf(it) }
                        ?: PaymentModel.PER_ACTIVATION,
                )
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
