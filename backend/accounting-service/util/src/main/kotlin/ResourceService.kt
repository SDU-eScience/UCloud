package dk.sdu.cloud.accounting.util

import com.github.jasync.sql.db.ResultSet
import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductType
import dk.sdu.cloud.accounting.api.WalletOwner
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.project.api.v2.FindByProjectId
import dk.sdu.cloud.project.api.v2.Projects
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
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

const val accountingPerformanceMitigations = true

// TODO(Dan): This should probably be moved to `dk.sdu.cloud` since it is of general use
data class PartialQuery(
    val arguments: EnhancedPreparedStatement.() -> Unit,
    @Language("sql")
    val query: String,
)

enum class ResourceBrowseStrategy {
    OLD,
    NEW
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
    protected val projectCache: ProjectCache,
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
    protected open val personalResource: Boolean = false
    protected open val browseStrategy: ResourceBrowseStrategy = ResourceBrowseStrategy.OLD

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

    abstract val productArea: ProductType

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
            // TODO Bad fix for a bug in the new query
            if (actorAndProject.actor.safeUsername().startsWith(AuthProviders.PROVIDER_PREFIX)) {
                listOf(Permission.PROVIDER)
            } else {
                listOf(Permission.READ)
            },
            request.flags,
            request,
            request.normalize(),
            useProject,
            null,
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
        return retrieveBulk(
            actorAndProject,
            listOf(id),
            if (asProvider) listOf(Permission.PROVIDER) else listOf(Permission.READ),
            flags,
            ctx = ctx,
            requireAll = false,
            includeUnconfirmed = asProvider
        ).singleOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
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
        val mappedIds = ids.mapNotNull { it.toLongOrNull() }
        if (mappedIds.isEmpty()) return emptyList()
        if (permissionOneOf.isEmpty()) throw IllegalArgumentException("Must specify at least one permission")

        val (params, query) = browseQuery(actorAndProject, flags)
        val converter = sqlJsonConverter.verify({ db.openSession() }, { db.closeSession(it) })

        val (resourceParams, resourceQuery) = accessibleResources(
            actorAndProject.actor,
            permissionOneOf,
            includeUnconfirmed = includeUnconfirmed,
            flags = flags,
            projectFilter = if (useProject) actorAndProject.project else "",
            simpleFlags = (simpleFlags ?: SimpleResourceIncludeFlags()).copy(
                filterIds = mappedIds.joinToString(",")
            )
        )

        @Suppress("SqlResolve")
        return (ctx ?: db).withSession { session ->
            val rows = when (browseStrategy) {
                ResourceBrowseStrategy.OLD -> {
                    session.sendPreparedStatement(
                        {
                            params()
                            resourceParams()
                            setParameter("ids", mappedIds)
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
                        "${this::class.simpleName} retrieveBulk",
                    )
                        .rows
                }

                ResourceBrowseStrategy.NEW -> {
                    fetchResourcesNew(session, resourceQuery, query, converter) {
                        params()
                        resourceParams()
                    }
                }
            }

            val result = rows
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
                throw RPCException("Permission denied. Try to reload the page and try again.", HttpStatusCode.BadRequest)
            }

            result.attachExtra(
                actorAndProject.actor,
                flags,
                flags?.includeSupport ?: simpleFlags?.includeSupport ?: false
            )
        }
    }

    private suspend fun List<Res>.attachExtra(actor: Actor, flags: Flags?, includeSupport: Boolean = false): List<Res> =
        onEach { it.attachExtra(actor, flags, includeSupport) }

    private suspend fun Res.attachExtra(actor: Actor, flags: Flags?, includeSupport: Boolean = false): Res {
        val perms = permissions
        if (browseStrategy == ResourceBrowseStrategy.NEW && perms != null && perms.myself.isEmpty()) {
            // In this specific case, we need to extract the permissions from the `others` list
            val username = actor.safeUsername()
            val cached = projectCache.lookup(username)
            val actualPermissions = HashSet<Permission>()

            if (owner.createdBy == username) {
                actualPermissions.add(Permission.ADMIN)
            }

            for (admin in cached.adminInProjects) {
                if (owner.project == admin) {
                    actualPermissions.add(Permission.ADMIN)
                }
            }

            for (entry in (perms.others ?: emptyList())) {
                when (val entity = entry.entity) {
                    is AclEntity.User -> {
                        if (entity.username == username) {
                            actualPermissions.addAll(entry.permissions)
                        }
                    }

                    is AclEntity.ProjectGroup -> {
                        for (membership in cached.groupMemberOf) {
                            if (membership.group == entity.group) {
                                actualPermissions.addAll(entry.permissions)
                            }
                        }
                    }
                }
            }

            perms.myself = actualPermissions.toList()
        }

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
        return attachExtraInformation(this, actor, flags)
    }

    // NOTE(Dan): Invoked by the function above. Used by sub-classes to add their own logic to the mapping process.
    protected open suspend fun attachExtraInformation(resource: Res, actor: Actor, flags: Flags?): Res {
        return resource
    }

    private suspend fun verifyCreationInProject(actorAndProject: ActorAndProject, ctx: DBContext? = null, isCoreResource: Boolean = false) {
        if (actorAndProject.project == null) return
        (ctx ?: db).withSession { session ->
            val row = session
                .sendPreparedStatement(
                    {
                        setParameter("user", actorAndProject.actor.safeUsername())
                        setParameter("project", actorAndProject.project)
                    },
                    """
                        select pm.role is not null, p.can_consume_resources
                        from
                            project.projects p join
                            project.project_members pm on p.id = pm.project_id
                        where
                            p.id = :project and
                            pm.username = :user
                    """
                )
                .rows.singleOrNull()

            val isMember = row?.getBoolean(0) == true
            if (!isMember) {
                throw RPCException(
                    "You are not able to create a resource in this project. Please check your permissions.",
                    HttpStatusCode.Forbidden
                )
            }

            val canConsumeResources = row?.getBoolean(1) == true
            if (!canConsumeResources && !isCoreResource) {
                throw RPCException(
                    "This project is not allowed to consume any resources. Please use a different project.",
                    HttpStatusCode.Forbidden
                )
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
        val resolvedActorAndProject =
            if (personalResource) ActorAndProject(actorAndProject.actor, null) else actorAndProject
        var lastBatchOfIds: List<Long>? = null
        val adjustedResponse = ArrayList<FindByStringId?>()

        val shouldCloseEarly = !(ctx != null && ctx is AsyncDBConnection)
        if (!shouldCloseEarly && !isCoreResource) {
            throw IllegalStateException("Unable to re-use existing database session for non-core resources!")
        }

        proxy.bulkProxy(
            resolvedActorAndProject,
            request,
            object : BulkProxyInstructions<Comms, Support, Res, Spec, BulkRequest<Res>, FindByStringId>() {
                override val isUserRequest: Boolean = true

                override fun retrieveCall(comms: Comms) = providerApi(comms).create

                override suspend fun verifyAndFetchResources(
                    actorAndProject: ActorAndProject,
                    request: BulkRequest<Spec>
                ): List<RequestWithRefOrResource<Spec, Res>> {
                    (ctx as? AsyncDBConnection ?: db).withSession { session ->
                        verifyCreationInProject(
                            actorAndProject,
                            session,
                            request.items.all { it.product.provider == Provider.UCLOUD_CORE_PROVIDER }
                        )
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
                        if (!isCoreResource && !accountingPerformanceMitigations) {
                            val project = resolvedActorAndProject.project
                            payment.creditCheck(
                                if (project != null) {
                                    WalletOwner.Project(project)
                                } else {
                                    WalletOwner.User(resolvedActorAndProject.actor.safeUsername())
                                },
                                resources.map { it.second.reference }
                            )
                        }

                        val isPublicRead =
                            isResourcePublicRead(resolvedActorAndProject, resources.map { it.first }, session)
                        val generatedIds = session
                            .sendPreparedStatement(
                                {
                                    setParameter("type", resourceType)
                                    setParameter("provider", provider.takeIf { it != Provider.UCLOUD_CORE_PROVIDER })
                                    setParameter("created_by", resolvedActorAndProject.actor.safeUsername())
                                    setParameter("project", resolvedActorAndProject.project)
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
                            resolvedActorAndProject,
                            generatedIds.zip(resources.map { it.first }),
                            session,
                            false
                        )

                        lastBatchOfIds = generatedIds
                        if (shouldCloseEarly) db.commit(session)

                        BulkRequest(
                            retrieveBulk(
                                resolvedActorAndProject,
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

    open suspend fun onUpdateAcl(
        session: AsyncDBConnection,
        request: BulkRequest<UpdatedAclWithResource<Res>>
    ) {
        // Empty by default
    }

    override suspend fun updateAcl(
        actorAndProject: ActorAndProject,
        request: BulkRequest<UpdatedAcl>
    ): BulkResponse<Unit?> {
        val resolvedActorAndProject =
            if (personalResource) ActorAndProject(actorAndProject.actor, null) else actorAndProject
        val session = db.openSession()
        return try {
            proxy.bulkProxy(
                resolvedActorAndProject,
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

                        val bulkRequest = BulkRequest(resources.map {
                            UpdatedAclWithResource(
                                (it.second as ProductRefOrResource.SomeResource<Res>).resource,
                                it.first.added,
                                it.first.deleted
                            )
                        })

                        onUpdateAcl(session, bulkRequest)

                        return bulkRequest
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
        val resolvedActorAndProject =
            if (personalResource) ActorAndProject(actorAndProject.actor, null) else actorAndProject
        val hasDelete = providerApi(providers.placeholderCommunication).delete != null
        if (!hasDelete) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        val session = db.openSession()
        return try {
            proxy.bulkProxy(
                resolvedActorAndProject,
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
        val resolvedActorAndProject =
            if (personalResource) ActorAndProject(actorAndProject.actor, null) else actorAndProject
        db.withSession { session ->
            val ids = updates.items.asSequence().map { it.id }.toSet()
            val resources = retrieveBulk(
                resolvedActorAndProject,
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
                                on conflict (provider, provider_generated_id) do update set
                                    type = excluded.type,
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
                ).rows.map { it.getLong(0)!! }

            check(generatedIds.size == request.items.size, lazyMessage = { "Might be missing product $request" })

            // NOTE(Dan): Registering projectAllRead and projectAllWrite is a bit more complex. As a result, we don't
            // attempt to put it into the query above. The code starts by finding all the relevant "All users" groups
            // and turns these into appropriate ACL changes.
            val resourcesWhichRequireAllUsersGroup = request.items.filter { it.projectAllRead || it.projectAllWrite }
            if (resourcesWhichRequireAllUsersGroup.isNotEmpty()) {
                val projectIds = resourcesWhichRequireAllUsersGroup.mapNotNull {
                    if (it.project == null) null
                    else FindByProjectId(it.project!!)
                }.toSet().toList()

                val allUserGroups = Projects.retrieveAllUsersGroup.call(
                    BulkRequest(projectIds),
                    serviceClient
                ).orThrow()

                data class AclEntry(val resourceId: Long, val groupId: String, val perm: Permission)
                val aclEntries = ArrayList<AclEntry>()

                for (resource in resourcesWhichRequireAllUsersGroup) {
                    val originalIndex = request.items.indexOf(resource)
                    val resourceId = generatedIds[originalIndex]
                    val groupId = allUserGroups.responses[projectIds.indexOf(FindByProjectId(resource.project!!))].id

                    val perms = buildList {
                        if (resource.projectAllRead) add(Permission.READ)
                        if (resource.projectAllWrite) add(Permission.EDIT)
                    }

                    perms.forEach { perm ->
                        aclEntries.add(AclEntry(resourceId, groupId, perm))
                    }
                }

                check(aclEntries.isNotEmpty())

                session.sendPreparedStatement(
                    {
                        aclEntries.split {
                            into("resource_ids") { it.resourceId }
                            into("group_ids") { it.groupId }
                            into("permissions") { it.perm }
                        }
                    },
                    """
                        with entries as (
                            select
                                unnest(:resource_ids::bigint[]) as resource_id,
                                unnest(:group_ids::text[]) as group_id,
                                unnest(:permissions::text[]) as permission
                        )
                        insert into provider.resource_acl_entry (group_id, username, permission, resource_id) 
                        select group_id, null, permission, resource_id
                        from entries
                    """
                )
            }

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
        val owner = ResourceOwner(
            actorAndProject.actor.safeUsername(),
            if (personalResource) null else actorAndProject.project
        )
        val relevantProviders = findRelevantProviders(actorAndProject)
        relevantProviders.forEach { provider ->
            val comms = providers.prepareCommunication(provider)
            val api = providerApi(comms)

            // NOTE(Dan): Ignore failures as they commonly indicate that it is not supported.
            loop@for (attempt in 0 until 5) {
                try {
                    val resp = api.init.call(
                        ResourceInitializationRequest(owner),
                        comms.client.withProxyInfo(owner.createdBy, actorAndProject.signedIntentFromUser)
                    )

                    if (resp.statusCode.value == 449 || resp.statusCode == HttpStatusCode.ServiceUnavailable) {
                        val im = IntegrationProvider(provider)
                        im.init.call(IntegrationProviderInitRequest(owner.createdBy), comms.client).orThrow()
                        delay(200L + (attempt * 500))
                        continue@loop
                    } else {
                        break@loop
                    }
                } catch(ex: Throwable) {
                    if (ex is RPCException && ex.httpStatusCode == HttpStatusCode.BadGateway) {
                        if (attempt == 0) log.debug("Could not connect to provider: $provider")
                    } else {
                        log.info(ex.stackTraceToString())
                    }
                }
            }
        }
    }

    override suspend fun retrieveProducts(actorAndProject: ActorAndProject): SupportByProvider<Prod, Support> {
        val relevantProviders = findRelevantProviders(actorAndProject)
        return SupportByProvider(support.retrieveProducts(relevantProviders))
    }

    suspend fun findRelevantProviders(
        actorAndProject: ActorAndProject,
        useProject: Boolean = true,
        ctx: DBContext? = null,
    ): List<String> {
        val relevantProviders = (ctx ?: db).withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("area", productArea.name)
                        setParameter("project", actorAndProject.project)
                        setParameter("username", actorAndProject.actor.safeUsername())
                        setParameter("use_project", useProject)
                    },
                    """
                        select distinct pc.provider
                        from
                            accounting.product_categories pc join
                            accounting.products product on product.category = pc.id left join
                            accounting.wallets w on pc.id = w.category left join
                            accounting.wallet_owner wo on wo.id = w.owned_by left join
                            project.project_members pm on
                                pm.username = :username and
                                (
                                    (
                                        :use_project and
                                        wo.project_id = pm.project_id and
                                        pm.project_id = :project::text
                                    ) or
                                    (
                                        not :use_project
                                    )
                                )
                        where
                            pc.product_type = :area::accounting.product_type and
                            (
                                product.free_to_use or
                                (
                                    wo.username = :username and
                                    w.id is not null and
                                    :project::text is null
                                ) or
                                (
                                    pm.project_id is not null and
                                    w.id is not null
                                )
                            );
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
        val resolvedActorAndProject =
            if (personalResource) ActorAndProject(actorAndProject.actor, null) else actorAndProject
        val ids = request.items.asSequence().map { it.id }.toSet()

        val allResources = retrieveBulk(
            resolvedActorAndProject,
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
        val resolvedActorAndProject =
            if (personalResource) ActorAndProject(actorAndProject.actor, null) else actorAndProject
        val search = browseQuery(resolvedActorAndProject, request.flags, request.query)
        return paginatedQuery(
            search, resolvedActorAndProject, listOf(Permission.READ), request.flags,
            request, request.normalize(), true, request.query, ctx
        )
    }

    protected suspend fun accessibleResources(
        actor: Actor,
        permissionsOneOf: Collection<Permission>,
        resourceId: Long? = null,
        projectFilter: String? = "",
        flags: Flags? = null,
        simpleFlags: SimpleResourceIncludeFlags? = null,
        includeUnconfirmed: Boolean = false,
        offset: Long? = null,
        limit: Long? = null,
        sort: SortFlags? = null,
        query: String? = null,
    ): PartialQuery {
        return when (browseStrategy) {
            ResourceBrowseStrategy.OLD -> accessibleResourcesOld(
                actor, permissionsOneOf, resourceId, projectFilter,
                flags, simpleFlags, includeUnconfirmed
            )
            ResourceBrowseStrategy.NEW -> accessibleResourcesNew(
                actor, permissionsOneOf, resourceId, projectFilter,
                flags, simpleFlags, includeUnconfirmed, offset, limit, sort, query
            )
        }
    }

    protected open suspend fun applyFilters(
        actor: Actor,
        query: String?,
        flags: Flags?,
    ): PartialQuery {
        return PartialQuery({}, "true")
    }

    protected suspend fun accessibleResourcesNew(
        actor: Actor,
        permissionsOneOf: Collection<Permission>,
        resourceId: Long? = null,
        projectFilter: String? = "",
        flags: Flags? = null,
        simpleFlags: SimpleResourceIncludeFlags? = null,
        includeUnconfirmed: Boolean = false,
        offset: Long? = null,
        limit: Long? = null,
        sort: SortFlags? = null,
        query: String? = null,
    ): PartialQuery {
        // NOTE(Dan): This function is responsible for fetching the accessible resources.
        // The query starts by fetching the relevant rows from provider.resource and the specification.
        // At this point we perform a sort and limit. It is crucial that we limit the query at this stage
        // as the query times otherwise grow out of control.
        //
        // The query is built dynamically based on what is requested. This allows us to take a faster path
        // through the query when possible.

        // NOTE(Dan): We start by determining the concrete values of every flag. The flags in `Flags` have
        // higher priority than `simpleFlags`.
        val filterCreatedBy = flags?.filterCreatedBy ?: simpleFlags?.filterCreatedBy
        val filterCreatedBefore = flags?.filterCreatedBefore ?: simpleFlags?.filterCreatedBefore
        val filterCreatedAfter = flags?.filterCreatedAfter ?: simpleFlags?.filterCreatedAfter
        val filterProvider = flags?.filterProvider ?: simpleFlags?.filterProvider
        val filterProductId = flags?.filterProductId ?: simpleFlags?.filterProductId
        val filterProductCategory = flags?.filterProductCategory ?: simpleFlags?.filterProductCategory
        val filterProviderIds = (flags?.filterProviderIds ?: simpleFlags?.filterProviderIds)?.split(",")
        val filterIds = run {
            (flags?.filterIds ?: simpleFlags?.filterIds ?: "")
                .split(",")
                .asSequence()
                .map { it.toLongOrNull() }
                .plus(resourceId)
                .filterNotNull()
                .toList()
                .takeIf { it.isNotEmpty() }
        }
        val hideProductId = flags?.hideProductId ?: simpleFlags?.hideProductId
        val hideProductCategory = flags?.hideProductCategory ?: simpleFlags?.hideProductCategory

        // NOTE(Dan): Next we need to determine the table name of the specification. We also need to determine how to
        // sort the query.
        val tableName = table.verify({ db.openSession() }, { db.closeSession(it) })
        val columnToSortBy = computedSortColumns[sort?.sortBy ?: ""] ?: defaultSortColumn
        var sortBy = columnToSortBy.verify({ db.openSession() }, { db.closeSession(it) })
        val sortDirection = when (sort?.sortDirection ?: defaultSortDirection) {
            SortDirection.ascending -> "asc"
            SortDirection.descending -> "desc"
        }

        // NOTE(Dan): Project information is kept in a Redis cache. We fetch it only if needed.
        val needsProjectMembership = when {
            projectFilter == null -> false
            permissionsOneOf.contains(Permission.PROVIDER) -> false
            actor is Actor.System -> false
            else -> true
        }

        val projectMembership = if (needsProjectMembership) {
            projectCache.lookup(actor.safeUsername())
        } else {
            null
        }

        // NOTE(Dan): We will optionally fetch the ACL early. We only fetch it early if we need it to
        // determine which resources are available to the user. In many cases we can completely skip the ACL
        // as we have access as an admin/provider.
        val needsEarlyAcl: Boolean = run acl@{
            if (permissionsOneOf.contains(Permission.ADMIN)) return@acl false
            if (!needsProjectMembership) return@acl false
            if (projectFilter != "") {
                val isAdmin = projectMembership!!.adminInProjects.any { it == projectFilter }
                if (isAdmin) return@acl false
            }
            true
        }

        val (filterParams, filterQuery) = applyFilters(actor, query, flags)

        return PartialQuery(
            {
                filterParams()
                setParameter("resource_type", resourceType)
                setParameter("include_unconfirmed", includeUnconfirmed)
                setParameter("username", actor.safeUsername())
                if (filterCreatedBy != null) setParameter("filter_created_by", filterCreatedBy)
                if (filterCreatedBefore != null) setParameter("filter_created_before", filterCreatedBefore)
                if (filterCreatedAfter != null) setParameter("filter_created_after", filterCreatedAfter)
                if (filterProvider != null) setParameter("filter_provider", filterProvider)
                if (filterProductId != null) setParameter("filter_product_id", filterProductId)
                if (filterProductCategory != null) setParameter("filter_product_category", filterProductCategory)
                if (filterProviderIds != null) setParameter("filter_provider_ids", filterProviderIds)
                if (filterIds != null) setParameter("filter_ids", filterIds)
                if (hideProductId != null) setParameter("hide_product_id", hideProductId)
                if (hideProductCategory != null) setParameter("hide_product_category", hideProductCategory)
                if (!projectFilter.isNullOrEmpty()) setParameter("project_filter", projectFilter)
                if (projectMembership != null) setParameter("groups", projectMembership.groupMemberOf.map { it.group })
                if (projectMembership != null) setParameter("admin_in", projectMembership.adminInProjects)
                if (needsEarlyAcl) setParameter("permissions", permissionsOneOf.map { it.name })
            },
            buildString {
                run {
                    appendLine("select distinct")
                    appendLine("  r, spec")
                    appendLine("  , p.name as product_name, pc.category as product_category, pc.provider as provider")
                    appendLine("  , $sortBy")

                    if (needsEarlyAcl) {
                        // We write an empty array here to indicate that the permissions should be extracted from the
                        // ACL we retrieve later. See `attachExtra` for details.
                        appendLine("  , array[]::text[] as permissions")
                    } else {
                        // If we don't need to ACL, then it is because we already know for sure that we implicitly are
                        // an ADMIN or a PROVIDER.
                        if (permissionsOneOf.contains(Permission.PROVIDER)) {
                            appendLine("  , array['PROVIDER'] as permissions")
                        } else {
                            appendLine("  , array['ADMIN', 'READ', 'EDIT'] as permissions")
                        }
                    }
                }

                run {
                    // NOTE(Dan): We always select the resource, specification and product information.
                    appendLine("from")
                    appendLine("  provider.resource r")
                    appendLine("  join $tableName spec on r.id = spec.resource")
                    appendLine("  join accounting.products p on r.product = p.id")
                    appendLine("  join accounting.product_categories pc on p.category = pc.id")

                    if (needsEarlyAcl) {
                        appendLine("  left join provider.resource_acl_entry acl on acl.resource_id = r.id")
                    }
                }

                run {
                    // NOTE(Dan): We now apply filters in the query. All filters are AND'd together at this level of
                    // indentation. At each level of indentation we swap between AND and OR. This makes the query
                    // easier to read and reason about. The AND/OR should be attached to each filter at the beginning.
                    appendLine("where")
                    appendLine("  r.type = :resource_type")

                    run {
                        // Verify permissions
                        appendLine("  and (")
                        if (permissionsOneOf.contains(Permission.READ)) {
                            appendLine("    r.public_read = true")
                        } else {
                            appendLine("    false")
                        }

                        when {
                            actor is Actor.System -> {
                                // Apply no filter. The system always has permission to access the object.
                                appendLine("    or true")
                            }

                            permissionsOneOf.contains(Permission.PROVIDER) -> {
                                // Verify that we are the provider user
                                appendLine("    or :username = '#P_' || pc.provider")
                            }

                            permissionsOneOf.contains(Permission.ADMIN) -> {
                                if (projectFilter != null) {
                                    appendLine("  or r.project = some(:admin_in::text[])")
                                }

                                if (projectFilter == null || projectFilter == "") {
                                    appendLine("  or (r.created_by = :username and r.project is null)")
                                }
                            }

                            projectFilter == null -> {
                                // Fast-path, we just need to look at created_by
                                appendLine("    or (r.created_by = :username and r.project is null)")
                            }

                            projectFilter != "" -> {
                                // We need to consider a specific project
                                val membership = projectCache.lookup(actor.safeUsername())
                                val isAdmin = membership.adminInProjects.any { it == projectFilter }

                                if (isAdmin) {
                                    appendLine("    or r.project = :project_filter")
                                } else {
                                    appendLine("    or (")
                                    appendLine("      r.project = :project_filter")
                                    appendLine("      and (acl.group_id = some(:groups::text[]) or acl.username = :username)")
                                    appendLine("      and acl.permission = some(:permissions::text[])")
                                    appendLine("    )")
                                }
                            }

                            else -> {
                                // We need to consider everything
                                appendLine("    or (")
                                appendLine("      (r.project = some(:admin_in::text[]) or acl.group_id = some(:groups::text[]) or acl.username = :username)")
                                appendLine("      and acl.permission = some(:permissions::text[])")
                                appendLine("    )")

                                appendLine("    or (r.created_by = :username and r.project is null)")
                            }
                        }
                        appendLine("  and")
                        append(filterQuery)

                        appendLine("  )")
                    }

                    run {
                        // Apply filters
                        appendLine("  and (confirmed_by_provider = true or :include_unconfirmed = true)")

                        if (filterCreatedBy != null) {
                            appendLine("  and r.created_by ilike '%' || :filter_created_by || '%'")
                        }

                        if (filterCreatedAfter != null) {
                            appendLine("  and r.created_at >= to_timestamp(:filter_created_after::bigint / 1000)")
                        }

                        if (filterCreatedBefore != null) {
                            appendLine("  and r.created_at <= to_timestamp(:filter_created_before::bigint / 1000)")
                        }

                        if (filterProviderIds != null) {
                            appendLine("  and r.provider_generated_id = some(:filter_provider_ids::text[])")
                        }

                        if (filterIds != null) {
                            appendLine("  and r.id = some(:filter_ids::bigint[])")
                        }

                        if (filterProductCategory != null) {
                            appendLine("  and pc.category = :filter_product_category")
                        }

                        if (filterProductId != null) {
                            appendLine("  and p.name = :filter_product_id")
                        }

                        if (hideProductCategory != null) {
                            appendLine("  and pc.category != :hide_product_category")
                        }

                        if (hideProductId != null) {
                            appendLine("  and p.name != :hide_product_id")
                        }
                    }
                }

                // Apply sort and pagination
                appendLine("order by $sortBy $sortDirection")
                if (offset != null) appendLine("offset $offset")
                if (limit != null) appendLine("limit $limit")
            }
        )
    }

    private suspend fun fetchResourcesNew(
        session: AsyncDBConnection,
        resourceQuery: String,
        query: String,
        converter: String,
        sort: SortFlags? = null,
        params: EnhancedPreparedStatement.() -> Unit,
    ): ResultSet {
        val columnToSortBy = computedSortColumns[sort?.sortBy ?: ""] ?: defaultSortColumn
        var sortBy = columnToSortBy.verify({ db.openSession() }, { db.closeSession(it) })
        val sortDirection = when (sort?.sortDirection ?: defaultSortDirection) {
            SortDirection.ascending -> "asc"
            SortDirection.descending -> "desc"
        }

        return session.sendPreparedStatement(
            params,
            """
                with
                    relevant_resources as ($resourceQuery),
                    spec as ($query),
                    accessible_resources as (
                        select distinct
                            r.r,
                            r.product_name as name,
                            r.product_category as category,
                            r.provider as provider,
                            r.permissions,
                            array_remove(array_agg(distinct acl), null) as other_permissions,
                            array_remove(array_agg(distinct update), null) as updates
                        from
                            relevant_resources r left join
                            provider.resource_acl_entry acl on (r.r).id = acl.resource_id left join
                            provider.resource_update update on (r.r).id = update.resource
                        group by
                            r.r, r.product_name, r.product_category, r.provider, r.permissions
                    )
                select provider.resource_to_json(resc, $converter(spec_t))
                from
                    accessible_resources resc join
                    spec spec_t on (resc.r).id = spec_t.resource
                order by $sortBy $sortDirection
            """,
        ).rows
    }

    protected fun accessibleResourcesOld(
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
        query: String?,
        ctx: DBContext?,
    ): PageV2<Res> {
        val resolvedActorAndProject =
            if (personalResource) ActorAndProject(actorAndProject.actor, null) else actorAndProject
        val (params, sqlQuery) = partialQuery
        val converter = sqlJsonConverter.verify({ db.openSession() }, { db.closeSession(it) })
        val columnToSortBy = computedSortColumns[sortFlags?.sortBy ?: ""] ?: defaultSortColumn
        var sortBy = columnToSortBy.verify({ db.openSession() }, { db.closeSession(it) })

        val sortDirection = when (sortFlags?.sortDirection ?: defaultSortDirection) {
            SortDirection.ascending -> "asc"
            SortDirection.descending -> "desc"
        }

        val offset = (pagination.itemsToSkip ?: 0L) + ((pagination.next ?: "").toIntOrNull() ?: 0)
        val next = (offset + pagination.itemsPerPage).toString()

        val (resourceParams, resourceQuery) = accessibleResources(
            resolvedActorAndProject.actor,
            permissionsOneOf,
            projectFilter = if (useProject) resolvedActorAndProject.project else "",
            flags = flags,
            limit = pagination.itemsPerPage.toLong(),
            offset = offset,
            sort = sortFlags,
            query = query
        )

        val rows = (ctx ?: db).withSession { session ->
            when (browseStrategy) {
                ResourceBrowseStrategy.OLD -> {
                    if (sortBy == "created_by") {
                        sortBy = "(resc.r).created_by"
                    } else if (sortBy == "created_at") {
                        sortBy = "(resc.r).created_at"
                    }

                    session.sendPreparedStatement(
                        {
                            resourceParams()
                            params()
                        },
                        """
                            with
                                accessible_resources as ($resourceQuery),
                                spec as ($sqlQuery)
                            select provider.resource_to_json(resc, $converter(spec))
                            from
                                accessible_resources resc join
                                spec on (resc.r).id = spec.resource
                            order by
                                $sortBy $sortDirection, resc.category, resc.name 
                            limit ${pagination.itemsPerPage}
                            offset $offset
                        """,
                    ).rows
                }

                ResourceBrowseStrategy.NEW -> {
                    fetchResourcesNew(session, resourceQuery, sqlQuery, converter) {
                        resourceParams()
                        params()
                    }
                }
            }
        }

        val items = rows.mapNotNull {
            try {
                defaultMapper.decodeFromString(serializer, it.getString(0)!!)
            } catch (ex: Throwable) {
                log.warn("Caught exception while browsing resource: ${it.getString(0)}")
                log.warn(ex.stackTraceToString())
                null
            }
        }.attachExtra(actorAndProject.actor, flags)

        return PageV2(
            pagination.itemsPerPage,
            items,
            if (items.size < pagination.itemsPerPage) null else next
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
