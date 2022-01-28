package dk.sdu.cloud.accounting.util

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductArea
import dk.sdu.cloud.accounting.api.WalletOwner
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.service.db.withTransaction
import kotlinx.coroutines.delay
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
    protected open val isCoreResource: Boolean = false
    protected abstract val table: SqlObject.Table
    protected abstract val sortColumns: Map<String, SqlObject.Column>
    protected abstract val defaultSortColumn: SqlObject.Column
    protected open val defaultSortDirection: SortDirection = SortDirection.ascending
    protected abstract val serializer: KSerializer<Res>
    protected open val requireAdminForCreate: Boolean = false

    private val resourceTable = SqlObject.Table("provider.resource")
    private val defaultSortColumns = mapOf(
        "createdAt" to SqlObject.Column(resourceTable, "created_at"),
        "createdBy" to SqlObject.Column(resourceTable, "created_by"),
    )
    private val computedSortColumns: Map<String, SqlObject.Column> by lazy {
        defaultSortColumns + sortColumns
    }


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
            endsWith("ses") -> removeSuffix("es")
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
        request: ResourceBrowseRequest<Flags>,
        useProject: Boolean,
        ctx: DBContext?
    ): PageV2<Res> {
        val browseQuery = browseQuery(actorAndProject, request.flags)
        return paginatedQuery(
            browseQuery,
            actorAndProject,
            listOf(Permission.READ),
            request.flags,
            request,
            request.normalize(),
            useProject,
            ctx
        )
    }

    protected open suspend fun browseQuery(
        actorAndProject: ActorAndProject,
        flags: Flags?,
        query: String? = null
    ): PartialQuery {
        val tableName = table.verify({ db.openSession() }, { db.closeSession(it) })
        return PartialQuery(
            {},
            """
                select t.*
                from
                    accessible_resources resc join
                    $tableName t on (resc.r).id = resource
            """.trimIndent()
        )
    }

    override suspend fun retrieve(
        actorAndProject: ActorAndProject,
        id: String,
        flags: Flags?,
        ctx: DBContext?,
        asProvider: Boolean,
    ): Res {
        val convertedId = id.toLongOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        val (params, query) = browseQuery(actorAndProject, flags)
        val converter = sqlJsonConverter.verify({ db.openSession() }, { db.closeSession(it) })

        val (resourceParams, resourceQuery) = accessibleResources(
            actorAndProject.actor,
            if (asProvider) listOf(Permission.PROVIDER) else listOf(Permission.READ),
            resourceId = convertedId,
            projectFilter = "",
            flags = flags,
            includeUnconfirmed = asProvider,
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
                            accessible_resources as ($resourceQuery),
                            spec as ($query)
                        select provider.resource_to_json(resc, $converter(spec))
                        from
                            accessible_resources resc join
                            spec on (resc.r).id = spec.resource
                    """,
                    "${this::class.simpleName} retrieve"
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
                ?.attachExtra(flags)
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }

    open suspend fun retrieveBulk(
        actorAndProject: ActorAndProject,
        ids: Collection<String>,
        permissionOneOf: Collection<Permission>,
        flags: Flags? = null,
        simpleFlags: SimpleResourceIncludeFlags? = null,
        includeUnconfirmed: Boolean = false,
        requireAll: Boolean = true,
        ctx: DBContext? = null,
        useProject: Boolean = false,
    ): List<Res> {
        if (permissionOneOf.isEmpty()) throw IllegalArgumentException("Must specify at least one permission")

        val (params, query) = browseQuery(actorAndProject, flags)
        val converter = sqlJsonConverter.verify({ db.openSession() }, { db.closeSession(it) })

        val (resourceParams, resourceQuery) = accessibleResources(
            actorAndProject.actor,
            permissionOneOf,
            includeUnconfirmed = includeUnconfirmed,
            flags = flags,
            projectFilter = if (useProject) actorAndProject.project else "",
            simpleFlags = (simpleFlags ?: SimpleResourceIncludeFlags()).copy(filterIds = ids.mapNotNull { it.toLongOrNull() }.joinToString(","))
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
                            accessible_resources as ($resourceQuery),
                            spec as ($query)
                        select provider.resource_to_json(resc, $converter(spec))
                        from
                            accessible_resources resc join
                            spec on (resc.r).id = spec.resource
                        where
                            spec.resource = some(:ids::bigint[])
                    """,
                    "${this::class.simpleName} retrieveBulk"
                )
                .rows
                .asSequence()
                .map { defaultMapper.decodeFromString(serializer, it.getString(0)!!) }
                .filter {
                    if (permissionOneOf.singleOrNull() == Permission.PROVIDER && actorAndProject.actor != Actor.System) {
                        // Admin isn't enough if we are looking for Provider
                        if (Permission.PROVIDER !in (it.permissions?.myself ?: emptyList())) {
                            return@filter false
                        }
                    }
                    return@filter true
                }
                .toList()

            if (requireAll && result.size != ids.size) {
                throw RPCException("Unable to use all requested resources", HttpStatusCode.BadRequest)
            }

            result.attachExtra(flags, flags?.includeSupport ?: simpleFlags?.includeSupport ?: false)
        }
    }

    private suspend fun List<Res>.attachExtra(flags: Flags?, includeSupport: Boolean = false): List<Res> =
        onEach { it.attachExtra(flags, includeSupport) }

    private suspend fun Res.attachExtra(flags: Flags?, includeSupport: Boolean = false): Res {
        if (specification.product.provider != Provider.UCLOUD_CORE_PROVIDER) {
            if (includeSupport || flags?.includeSupport == true) {
                val retrieveProductSupport = support.retrieveProductSupport(specification.product)
                status.resolvedSupport = retrieveProductSupport
                status.resolvedProduct = retrieveProductSupport.product
            }

            if (flags?.includeProduct == true) {
                status.resolvedProduct = support.retrieveProductSupport(specification.product).product
            }
        }
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

    protected open suspend fun verifyProviderSupportsCreate(
        spec: Spec,
        res: ProductRefOrResource<Res>,
        support: Support
    ) {
        // Empty by default
    }

    protected open suspend fun isResourcePublicRead(
        actorAndProject: ActorAndProject,
        specs: List<Spec>,
        session: AsyncDBConnection,
    ): List<Boolean> = specs.map { false }

    protected abstract suspend fun createSpecifications(
        actorAndProject: ActorAndProject,
        idWithSpec: List<Pair<Long, Spec>>,
        session: AsyncDBConnection,
        allowDuplicates: Boolean
    )

    override suspend fun create(
        actorAndProject: ActorAndProject,
        request: BulkRequest<Spec>,
        ctx: DBContext?
    ): BulkResponse<FindByStringId?> {
        var lastBatchOfIds: List<Long>? = null
        val adjustedResponse = ArrayList<FindByStringId?>()

        val shouldCloseEarly = !(ctx != null && ctx is AsyncDBConnection)
        if (!shouldCloseEarly && !isCoreResource) {
            throw IllegalStateException("Unable to re-use existing database session for non-core resources!")
        }

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
                    (ctx as? AsyncDBConnection ?: db).withSession { session ->
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
                    return (ctx as? AsyncDBConnection ?: db).withSession(remapExceptions = true) { session ->
                        if (!isCoreResource) {
                            val project = actorAndProject.project
                            payment.creditCheck(
                                if (project != null) {
                                    WalletOwner.Project(project)
                                } else {
                                    WalletOwner.User(actorAndProject.actor.safeUsername())
                                },
                                resources.map { it.second.reference }
                            )
                        }

                        val isPublicRead = isResourcePublicRead(actorAndProject, resources.map { it.first }, session)
                        val generatedIds = session
                            .sendPreparedStatement(
                                {
                                    setParameter("type", resourceType)
                                    setParameter("provider", provider.takeIf { it != Provider.UCLOUD_CORE_PROVIDER })
                                    setParameter("created_by", actorAndProject.actor.safeUsername())
                                    setParameter("project", actorAndProject.project)
                                    setParameter("product_ids", resources.map { it.second.reference.id })
                                    setParameter("product_categories", resources.map { it.second.reference.category })
                                    setParameter("is_public_read", isPublicRead)
                                    setParameter("auto_confirm", provider == Provider.UCLOUD_CORE_PROVIDER)
                                },
                                """
                                    with
                                        product_tuples as (
                                            select
                                                unnest(:product_ids::text[]) id,
                                                unnest(:product_categories::text[]) cat,
                                                unnest(:is_public_read::boolean[]) public_read
                                        ),
                                        created_resources as (
                                            insert into provider.resource
                                                (type, provider, created_by, project, product, public_read, 
                                                 confirmed_by_provider) 
                                            select :type, :provider, :created_by, :project, p.id, t.public_read, :auto_confirm
                                            from
                                                product_tuples t left join 
                                                accounting.product_categories pc on 
                                                    pc.category = t.cat and pc.provider = :provider left join
                                                accounting.products p on pc.id = p.category and t.id = p.name
                                            returning id, created_by
                                        ),
                                        acl_entries as (
                                            insert into provider.resource_acl_entry (username, permission, resource_id) 
                                            select created_by, unnest(array['READ', 'EDIT']), id
                                            from created_resources
                                            returning resource_id
                                        )
                                    select distinct resource_id from acl_entries;
                                """,
                                "${this::class.simpleName} create 1"
                            )
                            .rows
                            .map { it.getLong(0)!! }

                        check(generatedIds.size == resources.size)

                        createSpecifications(
                            actorAndProject,
                            generatedIds.zip(resources.map { it.first }),
                            session,
                            false
                        )

                        lastBatchOfIds = generatedIds
                        if (shouldCloseEarly) db.commit(session)

                        BulkRequest(
                            retrieveBulk(
                                actorAndProject,
                                generatedIds.map { it.toString() },
                                if (requireAdminForCreate) listOf(Permission.ADMIN) else listOf(Permission.EDIT),
                                ctx = session,
                                includeUnconfirmed = true,
                                simpleFlags = SimpleResourceIncludeFlags(includeSupport = true)
                            )
                        )
                    }
                }

                override suspend fun afterCall(
                    provider: String,
                    resources: List<RequestWithRefOrResource<Spec, Res>>,
                    response: BulkResponse<FindByStringId?>
                ) {
                    if (response.responses.any { it?.id?.contains(",") == true }) {
                        throw RPCException("Provider generated ID cannot contain ','", HttpStatusCode.BadGateway)
                    }

                    (ctx as? AsyncDBConnection ?: db).withSession { session ->
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
                                """,
                                "${this::class.simpleName} create 2"
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
                    if (mappedRequestIfAny != null && shouldCloseEarly) {
                        db.withTransaction { session ->
                            deleteFromDatabaseSkipProvider(
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
        // Empty by default
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
                            retrieveBulk(actorAndProject, request.items.map { it.id }, listOf(Permission.ADMIN))
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
                                    """,
                                    "${this::class.simpleName} updateAcl"
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
        // Empty by default
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

    suspend fun deleteFromDatabaseSkipProvider(ids: List<Long>, resources: List<Res>, session: AsyncDBConnection) {
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
                            retrieveBulk(actorAndProject, request.items.map { it.id }, listOf(Permission.EDIT))
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
                        deleteFromDatabaseSkipProvider(ids, mappedResources, session)

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
        // Empty by default
    }

    override suspend fun addUpdate(
        actorAndProject: ActorAndProject,
        updates: BulkRequest<ResourceUpdateAndId<Update>>,
        requireAll: Boolean,
    ) {
        db.withSession { session ->
            val ids = updates.items.asSequence().map { it.id }.toSet()
            val resources = retrieveBulk(
                actorAndProject,
                ids,
                listOf(Permission.PROVIDER),
                includeUnconfirmed = true,
                requireAll = requireAll,
            )

            session
                .sendPreparedStatement(
                    {
                        val resourceIds = ArrayList<Long>()
                        val statusMessages = ArrayList<String?>()
                        val extraMessages = ArrayList<String>()
                        for (update in updates.items) {
                            resources.find { it.id == update.id } ?: continue

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
                    """,
                    "${this::class.simpleName} add update"
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

        if (request.items.any { it.providerGeneratedId?.contains(",") == true }) {
            throw RPCException("Provider generated ID cannot contain ','", HttpStatusCode.BadRequest)
        }

        return BulkResponse(db.withSession(remapExceptions = true) { session ->
            val generatedIds = session
                .sendPreparedStatement(
                    {
                        setParameter("type", resourceType)
                        setParameter("provider", provider)
                        request.items.split {
                            into("product_ids") { it.spec.product.id }
                            into("product_categories") { it.spec.product.category }
                            into("provider_generated_ids") { it.providerGeneratedId }
                            into("created_by") { it.createdBy ?: Actor.System.safeUsername() }
                            into("projects") { it.project }
                        }
                    },
                    """
                        with
                            product_tuples as (
                                select
                                    unnest(:product_ids::text[]) id, 
                                    unnest(:product_categories::text[]) cat,
                                    unnest(:provider_generated_ids::text[]) provider_generated_id,
                                    unnest(:created_by::text[]) created_by,
                                    unnest(:projects::text[]) project
                            ),
                            created_resources as (
                                insert into provider.resource
                                    (type, provider, created_by, project, product, provider_generated_id, confirmed_by_provider) 
                                select :type, :provider, t.created_by, t.project, p.id, t.provider_generated_id, true
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
                                returning id, created_by    
                            ),
                            acl_entries as (
                                insert into provider.resource_acl_entry (username, permission, resource_id) 
                                select created_by, unnest(array['READ', 'EDIT']), id
                                from created_resources
                                -- This no-op ensures that resource_id is returned in case on conflicts
                                on conflict (coalesce(username, ''), coalesce(group_id, ''), resource_id, permission) do update set username = excluded.username
                                returning resource_id
                            )
                        select distinct resource_id from acl_entries;
                    """,
                    debug = true
                ).rows.map { it.getLong(0)!! }

            check(generatedIds.size == request.items.size, lazyMessage = {"Might be missing product"} )

            createSpecifications(
                actorAndProject,
                generatedIds.zip(request.items.map { it.spec }),
                session,
                allowDuplicates = true
            )

            generatedIds.map { FindByStringId(it.toString()) }
        })
    }

    override suspend fun init(actorAndProject: ActorAndProject) {
        val owner = ResourceOwner(actorAndProject.actor.safeUsername(), actorAndProject.project)
        val relevantProviders = findRelevantProviders(actorAndProject)
        relevantProviders.forEach { provider ->
            val comms = providers.prepareCommunication(provider)
            val api = providerApi(comms)

            // NOTE(Dan): Ignore failures as they commonly indicate that it is not supported.
            for (attempt in 0 until 5) {
                val resp =
                    api.init.call(ResourceInitializationRequest(owner), comms.client.withProxyInfo(owner.createdBy))

                if (resp.statusCode.value == 449 || resp.statusCode == HttpStatusCode.ServiceUnavailable) {
                    val im = IntegrationProvider(provider)
                    im.init.call(IntegrationProviderInitRequest(owner.createdBy), comms.client).orThrow()
                    delay(200L + (attempt * 500))
                    continue
                } else {
                    break
                }
            }
        }
    }

    override suspend fun retrieveProducts(actorAndProject: ActorAndProject): SupportByProvider<Prod, Support> {
        val relevantProviders = findRelevantProviders(actorAndProject)
        return SupportByProvider(support.retrieveProducts(relevantProviders))
    }

    private suspend fun findRelevantProviders(actorAndProject: ActorAndProject): List<String> {
        val relevantProviders = db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("area", productArea.name)
                        setParameter("project", actorAndProject.project)
                        setParameter("username", actorAndProject.actor.safeUsername())
                    },
                    """
                            select distinct pc.provider
                            from
                                accounting.product_categories pc join
                                accounting.products product on product.category = pc.id left join
                                accounting.wallets w on pc.id = w.category left join
                                accounting.wallet_owner wo on wo.id = w.owned_by left join
                                project.project_members pm on
                                    wo.project_id = pm.project_id and
                                    pm.project_id = :project::text and
                                    pm.username = :username
                            where
                                pc.product_type = :area::accounting.product_type and
                                (
                                    product.free_to_use or
                                    (
                                        wo.username = :username and
                                        w.id is not null
                                    ) or
                                    (
                                        wo.project_id = :project::text and
                                        w.id is not null
                                    )
                                )
                        """
                )
                .rows
                .map { it.getString(0)!! }
        }
        return relevantProviders
    }

    override suspend fun chargeCredits(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ResourceChargeCredits>,
        checkOnly: Boolean
    ): ResourceChargeCreditsResponse {
        val ids = request.items.asSequence().map { it.id }.toSet()

        val allResources = retrieveBulk(
            actorAndProject,
            ids,
            listOf(Permission.PROVIDER),
            simpleFlags = SimpleResourceIncludeFlags(includeSupport = true),
            includeUnconfirmed = true,
        ).associateBy { it.id }

        val paymentRequests = request.items.map { reqItem ->
            val resource = allResources.getValue(reqItem.id)
            val project = resource.owner.project

            Payment(
                reqItem.chargeId,
                reqItem.periods,
                reqItem.units,
                resource.status.resolvedSupport!!.product.pricePerUnit,
                reqItem.id,
                reqItem.performedBy ?: resource.owner.createdBy,
                if (project != null) {
                    WalletOwner.Project(project)
                } else {
                    WalletOwner.User(resource.owner.createdBy)
                },
                resource.specification.product,
                reqItem.description,
                reqItem.chargeId
            )
        }

        val chargeResult =
            if (checkOnly) payment.creditCheckForPayments(paymentRequests)
            else payment.charge(paymentRequests)

        val insufficient = chargeResult.mapIndexedNotNull { index, result ->
            val request = request.items[index]
            when (result) {
                PaymentService.ChargeResult.Charged -> null
                PaymentService.ChargeResult.Duplicate -> null
                PaymentService.ChargeResult.InsufficientFunds -> FindByStringId(request.id)
            }
        }

        return ResourceChargeCreditsResponse(insufficient, emptyList())
    }

    override suspend fun search(
        actorAndProject: ActorAndProject,
        request: ResourceSearchRequest<Flags>,
        ctx: DBContext?,
    ): PageV2<Res> {
        val search = browseQuery(actorAndProject, request.flags, request.query)
        return paginatedQuery(
            search, actorAndProject, listOf(Permission.READ), request.flags,
            request, request.normalize(), true, ctx
        )
    }

    protected fun accessibleResources(
        actor: Actor,
        permissionsOneOf: Collection<Permission>,
        resourceId: Long? = null,
        projectFilter: String? = "",
        flags: Flags? = null,
        simpleFlags: SimpleResourceIncludeFlags? = null,
        includeUnconfirmed: Boolean = false,
    ): PartialQuery {
        val includeOthers = flags?.includeOthers ?: simpleFlags?.includeOthers ?: false
        val includeUpdates = flags?.includeUpdates ?: simpleFlags?.includeUpdates ?: false
        return PartialQuery(
            {
                setParameter("username", actor.safeUsername("_ucloud"))
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
                setParameter("hide_provider", flags?.hideProvider ?: simpleFlags?.hideProvider)
                setParameter("hide_product_id", flags?.hideProductId ?: simpleFlags?.hideProductId)
                setParameter(
                    "hide_product_category",
                    flags?.hideProductCategory ?: simpleFlags?.hideProductCategory
                )
                setParameter(
                    "filter_provider_ids",
                    (flags?.filterProviderIds ?: simpleFlags?.filterProviderIds)?.split(",")
                )
                setParameter(
                    "filter_ids", 
                    (flags?.filterIds ?: simpleFlags?.filterIds)?.let { ids ->
                        ids.split(",").mapNotNull { it.toLongOrNull() }
                    }
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
                                    when :username = '_ucloud' then 'ADMIN'
                                    when pm.role = 'PI' then 'ADMIN'
                                    when pm.role = 'ADMIN' then 'ADMIN'
                                    when r.created_by = :username and r.project is null then 'ADMIN'
                                    when :username = '#P_' || r.provider then 'PROVIDER'
                                    when r.public_read and acl.permission is null then 'READ'
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
                           provider.resource r left join
                           accounting.products the_product on r.product = the_product.id left join
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
                           (r.public_read or :project_filter = '' or :project_filter is not distinct from r.project) and
                           (:resource_id::bigint is null or r.id = :resource_id) and
                           r.type = :resource_type and
                           (
                                (:username = '_ucloud') or
                                (:username = '#P_' || r.provider) or
                                (r.created_by = :username and r.project is null) or
                                (
                                    acl.username = :username and
                                    (
                                        r.project is null or
                                        pm.username = :username
                                    )
                                ) or
                                (pm.role = 'PI' or pm.role = 'ADMIN') or
                                (gm.username is not null) or
                                (r.public_read = true)
                          ) and
                          (:filter_created_by::text is null or r.created_by like '%' || :filter_created_by || '%') and
                          (:filter_created_after::bigint is null or r.created_at >= to_timestamp(:filter_created_after::bigint / 1000)) and
                          (:filter_created_before::bigint is null or r.created_at <= to_timestamp(:filter_created_before::bigint / 1000)) and
                          (:filter_provider::text is null or p_cat.provider = :filter_provider) and
                          (:filter_product_id::text is null or the_product.name = :filter_product_id) and
                          (:filter_product_category::text is null or p_cat.category = :filter_product_category) and
                          (:hide_provider::text is null or p_cat.provider != :hide_provider) and
                          (:hide_product_id::text is null or the_product.name != :hide_product_id) and
                          (:hide_product_category::text is null or p_cat.category != :hide_product_category) and
                          (
                              :filter_provider_ids::text[] is null or
                              r.provider_generated_id = some(:filter_provider_ids::text[])
                          ) and
                          (:filter_ids::bigint[] is null or r.id = some(:filter_ids::bigint[]))
                                        
                    """
                )

                append(" group by r.*, the_product.name, p_cat.category, p_cat.provider ")

                append(
                    """
                        having
                           (:permissions || array['ADMIN', 'PROVIDER']) && array_agg(
                                distinct
                                case
                                    when :username = '_ucloud' then 'ADMIN'
                                    when pm.role = 'PI' then 'ADMIN'
                                    when pm.role = 'ADMIN' then 'ADMIN'
                                    when r.created_by = :username and r.project is null then 'ADMIN'
                                    when :username = '#P_' || r.provider then 'PROVIDER'
                                    when r.public_read and acl.permission is null then 'READ'
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
        sortFlags: SortFlags?,
        pagination: NormalizedPaginationRequestV2,
        useProject: Boolean,
        ctx: DBContext?
    ): PageV2<Res> {
        val (params, query) = partialQuery
        val converter = sqlJsonConverter.verify({ db.openSession() }, { db.closeSession(it) })
        val columnToSortBy = computedSortColumns[sortFlags?.sortBy ?: ""] ?: defaultSortColumn
        var sortBy = columnToSortBy.verify({ db.openSession() }, { db.closeSession(it) })
        if (sortBy == "created_by") {
            sortBy = "(resc.r).created_by"
        } else if (sortBy == "created_at") {
            sortBy = "(resc.r).created_at"
        }

        val sortDirection = when (sortFlags?.sortDirection ?: defaultSortDirection) {
            SortDirection.ascending -> "asc"
            SortDirection.descending -> "desc"
        }

        val (resourceParams, resourceQuery) = accessibleResources(
            actorAndProject.actor,
            permissionsOneOf,
            projectFilter = if (useProject) actorAndProject.project else "",
            flags = flags,
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
                            accessible_resources as ($resourceQuery),
                            spec as ($query)
                        select provider.resource_to_json(resc, $converter(spec))
                        from
                            accessible_resources resc join
                            spec on (resc.r).id = spec.resource
                        order by
                            $sortBy $sortDirection, resc.category, resc.name 
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
                }.attachExtra(flags)
            },
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
