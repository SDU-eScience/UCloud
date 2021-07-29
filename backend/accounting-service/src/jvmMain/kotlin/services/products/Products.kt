package dk.sdu.cloud.accounting.services.products

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.util.PartialQuery
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.sql.ResultSet

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

        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    val providers by parameterList<String>()
                    val categories by parameterList<String>()
                    val chargeTypes by parameterList<String>()
                    val productTypes by parameterList<String>()

                    for (req in request.items) {
                        providers.add(req.category.provider)
                        categories.add(req.category.name)
                        chargeTypes.add(req.chargeType.name)
                        productTypes.add(req.productType.name)
                    }
                },
                """
                    insert into accounting.product_categories
                        (provider, category, product_type, charge_type) 
                    select
                        unnest(:providers::text[]),
                        unnest(:categories::text[]),
                        unnest(:product_types::accounting.product_type[]),
                        unnest(:charge_types::accounting.charge_type[])
                    on conflict (provider, category)
                    do update set
                        charge_type = excluded.charge_type,
                        product_type = excluded.product_type
                """
            ).rowsAffected

            session.sendPreparedStatement(
                {
                    val names by parameterList<String>()
                    val pricesPerUnit by parameterList<Long>()
                    val cpus by parameterList<Int?>()
                    val gpus by parameterList<Int?>()
                    val memoryInGigs by parameterList<Int?>()
                    val licenseTags by parameterList<String?>()
                    val categories by parameterList<String>()
                    val providers by parameterList<String>()
                    val unitsOfPrice by parameterList<String>()
                    val freeToUse by parameterList<Boolean>()

                    for (req in request.items) {
                        names.add(req.name)
                        pricesPerUnit.add(req.pricePerUnit)
                        cpus.add(if (req is Product.Compute) req.cpu else null)
                        gpus.add(if (req is Product.Compute) req.gpu else null)
                        memoryInGigs.add(if (req is Product.Compute) req.memoryInGigs else null)
                        categories.add(req.category.name)
                        providers.add(req.category.provider)
                        unitsOfPrice.add(req.unitOfPrice.name)
                        freeToUse.add(req.freeToUse)
                        licenseTags.add(if (req is Product.License) defaultMapper.encodeToString(req.tags) else null)
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
                            unnest(:units_of_price::accounting.product_price_unit[]) unit_of_price,
                            unnest(:free_to_use::boolean[]) free_to_use,
                            unnest(:license_tags::jsonb[]) license_tags
                    )
                    insert into accounting.products
                        (name, price_per_unit, cpu, gpu, memory_in_gigs, license_tags, category,
                         unit_of_price, free_to_use, version) 
                    select
                        req.uname, req.price_per_unit, req.cpu, req.gpu, req.memory_in_gigs, req.license_tags,
                        pc.id, req.unit_of_price, req.free_to_use, coalesce(existing.version + 1, 1)
                    from
                        requests req join
                        accounting.product_categories pc on
                            req.category = pc.category and
                            req.provider = pc.provider left join
                        accounting.products existing on
                            req.uname = existing.name and
                            existing.category = pc.id
                """
            )
        }
    }

    suspend fun retrieve(
        actorAndProject: ActorAndProject,
        request: ProductsRetrieveRequest
    ): Product {
        return db.withSession { session ->
            val (params, query) = queryProducts(actorAndProject, request)
            session.sendPreparedStatement(
                params,
                query
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
                val (params, query) = queryProducts(actorAndProject, request)
                session.sendPreparedStatement(
                    params
                    ,
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


    private fun queryProducts(actorAndProject: ActorAndProject, flags: ProductFlags): PartialQuery {
        return PartialQuery(
            {
                setParameter("name_filter", flags.filterName)
                setParameter("provider_filter", flags.filterProvider)
                setParameter("product_filter", flags.filterArea?.name)
                setParameter("category_filter", flags.filterCategory)
                setParameter("include_balance", flags.includeBalance == true)
                setParameter("accountId", actorAndProject.project ?: actorAndProject.actor.safeUsername())
                setParameter("account_is_project", actorAndProject.project != null)
                setParameter("usable_filter", flags.filterUsable == true)
            },
            """
                with my_wallets as(
                    select *
                    from accounting.wallets wa join 
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
                            :category_filter is null or 
                            pc.category = :category_filter
                        ) and
                        (
                            :provider_filter is null or 
                            pc.provider = :provider_filter
                        )
                )
                select product_to_json(p, pc2, balance)
                from accounting.products p join product_categories pc2 on pc2.id = p.category
                    left outer join my_wallets mw 
                        on (p.category = mw.category and pc2.provider = mw.provider)
                where
                    (
                        :category_filter is null or 
                        pc2.category = :category_filter
                    ) and
                    (
                        :provider_filter is null or 
                        pc2.provider = :provider_filter
                    ) and 
                    (
                        (mw.balance is not null and mw.balance > 0) or 
                        (p.free_to_use)
                    )
                order by pc2.provider, pc2.category
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
