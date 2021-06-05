package dk.sdu.cloud.accounting.util

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductArea
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

suspend fun <T : Resource<*>> DBContext.paginateResource(
    actorAndProject: ActorAndProject,
    request: NormalizedPaginationRequestV2,
    serializer: KSerializer<T>,
    mapper: suspend (T) -> T = { it },
    extraArgs: (EnhancedPreparedStatement.() -> Unit)? = null,
    query: () -> String,
): PageV2<T> {
    return paginateV2(
        actorAndProject.actor,
        request,
        create = { session ->
            session.sendPreparedStatement(
                {
                    setParameter("user", actorAndProject.actor.safeUsername())
                    setParameter("project", actorAndProject.project)
                    extraArgs?.invoke(this)
                },
                query()
            )
        },
        mapper = { _, rows ->
            rows.map { mapper(defaultMapper.decodeFromString(serializer, it.getString(0)!!)) }
        }
    )
}

abstract class ResourceService<
    Res : Resource<*>,
    Spec : ResourceSpecification,
    Update : ResourceUpdate,
    Flags : ResourceIncludeFlags,
    Status : ResourceStatus,
    Prod : Product,
    Support : ProductSupport,
    Comms : ProviderComms>(
    protected val db: AsyncDBSessionFactory,
    protected val providers: Providers<Comms>,
    protected val support: ProviderSupport<Comms, Prod, Support>,
    protected val serviceClient: AuthenticatedClient,
) : ResourceSvc<Res, Flags, Spec, Update, Prod, Support> {
    protected abstract val table: String
    protected abstract val sortColumn: String
    protected abstract val serializer: KSerializer<Res>
    protected open val resourceType: String get() = table.substringAfterLast('.').removePluralSuffix()
    protected open val sqlJsonConverter: String get() = table.removePluralSuffix() + "_to_json"

    private fun String.removePluralSuffix(): String {
        return when {
            endsWith("es") -> removeSuffix("es")
            endsWith("s") -> removeSuffix("s")
            else -> this
        }
    }

    abstract val productArea: ProductArea

    protected abstract fun providerApi(
        comms: ProviderComms
    ): ResourceProviderApi<Res, Spec, Update, Flags, Status, Prod, Support>

    protected val proxy = ProviderProxy<Comms, Prod, Support, Res>(providers, support)
    protected val payment = PaymentService(db, serviceClient)

    override suspend fun browse(
        actorAndProject: ActorAndProject,
        request: WithPaginationRequestV2,
        flags: Flags?,
        ctx: DBContext?
    ): PageV2<Res> {
        return (ctx ?: db).paginateV2(
            actorAndProject.actor,
            request.normalize(),
            create = { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("resource_type", resourceType)
                        setParameter("table", table)
                        setParameter("sort_column", sortColumn)
                        setParameter("to_json", sqlJsonConverter)

                        setParameter("include_others", flags?.includeOthers ?: false)
                        setParameter("include_updates", flags?.includeUpdates ?: false)

                        setParameter("user", actorAndProject.actor.safeUsername())
                        setParameter("project", actorAndProject.project)
                    },
                    "select provider.default_browse(:resource_type, :table, :sort_column, :to_json, :user, :project, " +
                        ":include_others, :include_updates)"
                )
            },
            mapper = { _, rows ->
                rows.mapNotNull {
                    try {
                        defaultMapper.decodeFromString(serializer, it.getString(0)!!)
                    } catch (ex: Throwable) {
                        log.warn("Caught exception while browsing resource: ${it.getString(0)}")
                        log.warn(ex.stackTraceToString())
                        null
                    }
                }.attachSupport(flags)
            }
        )
    }

    override suspend fun retrieve(
        actorAndProject: ActorAndProject,
        id: String,
        flags: Flags?,
        ctx: DBContext?
    ): Res {
        val convertedId = id.toLongOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        return (ctx ?: db).withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("resource_type", resourceType)
                        setParameter("table", table)
                        setParameter("to_json", sqlJsonConverter)

                        setParameter("include_others", flags?.includeOthers ?: false)
                        setParameter("include_updates", flags?.includeUpdates ?: false)

                        setParameter("user", actorAndProject.actor.safeUsername())
                        setParameter("id", convertedId)
                    },
                    """
                        select provider.default_retrieve(:resource_type, :table, :to_json, :user, :id, 
                            :include_others, :include_updates)
                    """
                )
                .rows
                .singleOrNull()
                ?.let {
                    try {
                        defaultMapper.decodeFromString(serializer, it.getString(0)!!)
                    } catch (ex: Throwable) {
                        log.warn("Caught exception while retrieving resource: ${it.getString(0)}")
                        log.warn(ex.stackTraceToString())
                        null
                    }
                }
                ?.attachSupport(flags)
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }

    protected open suspend fun retrieveBulk(
        actorAndProject: ActorAndProject,
        ids: Collection<String>,
        flags: Flags?,
        vararg permissionOneOf: Permission,
        requireAll: Boolean = true,
        includeUpdates: Boolean = false,
        includeOthers: Boolean = false,
        includeSupport: Boolean = false,
        ctx: DBContext? = null,
    ): List<Res> {
        return (ctx ?: db).withSession { session ->
            val result = session
                .sendPreparedStatement(
                    {
                        setParameter("resource_type", resourceType)
                        setParameter("table", table)
                        setParameter("to_json", sqlJsonConverter)

                        setParameter("include_others", flags?.includeOthers ?: includeOthers)
                        setParameter("include_updates", flags?.includeUpdates ?: includeUpdates)

                        setParameter("user", actorAndProject.actor.safeUsername())
                        setParameter("ids", ids.mapNotNull { it.toLongOrNull() })
                        setParameter("permissions", permissionOneOf.map { it.name })
                    },
                    """
                        select * from provider.default_bulk_retrieve(:resource_type, :table, :to_json, :user, :ids, 
                            :permissions, :include_others, :include_updates)
                    """
                )
                .rows
                .asSequence()
                .map { defaultMapper.decodeFromString(serializer, it.getString(0)!!) }
                .filter {
                    if (permissionOneOf.singleOrNull() == Permission.Provider) {
                        // Admin isn't enough if we looking for Provider
                        if (Permission.Provider !in (it.permissions?.myself ?: emptyList())) {
                            return@filter false
                        }
                    }
                    return@filter true
                }
                .toList()

            if (requireAll && result.size != ids.size) {
                throw RPCException("Unable to use all requested resources", HttpStatusCode.BadRequest)
            }

            result.attachSupport(flags, includeSupport)
        }
    }

    private suspend fun List<Res>.attachSupport(flags: Flags?, includeSupport: Boolean = false): List<Res> {
        if (!includeSupport && (flags == null || !flags.includeSupport)) return this
        forEach { it.status.support = support.retrieveProductSupport(it.specification.product) }
        return this
    }

    private suspend fun Res.attachSupport(flags: Flags?, includeSupport: Boolean = false): Res {
        if (!includeSupport && (flags == null || !flags.includeSupport)) return this
        status.support = support.retrieveProductSupport(specification.product)
        return this
    }

    private suspend fun verifyMembership(actorAndProject: ActorAndProject, ctx: DBContext? = null) {
        if (actorAndProject.project == null) return
        (ctx ?: db).withSession { session ->
            val isMember = session
                .sendPreparedStatement(
                    {
                        setParameter("user", actorAndProject.actor.safeUsername())
                        setParameter("project", actorAndProject.project)
                    },
                    "select project.is_member(:user, :project)"
                )
                .rows.singleOrNull()?.getBoolean(0) ?: false

            if (!isMember) {
                throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            }
        }
    }

    protected open fun verifyProviderSupportsCreate(
        spec: Spec,
        res: ProductRefOrResource<Res>,
        support: Support
    ) {}

    protected abstract suspend fun createSpecification(
        resourceId: Long,
        specification: Spec,
        session: AsyncDBConnection,
        allowConflicts: Boolean = false
    )

    override suspend fun create(
        actorAndProject: ActorAndProject,
        request: BulkRequest<Spec>,
    ): BulkResponse<FindByStringId?> {
        val session = db.openSession()
        var lastBatchOfIds: List<Long>? = null
        val adjustedResponse = ArrayList<FindByStringId?>()
        try {
            proxy.bulkProxy(
                { comms -> providerApi(comms).create },
                actorAndProject,
                request,
                isUserRequest = true,
                verifyAndFetchResources = { _, _ ->
                    db.withSession { session ->
                        verifyMembership(actorAndProject, session)
                    }

                    request.items.map { it to ProductRefOrResource.SomeRef(it.product) }
                },
                verifyRequest = this::verifyProviderSupportsCreate,
                beforeCall = { provider, req ->
                    db.openTransaction(session)
                    val generatedIds = session
                        .sendPreparedStatement(
                            {
                                setParameter("type", resourceType)
                                setParameter("provider", provider)
                                setParameter("created_by", actorAndProject.actor.safeUsername())
                                setParameter("project", actorAndProject.project)
                                setParameter("product_ids", req.map { it.second.reference.id })
                                setParameter("product_categories", req.map { it.second.reference.category })
                            },
                            """
                                with product_tuples as (
                                    select unnest(:product_ids::text[]) id, unnest(:product_categories::text[]) cat
                                )
                                insert into provider.resource(type, provider, created_by, project, product) 
                                select :type, :provider, :created_by, :project, p.id
                                from
                                    product_tuples t join 
                                    accounting.product_categories pc on 
                                        pc.category = t.cat and pc.provider = :provider join
                                    accounting.products p on pc.id = p.category and t.id = p.name
                                returning id
                            """
                        )
                        .rows
                        .map { it.getLong(0)!! }

                    check(generatedIds.size == req.size)

                    for ((id, spec) in generatedIds.zip(req)) {
                        createSpecification(id, spec.first, session)
                    }

                    lastBatchOfIds = generatedIds

                    BulkRequest(
                        retrieveBulk(
                            actorAndProject,
                            generatedIds.map { it.toString() },
                            null,
                            Permission.Edit,
                            ctx = session
                        )
                    )
                },
                afterCall = { _, _, resp ->
                    session
                        .sendPreparedStatement(
                            {
                                setParameter("provider_ids", resp.responses.map { it?.id })
                                setParameter("resource_ids", lastBatchOfIds ?: error("Logic error"))
                            },
                            """
                                with backend_ids as (
                                    select
                                        unnest(:provider_ids::text[]) as provider_id, 
                                        unnest(:resource_ids::bigint[]) as resource_id
                                )
                                update provider.resource
                                set provider_generated_id = b.provider_id
                                from backend_ids b
                                where id = b.resource_id
                            """
                        )
                    lastBatchOfIds?.forEach { adjustedResponse.add(FindByStringId(it.toString())) }
                    lastBatchOfIds = null
                    db.commit(session)
                },
                onProviderFailure = { _, req, _ ->
                    req.forEach { _ -> adjustedResponse.add(null) }
                    lastBatchOfIds = null
                    db.rollback(session)
                }
            )

            return BulkResponse(adjustedResponse)
        } finally {
            db.closeSession(session)
        }
    }

    protected open fun verifyProviderSupportsUpdateAcl(
        spec: UpdatedAcl,
        res: ProductRefOrResource<Res>,
        support: Support
    ) {}

    override suspend fun updateAcl(
        actorAndProject: ActorAndProject,
        request: BulkRequest<UpdatedAcl>
    ): BulkResponse<Unit?> {
        val session = db.openSession()
        return try {
            proxy.bulkProxy(
                { comms -> providerApi(comms).updateAcl },
                actorAndProject,
                request,
                isUserRequest = true,
                verifyRequest = this::verifyProviderSupportsUpdateAcl,
                verifyAndFetchResources = { _, _ ->
                    request.items.zip(
                        retrieveBulk(actorAndProject, request.items.map { it.id }, null, Permission.Admin)
                            .map { ProductRefOrResource.SomeResource(it) }
                    )
                },
                beforeCall = { provider, req ->
                    db.openTransaction(session)
                    req.forEach { (acl) ->
                        session
                            .sendPreparedStatement(
                                {
                                    setParameter("id", acl.id.toLongOrNull() ?: error("Logic error"))

                                    val toAddGroups = ArrayList<String?>()
                                    val toAddUsers = ArrayList<String?>()
                                    val toAddPermissions = ArrayList<String>()
                                    acl.added.forEach { entityAndPermissions ->
                                        entityAndPermissions.permissions.forEach p@{
                                            if (!it.canBeGranted) return@p

                                            when (val entity = entityAndPermissions.entity) {
                                                is AclEntity.ProjectGroup -> {
                                                    toAddGroups.add(entity.group)
                                                    toAddUsers.add(null)
                                                }
                                                is AclEntity.User -> {
                                                    toAddGroups.add(null)
                                                    toAddUsers.add(entity.username)
                                                }
                                            }
                                            toAddPermissions.add(it.name)
                                        }
                                    }

                                    setParameter("to_add_groups", toAddGroups)
                                    setParameter("to_add_users", toAddUsers)
                                    setParameter("to_add_users", toAddUsers)

                                    val toRemoveGroups = ArrayList<String?>()
                                    val toRemoveUsers = ArrayList<String?>()
                                    acl.deleted.forEach { entity ->
                                        when (entity) {
                                            is AclEntity.ProjectGroup -> {
                                                toRemoveGroups.add(entity.group)
                                                toRemoveUsers.add(null)
                                            }

                                            is AclEntity.User -> {
                                                toRemoveGroups.add(null)
                                                toRemoveUsers.add(entity.username)
                                            }
                                        }
                                    }

                                    setParameter("to_remove_groups", toRemoveGroups)
                                    setParameter("to_remove_users", toRemoveUsers)
                                },
                                """
                                    select provider.update_acl(:id, :to_add_groups, :to_add_users, 
                                        :to_add_permissions, :to_remove_groups, :to_remove_users)
                                """
                            )
                    }

                    BulkRequest(
                        req.map {
                            UpdatedAclWithResource(
                                (it.second as ProductRefOrResource.SomeResource<Res>).resource,
                                it.first.added,
                                it.first.deleted
                            )
                        }
                    )
                },
                afterCall = { _, _, _ -> db.commit(session) },
                onProviderFailure = { _, _, _ -> db.rollback(session) }
            )
        } finally {
            db.closeSession(session)
        }
    }

    protected open fun verifyProviderSupportsDelete(
        id: FindByStringId,
        res: ProductRefOrResource<Res>,
        support: Support
    ) {}

    protected open suspend fun deleteSpecification(resourceIds: List<Long>, session: AsyncDBConnection) {
        session
            .sendPreparedStatement(
                {
                    setParameter("table", table)
                    setParameter("ids", resourceIds)
                },
                """
                    select provider.default_delete(:table, :id)
                """
            )
    }

    override suspend fun delete(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>
    ): BulkResponse<Unit?> {
        val hasDelete = providerApi(providers.placeholderCommunication).delete != null
        if (!hasDelete) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        val session = db.openSession()
        return try {
            proxy.bulkProxy(
                { comms -> providerApi(comms).delete!! },
                actorAndProject,
                request,
                isUserRequest = true,
                verifyRequest = this::verifyProviderSupportsDelete,
                verifyAndFetchResources = { _, _ ->
                    request.items.zip(
                        retrieveBulk(actorAndProject, request.items.map { it.id }, null, Permission.Edit)
                            .map { ProductRefOrResource.SomeResource(it) }
                    )
                },
                beforeCall = { _, req ->
                    db.openTransaction(session)

                    val ids = req.map { it.first.id.toLong() }
                    val block: EnhancedPreparedStatement.() -> Unit = { setParameter("ids", ids) }

                    deleteSpecification(ids, session)

                    session.sendPreparedStatement(
                        block,
                        "delete from provider.resource_acl_entry where resource_id = some(:ids)"
                    )

                    session.sendPreparedStatement(
                        block,
                        "delete from provider.resource_update where resource = some(:ids)"
                    )

                    session.sendPreparedStatement(
                        block,
                        "delete from provider.resource where id = some(:ids)"
                    )

                    BulkRequest(req.map { ((it.second) as ProductRefOrResource.SomeResource<Res>).resource })
                },
                afterCall = { _, _, _ -> db.commit(session) },
                onProviderFailure = { _, _, _ -> db.rollback(session) }
            )
        } finally {
            db.closeSession(session)
        }
    }

    abstract val updateSerializer: KSerializer<Update>
    open suspend fun onUpdate(
        resources: List<Res>,
        updates: List<ResourceUpdateAndId<Update>>,
        session: AsyncDBConnection
    ) {
    }

    override suspend fun addUpdate(
        actorAndProject: ActorAndProject,
        updates: BulkRequest<ResourceUpdateAndId<Update>>
    ) {
        db.withSession { session ->
            val ids = updates.items.asSequence().map { it.id }.toSet()
            val resources = retrieveBulk(actorAndProject, ids, null, Permission.Provider)

            session
                .sendPreparedStatement(
                    {
                        val resourceIds = ArrayList<Long>()
                        val statusMessages = ArrayList<String?>()
                        val extraMessages = ArrayList<String>()
                        for (update in updates.items) {
                            val rawEncoded = defaultMapper.encodeToJsonElement(updateSerializer, update.update)
                            val encoded = if (rawEncoded is JsonObject) {
                                val entries = HashMap<String, JsonElement>()
                                for ((key, value) in rawEncoded) {
                                    if (key == "timestamp") continue
                                    if (key == "status") continue
                                    entries[key] = value
                                }
                                JsonObject(entries)
                            } else {
                                rawEncoded
                            }

                            extraMessages.add(defaultMapper.encodeToString(encoded))
                            resourceIds.add(update.id.toLong())
                            statusMessages.add(update.update.status)
                        }

                        setParameter("resource_ids", resourceIds)
                        setParameter("status_messages", statusMessages)
                        setParameter("extra_messages", extraMessages)
                    },
                    """
                        insert into provider.resource_update
                        (resource, created_at, status, extra) 
                        select unnest(:resource_ids), now(), unnest(:status_messages), unnest(:extra_messages)
                    """
                )

            onUpdate(resources, updates.items, session)
        }
    }

    override suspend fun register(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ProviderRegisteredResource<Spec>>
    ): BulkResponse<FindByStringId> {
        val provider = request.items.map { it.spec.product.provider }.toSet().singleOrNull()
            ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)

        providers.verifyProvider(provider, actorAndProject.actor)

        return BulkResponse(db.withSession { session ->
            val generatedIds = session
                .sendPreparedStatement(
                    {
                        setParameter("type", resourceType)
                        setParameter("provider", provider)
                        setParameter("created_by", actorAndProject.actor.safeUsername())
                        setParameter("project", actorAndProject.project)
                        setParameter("product_ids", request.items.map { it.spec.product.id })
                        setParameter("product_categories", request.items.map { it.spec.product.category })
                        setParameter("provider_generated_id", request.items.map { it.providerGeneratedId })
                    },
                    """
                        with product_tuples as (
                            select
                                unnest(:product_ids::text[]) id, 
                                unnest(:product_categories::text[]) cat,
                                unnest(:provider_generated_ids) provider_generated_id
                        )
                        insert into provider.resource(type, provider, created_by, project, product, provider_generated_id) 
                        select :type, :provider, :created_by, :project, p.id, t.provider_generated_id
                        from
                            product_tuples t join 
                            accounting.product_categories pc on 
                                pc.category = t.cat and pc.provider = :provider join
                            accounting.products p on pc.id = p.category and t.id = p.name
                        on conflict (provider_generated_id) do update set
                            type = excluded.type,
                            provider = excluded.provider,
                            created_by = excluded.created_by,
                            project = excluded.project,
                            product = excluded.product
                        returning id
                    """
                ).rows.map { it.getLong(0)!! }

            check(generatedIds.size == request.items.size)

            for ((id, req) in generatedIds.zip(request.items)) {
                createSpecification(id, req.spec, session, allowConflicts = true)
            }

            generatedIds.map { FindByStringId(it.toString()) }
        })
    }

    override suspend fun retrieveProducts(actorAndProject: ActorAndProject): SupportByProvider<Prod, Support> {
        val relevantProviders = db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("area", productArea.name)
                        setParameter("project", actorAndProject.project)
                        setParameter("username", actorAndProject.actor.safeUsername())
                    },
                    """
                        with personal_wallets as (
                            select pc.provider
                            from
                                accounting.product_categories pc join
                                accounting.products product on product.category = pc.id left join
                                accounting.wallets w on pc.id = w.category
                            where
                                pc.area = :area and
                                (
                                    product.payment_model = 'FREE_BUT_REQUIRE_BALANCE' or
                                    (
                                        w.account_type = 'USER' and
                                        w.account_id = :username and
                                        w.balance > 0
                                    )
                                )
                        ),
                        project_wallets as (
                            select pc.provider
                            from
                                accounting.product_categories pc left join
                                accounting.products product on product.category = pc.id left join
                                accounting.wallets w on pc.id = w.category join
                                project.projects p on w.account_id = p.id and w.account_type = 'PROJECT' join
                                project.project_members pm on p.id = pm.project_id
                            where
                                pc.area = :area and
                                pm.username = :username and
                                (product.payment_model = 'FREE_BUT_REQUIRE_BALANCE' or w.balance > 0)
                        )
                        select * from personal_wallets union select * from project_wallets
                    """
                )
                .rows
                .map { it.getString(0)!! }
        }

        return SupportByProvider(support.retrieveProducts(relevantProviders))
    }

    override suspend fun chargeCredits(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ResourceChargeCredits>
    ): ResourceChargeCreditsResponse {
        val ids = request.items.asSequence().map { it.id }.toSet()

        val allResources = retrieveBulk(
            actorAndProject,
            ids,
            null,
            Permission.Provider,
            includeSupport = true
        ).associateBy { it.id }

        val chargeResults = ArrayList<Pair<Res, PaymentService.ChargeResult>>()
        for (reqItem in request.items) {
            val resource = allResources.getValue(reqItem.id)
            chargeResults.add(
                resource to payment.charge(
                    Payment(
                        reqItem.chargeId,
                        reqItem.units,
                        resource.status.support!!.product.pricePerUnit,
                        reqItem.id,
                        resource.owner.createdBy,
                        resource.owner.project,
                        resource.specification.product,
                        productArea
                    )
                )
            )
        }

        return ResourceChargeCreditsResponse(
            insufficientFunds = chargeResults
                .filter { (_, result) -> result is PaymentService.ChargeResult.InsufficientFunds }
                .map { FindByStringId(it.first.id) },

            duplicateCharges = chargeResults
                .filter { (_, result) -> result is PaymentService.ChargeResult.Duplicate }
                .map { FindByStringId(it.first.id) },
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
