package dk.sdu.cloud.accounting.services.products

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductsBrowseRequest
import dk.sdu.cloud.accounting.api.ProductsRetrieveRequest
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.parameterList
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*
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
        TODO()
    }

    suspend fun browse(
        actorAndProject: ActorAndProject,
        request: ProductsBrowseRequest
    ): PageV2<Product> {
        TODO()
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
