package dk.sdu.cloud.app.orchestrator.services

import com.github.jasync.sql.db.ResultSet
import dk.sdu.cloud.Actor
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*

object IngressUpdatesTable : SQLTable("ingress_updates") {
    val ingressId = text("ingress_id")
    val timestamp = timestamp("timestamp")
    val state = text("state")
    val status = text("status")
    val changeBinding = bool("change_binding")
    val boundTo = text("bound_to")
}

object IngressTable : SQLTable("ingresses") {
    val id = text("id")
    val domain = text("domain")
    val productId = text("product_id")
    val productProvider = text("product_provider")
    val productCategory = text("product_category")
    val productPricePerUnit = long("product_price_per_unit")
    val ownerUsername = text("owner_username")
    val ownerProject = text("owner_project")
    val currentState = text("current_state")

    // With default values:
    val statusBoundTo = text("status_bound_to")
    val createdAt = timestamp("created_at")
    val lastUpdate = timestamp("last_update")
    val creditsCharged = long("credits_charged")
}

class IngressDao(
    private val products: ProductCache,
) {
    suspend fun create(
        ctx: DBContext,
        ingress: Ingress,
    ) {
        ctx.withSession { session ->
            session.insert(IngressTable) {
                set(IngressTable.id, ingress.id)
                set(IngressTable.domain, ingress.specification.domain)
                set(IngressTable.productId, ingress.specification.product.id)
                set(IngressTable.productCategory, ingress.specification.product.category)
                set(IngressTable.productProvider, ingress.specification.product.provider)
                set(IngressTable.productPricePerUnit, ingress.billing.pricePerUnit)
                set(IngressTable.creditsCharged, ingress.billing.creditsCharged)
                set(IngressTable.ownerUsername, ingress.owner.createdBy)
                set(IngressTable.ownerProject, ingress.owner.project)
                set(IngressTable.currentState, ingress.status.state.name)
                set(IngressTable.statusBoundTo, ingress.status.boundTo)
            }

            insertUpdate(
                session,
                ingress,
                IngressUpdate(
                    Time.now(),
                    state = ingress.status.state,
                    didBind = true,
                    newBinding = ingress.status.boundTo
                )
            )

        }
    }

    suspend fun insertUpdate(
        ctx: DBContext,
        id: IngressId,
        update: IngressUpdate,
    ) {
        ctx.withSession { session ->
            session.insert(IngressUpdatesTable) {
                set(IngressUpdatesTable.ingressId, id.id)
                set(IngressUpdatesTable.state, update.state?.name)
                set(IngressUpdatesTable.status, update.status)
                set(IngressUpdatesTable.changeBinding, update.didBind)
                set(IngressUpdatesTable.boundTo, update.newBinding)
            }
        }
    }

    suspend fun chargeCredits(
        ctx: DBContext,
        id: IngressId,
        creditsCharged: Long,
    ) {
        ctx.withSession { session ->
            val success = session
                .sendPreparedStatement(
                    {
                        setParameter("id", id.id)
                        setParameter("creditsCharged", creditsCharged)
                    },
                    """
                        update ingresses
                        set credits_charged = credits_charged + :creditsCharged
                        where id = :id
                    """
                )
                .rowsAffected > 0L

            if (!success) throw RPCException("Unknown ingress", HttpStatusCode.NotFound)
        }
    }

    suspend fun delete(
        ctx: DBContext,
        ids: List<String>,
    ): List<Ingress> {
        return ctx.withSession { session ->
            val params: EnhancedPreparedStatement.() -> Unit = {
                setParameter("ids", ids)
            }

            session.sendPreparedStatement(
                params,
                "delete from ingress_updates where ingress_id in (select unnest(:ids::text[]))"
            )

            session
                .sendPreparedStatement(
                    params,
                    "delete from ingresses where id in (select unnest(:ids::text[])) returning *"
                )
                .rows.let { mapRows(session, it, IngressDataIncludeFlags()) }
        }
    }

    suspend fun retrieve(
        ctx: DBContext,
        id: IngressId,
        flags: IngressDataIncludeFlags,
    ): Ingress? {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", id.id)
                    },
                    """
                        select * from ingresses where id = :id
                    """
                )
                .rows
                .let { mapRows(session, it, flags) }
                .singleOrNull()
        }
    }

    suspend fun browse(
        db: AsyncDBSessionFactory,
        actor: Actor,
        project: String?,
        pagination: NormalizedPaginationRequestV2,
        flags: IngressDataIncludeFlags,
        filters: IngressFilters,
    ): PageV2<Ingress> {
        return db.paginateV2(
            actor,
            pagination,
            create = { session ->
                val isSystem = actor == Actor.System
                session
                    .sendPreparedStatement(
                        {
                            setParameter("isSystem", isSystem)
                            setParameter("project", project)
                            setParameter("username", actor.safeUsername())
                            setParameter("domain", filters.domain)
                            setParameter("provider", filters.provider)
                        },
                        """
                            declare c cursor for
                            select *
                            from app_orchestrator.ingresses i
                            where
                                (
                                    :isSystem or
                                    (i.owner_project is null and :project::text is null and i.owner_username = :username) or
                                    (i.owner_project = :project)
                                ) and
                                (:domain::text is null or i.domain = :domain) and
                                (:provider::text is null or i.product_provider = :provider)
                            order by
                                i.domain
                        """
                    )
            },
            mapper = { session, rows -> mapRows(session, rows, flags) }
        )
    }

    private suspend fun mapRows(
        session: AsyncDBConnection,
        rows: ResultSet,
        flags: IngressDataIncludeFlags,
    ): List<Ingress> {
        val ids = rows
            .map { it.getField(IngressTable.id) }
            .toSet()
            .toList()

        var ingresses = rows.map {
            Ingress(
                it.getField(IngressTable.id),
                IngressSpecification(
                    it.getField(IngressTable.domain).toLowerCase(),
                    ProductReference(
                        it.getField(IngressTable.productId),
                        it.getField(IngressTable.productCategory),
                        it.getField(IngressTable.productProvider),
                    )
                ),
                IngressOwner(it.getField(IngressTable.ownerUsername), it.getField(IngressTable.ownerProject)),
                it.getField(IngressTable.createdAt).toDateTime().millis,
                IngressStatus(
                    it.getFieldNullable(IngressTable.statusBoundTo),
                    IngressState.valueOf(it.getField(IngressTable.currentState))
                ),
                IngressBilling(
                    it.getField(IngressTable.productPricePerUnit),
                    it.getField(IngressTable.creditsCharged),
                ),
                emptyList()
            )
        }

        if (flags.includeUpdates == true) {
            val allUpdates = session
                .sendPreparedStatement(
                    {
                        setParameter("ids", ids)
                    },
                    """
                        select *
                        from ingress_updates
                        where ingress_id in (select unnest(:ids::text[]))
                        order by timestamp desc
                    """
                )
                .rows
                .map {
                    val id = it.getField(IngressUpdatesTable.ingressId)

                    id to IngressUpdate(
                        it.getField(IngressUpdatesTable.timestamp).toDateTime().millis,
                        it.getFieldNullable(IngressUpdatesTable.state)?.let { IngressState.valueOf(it) },
                        it.getFieldNullable(IngressUpdatesTable.status),
                        it.getField(IngressUpdatesTable.changeBinding),
                        it.getFieldNullable(IngressUpdatesTable.boundTo)
                    )
                }
                .groupBy { it.first }
                .mapValues { all -> all.value.map { it.second } }

            ingresses = ingresses.map { it.copy(updates = allUpdates.getValue(it.id)) }
        }

        if (flags.includeProduct == true) {
            ingresses = ingresses.map {
                it.copy(
                    resolvedProduct = products.find(
                        it.specification.product.provider,
                        it.specification.product.id,
                        it.specification.product.category
                    )
                )
            }
        }

        return ingresses
    }
}
