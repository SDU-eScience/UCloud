package dk.sdu.cloud.accounting.services.products

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.util.PartialQuery
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class ProductService(
    private val db: DBContext,
) {
    suspend fun create(
        actorAndProject: ActorAndProject,
        request: BulkRequest<Product>
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
                        setParameter("charge_type", req.chargeType.name)
                        setParameter("product_type", req.productType.name)
                        setParameter("unit_of_price", req.unitOfPrice.name)
                    },
                    """
                        insert into accounting.product_categories
                        (provider, category, product_type, charge_type, unit_of_price) 
                        values (
                            :provider, 
                            :category, 
                            :product_type::accounting.product_type, 
                            :charge_type::accounting.charge_type, 
                            :unit_of_price::accounting.product_price_unit
                        )
                        on conflict (provider, category)  
                        do update set
                            charge_type = excluded.charge_type,
                            product_type = excluded.product_type,
                            unit_of_price = excluded.unit_of_price
                    """
                )
            }

            session.sendPreparedStatement(
                {
                    // NOTE(Dan): The version property is no longer used and instead we simply update the products
                    val names by parameterList<String>()
                    val pricesPerUnit by parameterList<Long>()
                    val cpus by parameterList<Int?>()
                    val gpus by parameterList<Int?>()
                    val memoryInGigs by parameterList<Int?>()
                    val cpuModel by parameterList<String?>()
                    val memoryModel by parameterList<String?>()
                    val gpuModel by parameterList<String?>()
                    val licenseTags by parameterList<String?>()
                    val categories by parameterList<String>()
                    val providers by parameterList<String>()
                    val freeToUse by parameterList<Boolean>()
                    val description by parameterList<String>()

                    for (req in request.items) {
                        names.add(req.name)
                        pricesPerUnit.add(req.pricePerUnit)
                        categories.add(req.category.name)
                        providers.add(req.category.provider)
                        freeToUse.add(req.freeToUse)
                        licenseTags.add(if (req is Product.License) defaultMapper.encodeToString(req.tags) else null)
                        description.add(req.description)

                        run {
                            cpus.add(if (req is Product.Compute) req.cpu else null)
                            gpus.add(if (req is Product.Compute) req.gpu else null)
                            memoryInGigs.add(if (req is Product.Compute) req.memoryInGigs else null)

                            cpuModel.add(if (req is Product.Compute) req.cpuModel else null)
                            gpuModel.add(if (req is Product.Compute) req.gpuModel else null)
                            memoryModel.add(if (req is Product.Compute) req.memoryModel else null)
                        }
                    }
                },
                """
                    with requests as (
                        select
                            unnest(:names::text[]) uname,
                            unnest(:prices_per_unit::bigint[]) price_per_unit,
                            unnest(:cpus::int[]) cpu,
                            unnest(:gpus::int[]) gpu,
                            unnest(:memory_in_gigs::int[]) memory_in_gigs,
                            unnest(:categories::text[]) category,
                            unnest(:providers::text[]) provider,
                            unnest(:free_to_use::boolean[]) free_to_use,
                            unnest(:license_tags::jsonb[]) license_tags,
                            unnest(:description::text[]) description,
                            unnest(:cpu_model::text[]) cpu_model,
                            unnest(:gpu_model::text[]) gpu_model,
                            unnest(:memory_model::text[]) memory_model
                    )
                    insert into accounting.products
                        (name, price_per_unit, cpu, gpu, memory_in_gigs, license_tags, category,
                         free_to_use, version, description, cpu_model, gpu_model, memory_model) 
                    select
                        req.uname, req.price_per_unit, req.cpu, req.gpu, req.memory_in_gigs, req.license_tags,
                        pc.id, req.free_to_use, 1, req.description, req.cpu_model, req.gpu_model, req.memory_model
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
                        price_per_unit = excluded.price_per_unit,
                        cpu = excluded.cpu,
                        gpu = excluded.gpu,
                        memory_in_gigs = excluded.memory_in_gigs,
                        license_tags = excluded.license_tags,
                        free_to_use = excluded.free_to_use,
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
        return db.withSession { session ->
            val (params, query) = queryProducts(actorAndProject, request, null)
            session.sendPreparedStatement(
                params,
                query,
            ).rows
                .singleOrNull()
                ?.let { defaultMapper.decodeFromString<Product>(it.getString(0)!!) }
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }

    suspend fun browse(
        actorAndProject: ActorAndProject,
        request: ProductsBrowseRequest
    ): PageV2<Product> {
        return db.paginateV2(
            actorAndProject.actor,
            request.normalize(),
            create = { session ->
                val (params, query) = queryProducts(actorAndProject, request, request.showAllVersions)
                session.sendPreparedStatement(
                    params,
                    """
                        declare c cursor for 
                        $query
                    """
                )
            },
            mapper = {_, rows ->
                rows.map { defaultMapper.decodeFromString(it.getString(0)!!)}
            }
        )
    }


    private fun queryProducts(actorAndProject: ActorAndProject, flags: ProductFlags, showAllVersions: Boolean?): PartialQuery {
        return PartialQuery(
            {
                setParameter("name_filter", flags.filterName)
                setParameter("provider_filter", flags.filterProvider)
                setParameter("product_filter", flags.filterArea?.name)
                setParameter("category_filter", flags.filterCategory)
                setParameter("version_filter", flags.filterVersion)
                setParameter("show_all_versions", showAllVersions == true)
                setParameter("include_balance", flags.includeBalance == true)
                setParameter("accountId", actorAndProject.project ?: actorAndProject.actor.safeUsername())
                setParameter("account_is_project", actorAndProject.project != null)
            },
            """
                with my_wallets as (
                    select wa.category as wallet_category, wa.id as wallet_id, username, project_id, provider, balance
                    from
                        accounting.wallets wa join
                        accounting.wallet_owner wo on wo.id = wa.owned_by join
                        accounting.product_categories pc on pc.id = wa.category left join
                        (
                            select sum(walloc.balance) balance, wa.id
                            from
                                accounting.wallets wa join
                                accounting.wallet_allocations walloc on wa.id = walloc.associated_wallet
                            group by wa.id
                        ) as balances on (:include_balance and balances.id = wa.id)
                    where
                        (
                            (not :account_is_project and wo.username = :accountId) or
                            (:account_is_project and wo.project_id = :accountId)
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
                        )
                )
                select accounting.product_to_json(
                    p,
                    pc,
                    (CASE WHEN :include_balance = true THEN (coalesce(balance::bigint, 0)) END)
                )
                from
                    accounting.products p join
                    accounting.product_categories pc on pc.id = p.category left outer join
                    my_wallets mw on (pc.id = mw.wallet_category and pc.provider = mw.provider)
                where
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
                    ) and
                    (
                        not :include_balance or
                        (
                            (mw.balance is not null and mw.balance > 0) or
                            (p.free_to_use)
                        )
                    ) and
                    (
                        :show_all_versions or
                        p.version = (
                            select max(version) highest_version
                            from accounting.products p2
                            where (
                                (
                                    :version_filter::bigint is null or
                                    version = :version_filter
                                ) and
                                p.name = p2.name and
                                p.category = p2.category
                            )
                        )
                    )
                order by pc.provider, pc.category
            """
        )
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
