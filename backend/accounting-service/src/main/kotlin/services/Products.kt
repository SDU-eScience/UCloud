package dk.sdu.cloud.accounting.services

import com.github.jasync.sql.db.RowData
import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.api.FindProductRequest
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductArea
import dk.sdu.cloud.accounting.api.ProductAvailability
import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.PostgresErrorCodes
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.errorCode
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.getFieldNullable
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.int
import dk.sdu.cloud.service.db.async.long
import dk.sdu.cloud.service.db.async.paginatedQuery
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.mapItems
import io.ktor.http.HttpStatusCode

object ProductCategoryTable : SQLTable("product_categories") {
    val provider = text("provider", notNull = true)
    val category = text("category", notNull = true)
    val area = text("area", notNull = true)
}

object ProductTable : SQLTable("products") {
    val provider = text("provider", notNull = true)
    val category = text("category", notNull = true)
    val area = text("area", notNull = true)

    val pricePerUnit = long("price_per_unit", notNull = true)
    val id = text("id", notNull = true)
    val description = text("description", notNull = true)
    val availability = text("availability", notNull = false)
    val priority = int("priority", notNull = true)

    val cpu = int("cpu", notNull = false)
    val gpu = int("gpu", notNull = false)
    val memoryInGigs = int("memory_in_gigs", notNull = false)
}

class ProductService {
    suspend fun create(
        ctx: DBContext,
        actor: Actor,
        product: Product
    ) {
        ctx.withSession { session ->
            requirePermission(session, actor, readOnly = false)
            createProductCategoryIfNotExists(session, product.category.provider, product.category.id, product.area)
            try {
                session.insert(ProductTable) {
                    set(ProductTable.provider, product.category.provider)
                    set(ProductTable.category, product.category.id)
                    set(ProductTable.area, product.area.name)
                    set(ProductTable.pricePerUnit, product.pricePerUnit)
                    set(ProductTable.id, product.id)
                    set(ProductTable.description, product.description)
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
            requirePermission(session, actor, readOnly = false)

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
                        setParameter("availability", when(val availability = product.availability) {
                            is ProductAvailability.Available -> null
                            is ProductAvailability.Unavailable -> availability.reason
                        })
                        setParameter("cpu", when(product) {
                            is Product.Compute -> product.cpu
                            else -> null
                        })
                        setParameter("gpu", when(product) {
                            is Product.Compute -> product.gpu
                            else -> null
                        })
                        setParameter("memoryInGigs", when(product) {
                            is Product.Compute -> product.memoryInGigs
                            else -> null
                        })
                    },

                    """
                        update products 
                        set
                            price_per_unit = :pricePerUnit,
                            description = :description,
                            availability = :availability,
                            cpu = :cpu,
                            gpu = :gpu,
                            memory_in_gigs = :memoryInGigs
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
            requirePermission(session, actor, readOnly = true)

            session
                .sendPreparedStatement(
                    {
                        setParameter("category", product.productCategory)
                        setParameter("provider", product.provider)
                        setParameter("id", product.product)
                    },

                    """
                        select *
                        from products
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
        provider: String
    ): List<Product> {
        return ctx.withSession { session ->
            requirePermission(session, actor, readOnly = true)

            session
                .sendPreparedStatement(
                    { setParameter("provider", provider) },
                    "select * from products where provider = :provider order by priority, id"
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
            requirePermission(session, actor, readOnly = true)

            session
                .paginatedQuery(
                    paging,

                    {
                        setParameter("provider", provider)
                    },

                    """
                        from products
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
        paging: NormalizedPaginationRequest
    ): Page<Product> {
        return ctx.withSession { session ->
            requirePermission(session, actor, readOnly = true)

            session
                .paginatedQuery(
                    paging,

                    {
                        setParameter("provider", provider)
                        setParameter("area", area.name)
                    },

                    """
                        from products
                        where provider = :provider AND area = :area
                    """,

                    "order by priority, id"
                )
                .mapItems { it.toProduct() }
        }
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
                        from product_categories 
                        where 
                            provider = :provider and
                            category = :category
                    """
                )
                .rows
                .singleOrNull()
        }
    }

    private fun requirePermission(ctx: DBContext, actor: Actor, readOnly: Boolean) {
        if (readOnly) return
        if (actor is Actor.System) return
        if (actor is Actor.User && actor.principal.role in Roles.PRIVILEGED) return

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
                    when (val reason = getFieldNullable(ProductTable.availability)) {
                        null -> ProductAvailability.Available()
                        else -> ProductAvailability.Unavailable(reason)
                    },
                    getField(ProductTable.priority)
                )
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
