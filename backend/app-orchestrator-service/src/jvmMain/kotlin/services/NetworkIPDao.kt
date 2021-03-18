package dk.sdu.cloud.app.orchestrator.services

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.jasync.sql.db.ResultSet
import dk.sdu.cloud.Actor
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.provider.api.ResourceAclEntry
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

object NetworkIPUpdatesTable : SQLTable("network_ip_updates") {
    val networkId = text("network_ip_id")
    val timestamp = timestamp("timestamp")
    val state = text("state")
    val status = text("status")
    val changeBinding = bool("change_binding")
    val boundTo = text("bound_to")
    val changeIpAddress = bool("change_ip_address")
    val newIpAddress = text("new_ip_address")
}

object NetworkIPTable : SQLTable("network_ips") {
    val id = text("id")
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
    val acl = jsonb("acl")
    val firewall = jsonb("firewall")
    val ipAddress = text("ip_address")
}

class NetworkIPDao(
    private val products: ProductCache,
) {
    suspend fun create(
        ctx: DBContext,
        network: NetworkIP,
    ) {
        ctx.withSession { session ->
            session.insert(NetworkIPTable) {
                set(NetworkIPTable.id, network.id)
                set(NetworkIPTable.productId, network.specification.product.id)
                set(NetworkIPTable.productCategory, network.specification.product.category)
                set(NetworkIPTable.productProvider, network.specification.product.provider)
                set(NetworkIPTable.productPricePerUnit, network.billing.pricePerUnit)
                set(NetworkIPTable.creditsCharged, network.billing.creditsCharged)
                set(NetworkIPTable.ownerUsername, network.owner.createdBy)
                set(NetworkIPTable.ownerProject, network.owner.project)
                set(NetworkIPTable.currentState, network.status.state.name)
                set(NetworkIPTable.statusBoundTo, network.status.boundTo)
                set(
                    NetworkIPTable.firewall,
                    defaultMapper.encodeToString(network.specification.firewall
                        ?: NetworkIPSpecification.Firewall())
                )
            }

            insertUpdate(
                session,
                network,
                NetworkIPUpdate(
                    Time.now(),
                    state = network.status.state,
                    didBind = true,
                    newBinding = network.status.boundTo
                )
            )

        }
    }

    suspend fun insertUpdate(
        ctx: DBContext,
        id: NetworkIPId,
        update: NetworkIPUpdate,
    ) {
        ctx.withSession { session ->
            session.insert(NetworkIPUpdatesTable) {
                set(NetworkIPUpdatesTable.networkId, id.id)
                set(NetworkIPUpdatesTable.state, update.state?.name)
                set(NetworkIPUpdatesTable.status, update.status)
                set(NetworkIPUpdatesTable.changeBinding, update.didBind)
                set(NetworkIPUpdatesTable.boundTo, update.newBinding)
                set(NetworkIPUpdatesTable.changeIpAddress, update.changeIpAddress == true)
                set(NetworkIPUpdatesTable.newIpAddress, update.newIpAddress)
            }
        }
    }

    suspend fun chargeCredits(
        ctx: DBContext,
        id: NetworkIPId,
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
                        update network_ips
                        set credits_charged = credits_charged + :creditsCharged
                        where id = :id
                    """
                )
                .rowsAffected > 0L

            if (!success) throw RPCException("Unknown network", HttpStatusCode.NotFound)
        }
    }

    suspend fun delete(
        ctx: DBContext,
        ids: List<String>,
    ): List<NetworkIP> {
        return ctx.withSession { session ->
            val params: EnhancedPreparedStatement.() -> Unit = {
                setParameter("ids", ids)
            }

            session.sendPreparedStatement(
                params,
                "delete from network_ip_updates where network_ip_id in (select unnest(:ids::text[]))"
            )

            session
                .sendPreparedStatement(
                    params,
                    "delete from network_ips where id in (select unnest(:ids::text[])) returning *"
                )
                .rows.let { mapRows(session, it, NetworkIPDataIncludeFlags()) }
        }
    }

    suspend fun retrieve(
        ctx: DBContext,
        id: NetworkIPId,
        flags: NetworkIPDataIncludeFlags,
    ): NetworkIP? {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", id.id)
                    }, """
                        select * from network_ips where id = :id
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
        flags: NetworkIPDataIncludeFlags,
        filters: NetworkIPFilters,
    ): PageV2<NetworkIP> {
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
                            from app_orchestrator.network_ips i
                            where
                                (
                                    :isSystem or
                                    (i.owner_project is null and :project::text is null and i.owner_username = :username) or
                                    (i.owner_project = :project)
                                ) and
                                (:provider::text is null or i.product_provider = :provider)
                            order by created_at
                        """
                    )
            },
            mapper = { session, rows -> mapRows(session, rows, flags) }
        )
    }

    private suspend fun mapRows(
        session: AsyncDBConnection,
        rows: ResultSet,
        flags: NetworkIPDataIncludeFlags,
    ): List<NetworkIP> {
        val ids = rows
            .map { it.getField(NetworkIPTable.id) }
            .toSet()
            .toList()

        var networks = rows.map {
            NetworkIP(
                it.getField(NetworkIPTable.id),
                NetworkIPSpecification(
                    ProductReference(
                        it.getField(NetworkIPTable.productId),
                        it.getField(NetworkIPTable.productCategory),
                        it.getField(NetworkIPTable.productProvider),
                    ),
                    firewall = defaultMapper.decodeFromString(it.getField(NetworkIPTable.firewall)),
                ),
                NetworkIPOwner(it.getField(NetworkIPTable.ownerUsername), it.getField(NetworkIPTable.ownerProject)),
                it.getField(NetworkIPTable.createdAt).toDateTime().millis,
                NetworkIPStatus(
                    NetworkIPState.valueOf(it.getField(NetworkIPTable.currentState)),
                    boundTo = it.getFieldNullable(NetworkIPTable.statusBoundTo),
                    ipAddress = it.getFieldNullable(NetworkIPTable.ipAddress),
                ),
                NetworkIPBilling(
                    it.getField(NetworkIPTable.productPricePerUnit),
                    it.getField(NetworkIPTable.creditsCharged),
                ),
                emptyList(),
                acl = if (flags.includeAcl == true) defaultMapper.decodeFromString(it.getField(NetworkIPTable.acl)) else null
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
                        from network_ip_updates
                        where network_ip_id in (select unnest(:ids::text[]))
                        order by timestamp desc
                    """
                )
                .rows
                .map {
                    val id = it.getField(NetworkIPUpdatesTable.networkId)

                    id to NetworkIPUpdate(
                        it.getField(NetworkIPUpdatesTable.timestamp).toDateTime().millis,
                        it.getFieldNullable(NetworkIPUpdatesTable.state)?.let { NetworkIPState.valueOf(it) },
                        it.getFieldNullable(NetworkIPUpdatesTable.status),
                        it.getField(NetworkIPUpdatesTable.changeBinding),
                        it.getFieldNullable(NetworkIPUpdatesTable.boundTo)
                    )
                }
                .groupBy { it.first }
                .mapValues { all -> all.value.map { it.second } }

            networks = networks.map { it.copy(updates = allUpdates.getValue(it.id)) }
        }

        if (flags.includeProduct == true) {
            networks = networks.map {
                it.copy(
                    resolvedProduct = products.find(
                        it.specification.product.provider,
                        it.specification.product.id,
                        it.specification.product.category
                    )
                )
            }
        }

        return networks
    }

    suspend fun updateAcl(
        ctx: DBContext,
        id: NetworkIPId,
        acl: List<ResourceAclEntry<NetworkIPPermission>>,
    ): NetworkIP {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("acl", defaultMapper.encodeToString(acl))
                        setParameter("id", id.id)
                    },
                    "update network_ips set acl = :acl::jsonb where id = :id returning *"
                )
                .rows
                .let { mapRows(session, it, NetworkIPDataIncludeFlags(includeAcl = true)) }
                .singleOrNull()
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }

    suspend fun updateFirewall(
        ctx: DBContext,
        id: NetworkIPId,
        firewall: NetworkIPSpecification.Firewall,
    ): NetworkIP {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("firewall", defaultMapper.encodeToString(firewall))
                        setParameter("id", id.id)
                    },
                    "update network_ips set firewall = :firewall::jsonb where id = :id returning *"
                )
                .rows
                .let { mapRows(session, it, NetworkIPDataIncludeFlags(includeAcl = true)) }
                .singleOrNull()
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }
}
