package dk.sdu.cloud.accounting.services.products

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.provider.api.basicTranslationToAccountingUnit
import dk.sdu.cloud.provider.api.translateToAccountingFrequency
import dk.sdu.cloud.provider.api.translateToChargeType
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.*
import kotlinx.serialization.encodeToString

class ProductService(
    private val db: DBContext,
) {
    suspend fun productV1toV2(product: Product): ProductV2 {
        val category = ProductCategory(
            product.category.name,
            product.category.provider,
            product.productType,
            basicTranslationToAccountingUnit(product.unitOfPrice, product.productType),
            translateToAccountingFrequency(product.unitOfPrice),
            emptyList(),
            product.freeToUse
        )

        return when (product) {
            is Product.Compute -> {
                ProductV2.Compute(
                    name = product.name,
                    price = product.pricePerUnit,
                    category = category,
                    description = product.description,
                    cpu = product.cpu,
                    memoryInGigs = product.memoryInGigs,
                    gpu = product.gpu,
                    cpuModel = product.cpuModel,
                    memoryModel = product.memoryModel,
                    gpuModel = product.gpuModel,
                    hiddenInGrantApplications = product.hiddenInGrantApplications
                )
            }

            is Product.Storage -> {
                ProductV2.Storage(
                    name = product.name,
                    price = product.pricePerUnit,
                    category = category,
                    description = product.description,
                    hiddenInGrantApplications = product.hiddenInGrantApplications
                )
            }

            is Product.License -> {
                ProductV2.License(
                    name = product.name,
                    price = product.pricePerUnit,
                    category = category,
                    description = product.description,
                    hiddenInGrantApplications = product.hiddenInGrantApplications,
                    tags = product.tags
                )
            }

            is Product.NetworkIP -> {
                ProductV2.NetworkIP(
                    name = product.name,
                    price = product.pricePerUnit,
                    category = category,
                    description = product.description,
                    hiddenInGrantApplications = product.hiddenInGrantApplications
                )
            }

            is Product.Ingress -> {
                ProductV2.Ingress(
                    name = product.name,
                    price = product.pricePerUnit,
                    category = category,
                    description = product.description,
                    hiddenInGrantApplications = product.hiddenInGrantApplications
                )
            }

            else -> {
                throw RPCException("Unknown Product Type", HttpStatusCode.InternalServerError)
            }
        }

    }

    suspend fun createV1(
        actorAndProject: ActorAndProject,
        request: BulkRequest<Product>
    ) {
        val newProducts = request.items.map { product ->
            productV1toV2(product)
        }

        createV2(
            actorAndProject,
            bulkRequestOf(newProducts)
        )
    }

    suspend fun createV2(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ProductV2>
    ) {
        for (req in request.items) {
            requirePermission(actorAndProject.actor, req.category.provider, readOnly = false)
        }

        db.withSession(remapExceptions = true) { session ->
            for (req in request.items) {
                session.sendPreparedStatement(
                    {
                        setParameter("provider", req.category.provider)
                        setParameter("category", req.category.name)

                        setParameter("acname", req.category.accountingUnit.name)
                        setParameter("name_plural", req.category.accountingUnit.namePlural)
                        setParameter("floating", req.category.accountingUnit.floatingPoint)
                        setParameter("display", req.category.accountingUnit.displayFrequencySuffix)

                        setParameter("frequency", req.category.accountingFrequency.name)
                        setParameter("product_type", req.productType.name)
                        setParameter("free_to_use", req.category.freeToUse)
                        //TODO(HENRIK) There is no need for this in the future
                        setParameter("charge_type", translateToChargeType(req.category).name)
                        setParameter("allow_sub_allocations", req.category.allowSubAllocations)
                    },
                    """
                        with
                            acinsert as (
                                insert into accounting.accounting_units 
                                    (name, name_plural, floating_point, display_frequency_suffix)
                                values
                                    (:acname, :name_plural, :floating, :display) 
                                on conflict
                                    (name, name_plural, floating_point, display_frequency_suffix)
                                do update set
                                    name_plural = :name_plural
                                returning
                                    id 
                            ),
                            inserts as (
                                select 
                                    :provider provider, 
                                    :category category, 
                                    :product_type::accounting.product_type product_type, 
                                    ac.id accounting_unit,
                                    :frequency frequency,
                                    :charge_type::accounting.charge_type charge_t,
                                    :free_to_use::bool free,
                                    :allow_sub_allocations::bool allow_sub_allocations
                                from acinsert ac
                            )
                        insert into accounting.product_categories
                            (provider, category, product_type, accounting_unit, accounting_frequency, charge_type,
                             free_to_use, allow_sub_allocations) 
                        select
                            provider, category, product_type, accounting_unit, frequency, charge_t,
                            free, allow_sub_allocations
                        from inserts
                        on conflict (provider, category)  
                        do update set
                            product_type = excluded.product_type,
                            allow_sub_allocations = excluded.allow_sub_allocations
                    """,
                )
            }

            session.sendPreparedStatement(
                {
                    // NOTE(Dan): The version property is no longer used and instead we simply update the products
                    val names by parameterList<String>()
                    val prices by parameterList<Long>()
                    val cpus by parameterList<Int?>()
                    val gpus by parameterList<Int?>()
                    val memoryInGigs by parameterList<Int?>()
                    val cpuModel by parameterList<String?>()
                    val memoryModel by parameterList<String?>()
                    val gpuModel by parameterList<String?>()
                    val licenseTags by parameterList<String?>()
                    val categories by parameterList<String>()
                    val providers by parameterList<String>()
                    val description by parameterList<String>()

                    for (req in request.items) {
                        names.add(req.name)
                        prices.add(req.price)
                        categories.add(req.category.name)
                        providers.add(req.category.provider)
                        licenseTags.add(if (req is ProductV2.License) defaultMapper.encodeToString(req.tags) else null)
                        description.add(req.description)

                        run {
                            cpus.add(if (req is ProductV2.Compute) req.cpu else null)
                            gpus.add(if (req is ProductV2.Compute) req.gpu else null)
                            memoryInGigs.add(if (req is ProductV2.Compute) req.memoryInGigs else null)

                            cpuModel.add(if (req is ProductV2.Compute) req.cpuModel else null)
                            gpuModel.add(if (req is ProductV2.Compute) req.gpuModel else null)
                            memoryModel.add(if (req is ProductV2.Compute) req.memoryModel else null)
                        }
                    }
                },
                """
                    with requests as (
                        select
                            unnest(:names::text[]) uname,
                            unnest(:prices::bigint[]) price,
                            unnest(:cpus::int[]) cpu,
                            unnest(:gpus::int[]) gpu,
                            unnest(:memory_in_gigs::int[]) memory_in_gigs,
                            unnest(:categories::text[]) category,
                            unnest(:providers::text[]) provider,
                            unnest(:license_tags::jsonb[]) license_tags,
                            unnest(:description::text[]) description,
                            unnest(:cpu_model::text[]) cpu_model,
                            unnest(:gpu_model::text[]) gpu_model,
                            unnest(:memory_model::text[]) memory_model
                    )
                    insert into accounting.products
                        (name, price, cpu, gpu, memory_in_gigs, license_tags, category,
                          version, description, cpu_model, gpu_model, memory_model) 
                    select
                        req.uname, req.price, req.cpu, req.gpu, req.memory_in_gigs, req.license_tags,
                        pc.id, 1, req.description, req.cpu_model, req.gpu_model, req.memory_model
                    from
                        requests req join
                        accounting.product_categories pc on
                            req.category = pc.category and
                            req.provider = pc.provider left join
                        accounting.products existing on
                            req.uname = existing.name and
                            existing.category = pc.id
                    on conflict (name, category, version)
                    do update set
                        price = excluded.price,
                        cpu = excluded.cpu,
                        gpu = excluded.gpu,
                        memory_in_gigs = excluded.memory_in_gigs,
                        license_tags = excluded.license_tags,
                        description = excluded.description,
                        cpu_model = excluded.cpu_model,
                        gpu_model = excluded.gpu_model,
                        memory_model = excluded.memory_model
                """
            )
        }
    }

    suspend fun retrieve(
        actorAndProject: ActorAndProject,
        request: ProductsRetrieveRequest
    ): Product {
        return browse(
            actorAndProject,
            ProductsBrowseRequest(
                filterName = request.filterName,
                filterCategory = request.filterCategory,
                filterProvider = request.filterProvider,
                filterArea = request.filterArea,
                includeBalance = request.includeBalance
            )
        ).items.singleOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
    }

    suspend fun retrieve(
        actorAndProject: ActorAndProject,
        request: ProductsV2RetrieveRequest
    ): ProductV2 {
        return browse(
            actorAndProject,
            ProductsV2BrowseRequest(
                filterName = request.filterName,
                filterUsable = request.filterUsable,
                filterCategory = request.filterCategory,
                filterProvider = request.filterProvider,
                filterProductType = request.filterProductType,
                includeBalance = request.includeBalance
            )
        ).items.singleOrNull()?.first ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
    }

    suspend fun browse(
        actorAndProject: ActorAndProject,
        request: ProductsBrowseRequest
    ): PageV2<Product> {
        val newRequests = ProductsV2BrowseRequest(
            itemsPerPage = request.itemsPerPage,
            next = request.next,
            consistency = request.consistency,
            itemsToSkip = request.itemsToSkip,
            filterName = request.filterName,
            filterProvider = request.filterProvider,
            filterProductType = request.filterArea,
            filterCategory = request.filterCategory,
            includeBalance = request.includeBalance,
            includeMaxBalance = request.includeMaxBalance
        )
        val page = browse(actorAndProject, newRequests)
        val items = page.items.map {
            val product = it.first.toV1()
            product.balance = it.second
            product
        }
        return PageV2(page.itemsPerPage, items, page.next)
    }

    suspend fun browse(
        actorAndProject: ActorAndProject,
        request: ProductsV2BrowseRequest
    ): PageV2<Pair<ProductV2, Long?>> {
        return db.withSession { session ->
            val itemsPerPage = request.normalize().itemsPerPage

            val rows = session.sendPreparedStatement(
                {
                    setParameter("name_filter", request.filterName)
                    setParameter("provider_filter", request.filterProvider)
                    setParameter("product_filter", request.filterProductType?.name)
                    setParameter("category_filter", request.filterCategory)
                    setParameter("accountId", actorAndProject.project ?: actorAndProject.actor.safeUsername())
                    setParameter("account_is_project", actorAndProject.project != null)

                    //Cannot use "-" since product names include these, resulting in split.size != 3 -> null
                    val nextParts = request.next?.split("@")?.takeIf { it.size == 3 }
                    setParameter("next_provider", nextParts?.get(0))
                    setParameter("next_category", nextParts?.get(1))
                    setParameter("next_name", nextParts?.get(2))
                },
                """
                    select accounting.product_to_json(
                        p,
                        pc,
                        au,
                        0
                    )
                    from
                        accounting.products p join
                        accounting.product_categories pc on pc.id = p.category join 
                        accounting.accounting_units au on au.id = pc.accounting_unit
                    where
                        (
                            (:next_provider::text is null or :next_category::text is null or :next_name::text is null) or
                            pc.provider > :next_provider::text or
                            (pc.provider = :next_provider::text and pc.category > :next_category::text) or
                            (pc.provider = :next_provider::text and pc.category = :next_category::text and p.name > :next_name::text)
                        ) and
                        (
                            :category_filter::text is null or
                            pc.category = :category_filter
                        ) and
                        (
                            :provider_filter::text is null or
                            pc.provider = :provider_filter
                        ) and

                        (
                            :product_filter::accounting.product_type is null or
                            pc.product_type = :product_filter
                        ) and
                        (
                            :name_filter::text is null or
                            p.name = :name_filter
                        )
                    order by pc.provider, pc.category, p.name
                    limit $itemsPerPage;
                """
            ).rows
            val result = rows.mapNotNull {
                val product = defaultMapper.decodeFromString(ProductV2.serializer(), it.getString(0)!!)
                val balance = if (request.includeBalance == true) {
                    val owner = actorAndProject.project ?: actorAndProject.actor.safeUsername()
                    0L // TODO!
                } else {
                    null
                }
                return@mapNotNull Pair(product, balance)
            }
            val next = if (result.size < itemsPerPage) {
                null
            } else buildString {
                val lastElement = result.last().first
                append(lastElement.category.provider)
                append('@')
                append(lastElement.category.name)
                append('@')
                append(lastElement.name)
            }

            PageV2(
                itemsPerPage,
                result,
                next
            )
        }
    }

    private fun requirePermission(actor: Actor, providerId: String, readOnly: Boolean) {
        if (readOnly) return
        if (actor is Actor.System) return
        if (actor is Actor.User && actor.principal.role in Roles.PRIVILEGED) return
        if (actor is Actor.User && actor.principal.role == Role.PROVIDER &&
            actor.username.removePrefix(AuthProviders.PROVIDER_PREFIX) == providerId
        ) return

        throw RPCException("Forbidden", HttpStatusCode.Forbidden)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
