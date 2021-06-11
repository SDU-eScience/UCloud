package dk.sdu.cloud.accounting.util

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductArea
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.Language
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class PartialQuery(
    val arguments: EnhancedPreparedStatement.() -> Unit,
    @Language("sql")
    val query: String,
)

suspend fun <T : Resource<*, *>> DBContext.paginateResource(
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
    Res : Resource<Prod, Support>,
    Spec : ResourceSpecification,
    Update : ResourceUpdate,
    Flags : ResourceIncludeFlags,
    Status : ResourceStatus<Prod, Support>,
    Prod : Product,
    Support : ProductSupport,
    Comms : ProviderComms>(
    protected val db: AsyncDBSessionFactory,
    protected val providers: Providers<Comms>,
    protected val support: ProviderSupport<Comms, Prod, Support>,
    protected val serviceClient: AuthenticatedClient,
) : ResourceSvc<Res, Flags, Spec, Update, Prod, Support> {
    protected abstract val table: SqlObject.Table
    protected abstract val sortColumn: SqlObject.Column
    protected abstract val serializer: KSerializer<Res>
    protected open val resourceType: String by lazy {
        runBlocking {
            db.withSession { session ->
                table.verify({ session }).substringAfterLast('.').removePluralSuffix()
            }
        }
    }
    protected open val sqlJsonConverter: SqlObject.Function by lazy {
        runBlocking {
            db.withSession { session ->
                SqlObject.Function(table.verify({ session }).removePluralSuffix() + "_to_json")
            }
        }
    }

    private fun String.removePluralSuffix(): String {
        return when {
            endsWith("es") -> removeSuffix("es")
            endsWith("s") -> removeSuffix("s")
            else -> this
        }
    }

    abstract val productArea: ProductArea

    abstract fun userApi(): ResourceApi<Res, Spec, Update, Flags, Status, Prod, Support>
    abstract fun controlApi(): ResourceControlApi<Res, Spec, Update, Flags, Status, Prod, Support>
    abstract fun providerApi(comms: ProviderComms): ResourceProviderApi<Res, Spec, Update, Flags, Status, Prod, Support>

    protected val proxy = ProviderProxy<Comms, Prod, Support, Res>(providers, support)
    protected val payment = PaymentService(db, serviceClient)

    @Suppress("SqlResolve")
    override suspend fun browse(
        actorAndProject: ActorAndProject,
        request: WithPaginationRequestV2,
        flags: Flags?,
        ctx: DBContext?
    ): PageV2<Res> {
        val browseQuery = browseQuery(flags)
        return paginatedQuery(browseQuery, actorAndProject, listOf(Permission.Read), flags, request.normalize(), ctx)
    }

    protected open suspend fun browseQuery(flags: Flags?, ): PartialQuery {
        val tableName = table.verify({ db.openSession() }, { db.closeSession(it) })
        return PartialQuery({}, "select * from $tableName")
    }

    override suspend fun retrieve(
        actorAndProject: ActorAndProject,
        id: String,
        flags: Flags?,
        ctx: DBContext?
    ): Res {
        val convertedId = id.toLongOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        val (params, query) = browseQuery(flags)
        val converter = sqlJsonConverter.verify({ db.openSession() }, { db.closeSession(it) })

        val (resourceParams, resourceQuery) = accessibleResources(
            actorAndProject.actor,
            listOf(Permission.Read),
            resourceId = convertedId,
            projectFilter = actorAndProject.project,
            flags = flags
        )

        @Suppress("SqlResolve")
        return (ctx ?: db).withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        resourceParams()
                        params()
                    },
                    """
                        with
                            spec as ($query),
                            accessible_resources as ($resourceQuery)
                        select provider.resource_to_json(resc, $converter(spec))
                        from
                            accessible_resources resc join
                            spec on (resc.r).id = spec.resource
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
        simpleFlags: SimpleResourceIncludeFlags? = null,
        includeUnconfirmed: Boolean = false,
        requireAll: Boolean = true,
        ctx: DBContext? = null,
    ): List<Res> {
        val (params, query) = browseQuery(flags)
        val converter = sqlJsonConverter.verify({ db.openSession() }, { db.closeSession(it) })

        val (resourceParams, resourceQuery) = accessibleResources(
            actorAndProject.actor,
            listOf(Permission.Read),
            includeUnconfirmed = includeUnconfirmed,
            flags = flags
        )

        @Suppress("SqlResolve")
        return (ctx ?: db).withSession { session ->
            val result = session
                .sendPreparedStatement(
                    {
                        params()
                        resourceParams()
                        setParameter("ids", ids.mapNotNull { it.toLongOrNull() })
                    },
                    """
                        with
                            spec as ($query),
                            accessible_resources as ($resourceQuery)
                        select provider.resource_to_json(resc, $converter(spec))
                        from
                            accessible_resources resc join
                            spec on (resc.r).id = spec.resource
                        where
                            spec.resource = some(:ids::bigint[])
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

            result.attachSupport(flags, flags?.includeSupport ?: simpleFlags?.includeSupport ?: false)
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
    ) {
    }

    protected abstract suspend fun createSpecifications(
        idWithSpec: List<Pair<Long, Spec>>,
        session: AsyncDBConnection,
        allowDuplicates: Boolean
    )

    override suspend fun create(
        actorAndProject: ActorAndProject,
        request: BulkRequest<Spec>,
    ): BulkResponse<FindByStringId?> {
        var lastBatchOfIds: List<Long>? = null
        val adjustedResponse = ArrayList<FindByStringId?>()

        proxy.bulkProxy(
            actorAndProject,
            request,
            object : BulkProxyInstructions<Comms, Support, Res, Spec, BulkRequest<Res>, FindByStringId>() {
                override val isUserRequest: Boolean = true

                override fun retrieveCall(comms: Comms) = providerApi(comms).create

                override suspend fun verifyAndFetchResources(
                    actorAndProject: ActorAndProject,
                    request: BulkRequest<Spec>
                ): List<RequestWithRefOrResource<Spec, Res>> {
                    db.withSession { session ->
                        verifyMembership(actorAndProject, session)
                    }

                    return request.items.map { it to ProductRefOrResource.SomeRef(it.product) }
                }

                override suspend fun verifyRequest(request: Spec, res: ProductRefOrResource<Res>, support: Support) {
                    return verifyProviderSupportsCreate(request, res, support)
                }

                override suspend fun beforeCall(
                    provider: String,
                    resources: List<RequestWithRefOrResource<Spec, Res>>
                ): BulkRequest<Res> {
                    return db.withTransaction { session ->
                        val generatedIds = session
                            .sendPreparedStatement(
                                {
                                    setParameter("type", resourceType)
                                    setParameter("provider", provider)
                                    setParameter("created_by", actorAndProject.actor.safeUsername())
                                    setParameter("project", actorAndProject.project)
                                    setParameter("product_ids", resources.map { it.second.reference.id })
                                    setParameter("product_categories", resources.map { it.second.reference.category })
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

                        check(generatedIds.size == resources.size)

                        createSpecifications(generatedIds.zip(resources.map { it.first }), session, false)

                        lastBatchOfIds = generatedIds
                        db.commit(session)

                        BulkRequest(
                            retrieveBulk(
                                actorAndProject,
                                generatedIds.map { it.toString() },
                                null,
                                Permission.Edit,
                                ctx = session,
                                includeUnconfirmed = true
                            )
                        )
                    }
                }

                override suspend fun afterCall(
                    provider: String,
                    resources: List<RequestWithRefOrResource<Spec, Res>>,
                    response: BulkResponse<FindByStringId?>
                ) {
                    db.withTransaction { session ->
                        session
                            .sendPreparedStatement(
                                {
                                    setParameter("provider_ids", response.responses.map { it?.id })
                                    setParameter("resource_ids", lastBatchOfIds ?: error("Logic error"))
                                },
                                """
                                with backend_ids as (
                                    select
                                        unnest(:provider_ids::text[]) as provider_id, 
                                        unnest(:resource_ids::bigint[]) as resource_id
                                )
                                update provider.resource
                                set provider_generated_id = b.provider_id, confirmed_by_provider = true
                                from backend_ids b
                                where id = b.resource_id
                            """
                            )
                        lastBatchOfIds?.forEach { adjustedResponse.add(FindByStringId(it.toString())) }
                        lastBatchOfIds = null
                    }
                }

                override suspend fun onFailure(
                    provider: String,
                    resources: List<RequestWithRefOrResource<Spec, Res>>,
                    cause: Throwable,
                    mappedRequestIfAny: BulkRequest<Res>?
                ) {
                    if (mappedRequestIfAny != null && false) {
                        db.withTransaction { session ->
                            deleteInternal(
                                mappedRequestIfAny.items.map { it.id.toLong() },
                                mappedRequestIfAny.items,
                                session
                            )
                        }
                    }

                    resources.forEach { _ -> adjustedResponse.add(null) }
                    lastBatchOfIds = null
                }

            }
        )

        return BulkResponse(adjustedResponse)
    }

    protected open fun verifyProviderSupportsUpdateAcl(
        spec: UpdatedAcl,
        res: ProductRefOrResource<Res>,
        support: Support
    ) {
    }

    override suspend fun updateAcl(
        actorAndProject: ActorAndProject,
        request: BulkRequest<UpdatedAcl>
    ): BulkResponse<Unit?> {
        val session = db.openSession()
        return try {
            proxy.bulkProxy(
                actorAndProject,
                request,
                object : BulkProxyInstructions<Comms, Support, Res, UpdatedAcl,
                    BulkRequest<UpdatedAclWithResource<Res>>, Unit>() {
                    override val isUserRequest: Boolean = true

                    override fun retrieveCall(comms: Comms) = providerApi(comms).updateAcl

                    override suspend fun verifyAndFetchResources(
                        actorAndProject: ActorAndProject,
                        request: BulkRequest<UpdatedAcl>
                    ): List<RequestWithRefOrResource<UpdatedAcl, Res>> {
                        return request.items.zip(
                            retrieveBulk(actorAndProject, request.items.map { it.id }, null, Permission.Admin)
                                .map { ProductRefOrResource.SomeResource(it) }
                        )
                    }

                    override suspend fun verifyRequest(
                        request: UpdatedAcl,
                        res: ProductRefOrResource<Res>,
                        support: Support
                    ) {
                        return verifyProviderSupportsUpdateAcl(request, res, support)
                    }

                    override suspend fun beforeCall(
                        provider: String,
                        resources: List<RequestWithRefOrResource<UpdatedAcl, Res>>
                    ): BulkRequest<UpdatedAclWithResource<Res>> {
                        db.openTransaction(session)
                        resources.forEach { (acl) ->
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
                                        setParameter("to_add_permissions", toAddPermissions)

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

                        return BulkRequest(
                            resources.map {
                                UpdatedAclWithResource(
                                    (it.second as ProductRefOrResource.SomeResource<Res>).resource,
                                    it.first.added,
                                    it.first.deleted
                                )
                            }
                        )
                    }

                    override suspend fun afterCall(
                        provider: String,
                        resources: List<RequestWithRefOrResource<UpdatedAcl, Res>>,
                        response: BulkResponse<Unit?>
                    ) {
                        db.commit(session)
                    }

                    override suspend fun onFailure(
                        provider: String,
                        resources: List<RequestWithRefOrResource<UpdatedAcl, Res>>,
                        cause: Throwable,
                        mappedRequestIfAny: BulkRequest<UpdatedAclWithResource<Res>>?
                    ) {
                        db.rollback(session)
                    }

                }
            )
        } finally {
            db.closeSession(session)
        }
    }

    protected open fun verifyProviderSupportsDelete(
        id: FindByStringId,
        res: ProductRefOrResource<Res>,
        support: Support
    ) {
    }

    protected open suspend fun deleteSpecification(
        resourceIds: List<Long>,
        resources: List<Res>,
        session: AsyncDBConnection
    ) {
        val tableName = table.verify({ db.openSession() }, { db.closeSession(it) })
        @Suppress("SqlResolve")
        session
            .sendPreparedStatement(
                { setParameter("ids", resourceIds) },
                "delete from $tableName where resource = some(:ids::bigint[]) returning resource"
            )
    }

    private suspend fun deleteInternal(ids: List<Long>, resources: List<Res>, session: AsyncDBConnection) {
        val block: EnhancedPreparedStatement.() -> Unit = { setParameter("ids", ids) }

        deleteSpecification(ids, resources, session)

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
                actorAndProject,
                request,
                object : BulkProxyInstructions<Comms, Support, Res, FindByStringId, BulkRequest<Res>, Unit>() {
                    override val isUserRequest: Boolean = true

                    override fun retrieveCall(comms: Comms) = providerApi(comms).delete!!

                    override suspend fun verifyAndFetchResources(
                        actorAndProject: ActorAndProject,
                        request: BulkRequest<FindByStringId>
                    ): List<RequestWithRefOrResource<FindByStringId, Res>> {
                        return request.items.zip(
                            retrieveBulk(actorAndProject, request.items.map { it.id }, null, Permission.Edit)
                                .map { ProductRefOrResource.SomeResource(it) }
                        )
                    }

                    override suspend fun verifyRequest(
                        request: FindByStringId,
                        res: ProductRefOrResource<Res>,
                        support: Support
                    ) {
                        return verifyProviderSupportsDelete(request, res, support)
                    }

                    override suspend fun beforeCall(
                        provider: String,
                        resources: List<RequestWithRefOrResource<FindByStringId, Res>>
                    ): BulkRequest<Res> {
                        db.openTransaction(session)

                        val ids = resources.map { it.first.id.toLong() }
                        val mappedResources = resources.map {
                            ((it.second) as ProductRefOrResource.SomeResource<Res>).resource
                        }
                        deleteInternal(ids, mappedResources, session)

                        return BulkRequest(mappedResources)
                    }

                    override suspend fun afterCall(
                        provider: String,
                        resources: List<RequestWithRefOrResource<FindByStringId, Res>>,
                        response: BulkResponse<Unit?>
                    ) {
                        db.commit(session)
                    }

                    override suspend fun onFailure(
                        provider: String,
                        resources: List<RequestWithRefOrResource<FindByStringId, Res>>,
                        cause: Throwable,
                        mappedRequestIfAny: BulkRequest<Res>?
                    ) {
                        db.rollback(session)
                    }

                }
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
            val resources = retrieveBulk(actorAndProject, ids, null, Permission.Provider, includeUnconfirmed = true)

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
                        select unnest(:resource_ids::bigint[]), now(), unnest(:status_messages::text[]), unnest(:extra_messages::jsonb[])
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
                                unnest(:provider_generated_ids::text[]) provider_generated_id
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

            createSpecifications(generatedIds.zip(request.items.map { it.spec }), session, allowDuplicates = true)

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
            simpleFlags = SimpleResourceIncludeFlags(includeSupport = true)
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

    override suspend fun search(
        actorAndProject: ActorAndProject,
        request: ResourceSearchRequest<Flags>,
        ctx: DBContext?,
    ): PageV2<Res> {
        val search = searchQuery(request.query, request.flags)
        return paginatedQuery(search, actorAndProject, listOf(Permission.Read), request.flags,
            request.normalize(), ctx)
    }

    open fun searchQuery(query: String, flags: Flags?): PartialQuery {
        throw IllegalStateException(
            "Feature not support. Remember to override searchQuery if search is defined in the API")
    }

    private fun accessibleResources(
        actor: Actor,
        permissionsOneOf: Collection<Permission>,
        resourceId: Long? = null,
        projectFilter: String? = "",
        flags: Flags? = null,
        simpleFlags: SimpleResourceIncludeFlags? = null,
        includeUnconfirmed: Boolean = false
    ): PartialQuery {
        val includeOthers = flags?.includeOthers ?: simpleFlags?.includeOthers ?: false
        val includeUpdates = flags?.includeUpdates ?: simpleFlags?.includeUpdates ?: false
        return PartialQuery(
            {
                setParameter("username", actor.safeUsername())
                setParameter("project_filter", projectFilter)
                setParameter("permissions", permissionsOneOf.map { it.name })
                setParameter("resource_id", resourceId)
                setParameter("resource_type", resourceType)
                setParameter("include_unconfirmed", includeUnconfirmed)
                setParameter("filter_created_by", flags?.filterCreatedBy ?: simpleFlags?.filterCreatedBy)
                setParameter("filter_created_after", flags?.filterCreatedAfter ?: simpleFlags?.filterCreatedAfter)
                setParameter("filter_created_before", flags?.filterCreatedBefore ?: simpleFlags?.filterCreatedBefore)
                setParameter("filter_provider", flags?.filterProvider ?: simpleFlags?.filterProvider)
                setParameter("filter_product_id", flags?.filterProductId ?: simpleFlags?.filterProductId)
                setParameter(
                    "filter_product_category",
                    flags?.filterProductCategory ?: simpleFlags?.filterProductCategory
                )
            },
            buildString {
                append(
                    """
                        select distinct
                            r,
                            the_product.name,
                            p_cat.category,
                            p_cat.provider,
                            array_agg(
                                distinct
                                case
                                    when pm.role = 'PI' then 'ADMIN'
                                    when pm.role = 'ADMIN' then 'ADMIN'
                                    when r.created_by = :username and r.project is null then 'ADMIN'
                                    when :username = '#P_' || r.provider then 'PROVIDER'
                                    else acl.permission
                                end
                            ) as permissions,
                            
                    """
                )

                if (includeOthers) {
                    append("array_remove(array_agg(distinct other_acl), null),")
                } else {
                    append("array[]::provider.resource_acl_entry[],")
                }

                if (includeUpdates) {
                    append("array_remove(array_agg(distinct u), null) as updates")
                } else {
                    append("array[]::provider.resource_update[]")
                }

                append(
                    """
                        from
                           provider.resource r join
                           accounting.products the_product on r.product = the_product.id join
                           accounting.product_categories p_cat on the_product.category = p_cat.id left join
                           provider.resource_acl_entry acl on r.id = acl.resource_id left join
                           project.projects p on r.project = p.id left join
                           project.project_members pm on p.id = pm.project_id and pm.username = :username left join
                           project.groups g on pm.project_id = g.project and acl.group_id = g.id left join
                           project.group_members gm on g.id = gm.group_id and gm.username = :username
                           
                    """
                )

                if (includeOthers) {
                    append(" left join provider.resource_acl_entry other_acl on r.id = other_acl.resource_id ")
                }

                if (includeUpdates) {
                    append(" left join provider.resource_update u on r.id = u.resource ")
                }

                append(
                    """
                        where
                           (confirmed_by_provider = true or :include_unconfirmed) and
                           (:project_filter = '' or :project_filter is not distinct from r.project) and
                           (:resource_id::bigint is null or r.id = :resource_id) and
                           r.type = :resource_type and
                           (
                               (:username = '#P_' || r.provider) or
                               (r.created_by = :username and r.project is null) or
                               (acl.username = :username) or
                               (pm.role = 'PI' or pm.role = 'ADMIN') or
                               (gm.username is not null)
                          ) and
                          (:filter_created_by::text is null or :filter_created_by = r.created_by) and
                          (:filter_created_after::bigint is null or r.created_at >= to_timestamp(:filter_created_after::bigint / 1000)) and
                          (:filter_created_before::bigint is null or r.created_at <= to_timestamp(:filter_created_before::bigint / 1000)) and
                          (:filter_provider::text is null or p_cat.provider = :filter_provider) and
                          (:filter_product_id::text is null or the_product.name = :filter_product_id) and
                          (:filter_product_category::text is null or p_cat.category = :filter_product_category)
                                        
                    """
                )

                if (includeOthers) {
                    append(" and other_acl.username is distinct from :username ")
                }

                append(" group by r.*, the_product.name, p_cat.category, p_cat.provider ")

                append(
                    """
                        having
                           (:permissions || array['ADMIN', 'PROVIDER']) && array_agg(
                                distinct
                                case
                                    when pm.role = 'PI' then 'ADMIN'
                                    when pm.role = 'ADMIN' then 'ADMIN'
                                    when r.created_by = :username and r.project is null then 'ADMIN'
                                    when :username = '#P_' || r.provider then 'PROVIDER'
                                    else acl.permission
                                end
                            )
                    """
                )
            }
        )
    }

    private suspend fun paginatedQuery(
        partialQuery: PartialQuery,
        actorAndProject: ActorAndProject,
        permissionsOneOf: Collection<Permission>,
        flags: Flags?,
        pagination: NormalizedPaginationRequestV2,
        ctx: DBContext?
    ): PageV2<Res> {
        val (params, query) = partialQuery
        val converter = sqlJsonConverter.verify({ db.openSession() }, { db.closeSession(it) })
        val sortBy = sortColumn.verify({ db.openSession() }, { db.closeSession(it) })

        val (resourceParams, resourceQuery) = accessibleResources(
            actorAndProject.actor,
            permissionsOneOf,
            projectFilter = actorAndProject.project,
            flags = flags
        )

        @Suppress("SqlResolve")
        return (ctx ?: db).paginateV2(
            actorAndProject.actor,
            pagination,
            create = { session ->
                session.sendPreparedStatement(
                    {
                        resourceParams()
                        params()
                    },
                    """
                        declare c cursor for
                        with
                            spec as ($query),
                            accessible_resources as ($resourceQuery)
                        select provider.resource_to_json(resc, $converter(spec))
                        from
                            accessible_resources resc join
                            spec on (resc.r).id = spec.resource
                        order by
                            spec.$sortBy
                    """,
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

    companion object : Loggable {
        override val log = logger()
    }
}
