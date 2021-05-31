package dk.sdu.cloud.app.orchestrator.services

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.jasync.sql.db.ResultSet
import dk.sdu.cloud.Actor
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.provider.api.ResourceAclEntry
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

object LicenseUpdatesTable : SQLTable("license_updates") {
    val licenseId = text("license_id")
    val timestamp = timestamp("timestamp")
    val state = text("state")
    val status = text("status")
}

object LicenseTable : SQLTable("licenses") {
    val id = text("id")
    val productId = text("product_id")
    val productProvider = text("product_provider")
    val productCategory = text("product_category")
    val productPricePerUnit = long("product_price_per_unit")
    val ownerUsername = text("owner_username")
    val ownerProject = text("owner_project")
    val currentState = text("current_state")

    // With default values:
    val createdAt = timestamp("created_at")
    val lastUpdate = timestamp("last_update")
    val creditsCharged = long("credits_charged")
    val acl = jsonb("acl")
}

class LicenseDao(
    private val products: ProductCache,
) {
    suspend fun create(
        ctx: DBContext,
        license: License,
    ) {
        ctx.withSession { session ->
            session.insert(LicenseTable) {
                set(LicenseTable.id, license.id)
                set(LicenseTable.productId, license.specification.product.id)
                set(LicenseTable.productCategory, license.specification.product.category)
                set(LicenseTable.productProvider, license.specification.product.provider)
                set(LicenseTable.productPricePerUnit, license.billing.pricePerUnit)
                set(LicenseTable.creditsCharged, license.billing.creditsCharged)
                set(LicenseTable.ownerUsername, license.owner.createdBy)
                set(LicenseTable.ownerProject, license.owner.project)
                set(LicenseTable.currentState, license.status.state.name)
            }

            insertUpdate(
                session,
                license,
                LicenseUpdate(
                    Time.now(),
                    state = license.status.state,
                )
            )

        }
    }

    suspend fun insertUpdate(
        ctx: DBContext,
        id: LicenseId,
        update: LicenseUpdate,
    ) {
        ctx.withSession { session ->
            session.insert(LicenseUpdatesTable) {
                set(LicenseUpdatesTable.licenseId, id.id)
                set(LicenseUpdatesTable.state, update.state?.name)
                set(LicenseUpdatesTable.status, update.status)
            }
        }
    }

    suspend fun chargeCredits(
        ctx: DBContext,
        id: LicenseId,
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
                        update licenses
                        set credits_charged = credits_charged + :creditsCharged
                        where id = :id
                    """
                )
                .rowsAffected > 0L

            if (!success) throw RPCException("Unknown license", HttpStatusCode.NotFound)
        }
    }

    suspend fun delete(
        ctx: DBContext,
        ids: List<String>,
    ): List<License> {
        return ctx.withSession { session ->
            val params: EnhancedPreparedStatement.() -> Unit = {
                setParameter("ids", ids)
            }

            session.sendPreparedStatement(
                params,
                "delete from license_updates where license_id in (select unnest(:ids::text[]))"
            )

            session
                .sendPreparedStatement(
                    params,
                    "delete from licenses where id in (select unnest(:ids::text[])) returning *"
                )
                .rows.let { mapRows(session, it, null, LicenseDataIncludeFlags()) }
        }
    }

    suspend fun retrieve(
        ctx: DBContext,
        id: LicenseId,
        flags: LicenseDataIncludeFlags,
    ): License? {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", id.id)
                    },
                    """
                        select * from licenses where id = :id
                    """
                )
                .rows
                .let { mapRows(session, it, null, flags) }
                .singleOrNull()
        }
    }

    suspend fun updateAcl(
        ctx: DBContext,
        id: LicenseId,
        acl: List<ResourceAclEntry<LicensePermission>>,
    ): License {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("acl", defaultMapper.encodeToString(acl))
                        setParameter("id", id.id)
                    },
                    "update licenses set acl = :acl::jsonb where id = :id returning *"
                )
                .rows
                .let { mapRows(session, it, null, LicenseDataIncludeFlags(includeAcl = true)) }
                .singleOrNull()
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }

    suspend fun browse(
        db: AsyncDBSessionFactory,
        actor: Actor,
        project: String?,
        pagination: NormalizedPaginationRequestV2,
        flags: LicenseDataIncludeFlags,
        filters: LicenseFilters,
    ): PageV2<License> {
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
                            setParameter("provider", filters.provider)
                        },
                        """
                            declare c cursor for
                            select *
                            from app_orchestrator.licenses i
                            where
                                (
                                    :isSystem or
                                    (i.owner_project is null and :project::text is null and i.owner_username = :username) or
                                    (i.owner_project = :project)
                                ) and
                                (:provider::text is null or i.product_provider = :provider)
                            order by
                                i.created_at desc
                        """
                    )
            },
            mapper = { session, rows -> mapRows(session, rows, filters, flags) }
        )
    }

    private suspend fun mapRows(
        session: AsyncDBConnection,
        rows: ResultSet,
        filters: LicenseFilters?,
        flags: LicenseDataIncludeFlags,
    ): List<License> {
        val ids = rows
            .map { it.getField(LicenseTable.id) }
            .toSet()
            .toList()

        var licenses = rows.map {
            License(
                it.getField(LicenseTable.id),
                LicenseSpecification(
                    ProductReference(
                        it.getField(LicenseTable.productId),
                        it.getField(LicenseTable.productCategory),
                        it.getField(LicenseTable.productProvider),
                    )
                ),
                ResourceOwner(it.getField(LicenseTable.ownerUsername), it.getField(LicenseTable.ownerProject)),
                it.getField(LicenseTable.createdAt).toDateTime().millis,
                LicenseStatus(
                    LicenseState.valueOf(it.getField(LicenseTable.currentState))
                ),
                LicenseBilling(
                    it.getField(LicenseTable.productPricePerUnit),
                    it.getField(LicenseTable.creditsCharged),
                ),
                emptyList(),
                acl =
                    if (flags.includeAcl == true) defaultMapper.decodeFromString(it.getField(LicenseTable.acl))
                    else null
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
                        from license_updates
                        where license_id in (select unnest(:ids::text[]))
                        order by timestamp desc
                    """
                )
                .rows
                .map {
                    val id = it.getField(LicenseUpdatesTable.licenseId)

                    id to LicenseUpdate(
                        it.getField(LicenseUpdatesTable.timestamp).toDateTime().millis,
                        it.getFieldNullable(LicenseUpdatesTable.state)?.let { LicenseState.valueOf(it) },
                        it.getFieldNullable(LicenseUpdatesTable.status),
                    )
                }
                .groupBy { it.first }
                .mapValues { all -> all.value.map { it.second } }

            licenses = licenses.map { it.copy(updates = allUpdates.getValue(it.id)) }
        }

        if (filters?.tag != null) {
            val provider = filters.provider ?: error("Internal error. Was validateFilters() not called?")
            val products = products.productsByProvider.get(provider) ?: emptyList()
            val licenseProducts = products.filterIsInstance<Product.License>()
            val viableProducts = licenseProducts
                .filter { it.tags.contains(filters.tag) }
                .map { Pair(it.id, it.category.provider) }
                .toSet()

            licenses = licenses.filter {
                Pair(it.specification.product.id, it.specification.product.provider) in viableProducts
            }
        }

        if (flags.includeProduct == true) {
            licenses = licenses.map {
                it.copy(
                    resolvedProduct = products.find(
                        it.specification.product.provider,
                        it.specification.product.id,
                        it.specification.product.category
                    )
                )
            }
        }

        return licenses
    }
}
