package dk.sdu.cloud.accounting.util

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*
import kotlinx.serialization.KSerializer

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

interface ResourceSvc<
    R : Resource<*>,
    F : ResourceIncludeFlags,
    Spec : ResourceSpecification,
    Update : ResourceUpdate> {
    suspend fun browse(
        actorAndProject: ActorAndProject,
        request: WithPaginationRequestV2,
        flags: F,
        ctx: DBContext? = null,
    ): PageV2<R>

    suspend fun retrieve(
        actorAndProject: ActorAndProject,
        id: String,
        flags: F,
        ctx: DBContext? = null,
    ): R

    suspend fun create(
        actorAndProject: ActorAndProject,
        request: BulkRequest<Spec>,
    ): BulkResponse<FindByStringId?>

    suspend fun updateAcl(
        actorAndProject: ActorAndProject,
        request: BulkRequest<UpdatedAcl>
    ): BulkResponse<Unit?>

    suspend fun delete(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>
    ): BulkResponse<Unit?>

    suspend fun addUpdate(
        actorAndProject: ActorAndProject,
        updates: BulkRequest<ResourceUpdateAndId<Update>>
    )
}

abstract class ResourceService<
    Res : Resource<*>,
    Spec : ResourceSpecification,
    Flags : ResourceIncludeFlags,
    Comms : ProviderComms,
    Support : ProductSupport,
    Update : ResourceUpdate,
    Prod : Product>(
    private val db: AsyncDBSessionFactory,
    private val providers: Providers<Comms>,
    private val support: ProviderSupport<Comms, Prod, Support>
) : ResourceSvc<Res, Flags, Spec, Update> {
    protected abstract val table: String
    protected abstract val sortColumn: String
    protected abstract val serializer: KSerializer<Res>
    protected open val resourceType: String get() = table.substringAfterLast('.').removeSuffix("s")
    protected open val sqlJsonConverter: String get() = table.removeSuffix("s") + "_to_json"

    protected val proxy = ProviderProxy<Comms, Prod, Support, Res>(providers, support)

    override suspend fun browse(
        actorAndProject: ActorAndProject,
        request: WithPaginationRequestV2,
        flags: Flags,
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

                        setParameter("include_others", flags.includeOthers)
                        setParameter("include_updates", flags.includeUpdates)

                        setParameter("user", actorAndProject.actor.safeUsername())
                        setParameter("project", actorAndProject.project)
                    },
                    "select provider.default_browse(:resource_type, :table, :sort_column, :to_json, :user, :project, " +
                        ":include_others, :include_updates)"
                )
            },
            mapper = { _, rows ->
                rows.map { defaultMapper.decodeFromString(serializer, it.getString(0)!!) }
            }
        )
    }

    override suspend fun retrieve(
        actorAndProject: ActorAndProject,
        id: String,
        flags: Flags,
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

                        setParameter("include_others", flags.includeOthers)
                        setParameter("include_updates", flags.includeUpdates)

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
                ?.let { defaultMapper.decodeFromString(serializer, it.getString(0)!!) }
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }

    protected open suspend fun retrieveBulk(
        actorAndProject: ActorAndProject,
        ids: List<String>,
        flags: Flags?,
        permission: Permission,
        requireAll: Boolean = true,
        ctx: DBContext? = null,
    ): List<Res> {
        return (ctx ?: db).withSession { session ->
            val result = session
                .sendPreparedStatement(
                    {
                        setParameter("resource_type", resourceType)
                        setParameter("table", table)
                        setParameter("to_json", sqlJsonConverter)

                        setParameter("include_others", flags?.includeOthers ?: false)
                        setParameter("include_updates", flags?.includeUpdates ?: false)

                        setParameter("user", actorAndProject.actor.safeUsername())
                        setParameter("ids", ids.mapNotNull { it.toLongOrNull() })
                        setParameter("permission", permission.name)
                    },
                    """
                        select * from provider.default_bulk_retrieve(:resource_type, :table, :to_json, :user, :ids, 
                            :permission, :include_others, :include_updates)
                    """
                )
                .rows
                .map { defaultMapper.decodeFromString(serializer, it.getString(0)!!) }

            if (requireAll && result.size != ids.size) {
                throw RPCException("Unable to use all requested resources", HttpStatusCode.BadRequest)
            }

            result
        }
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

    protected abstract fun endpointForCreate(
        comms: Comms
    ): CallDescription<BulkRequest<Spec>, BulkResponse<FindByStringId?>, CommonErrorMessage>

    protected abstract fun verifyProviderSupportsCreate(
        spec: Spec,
        res: ProductRefOrResource<Res>,
        support: Support
    )
    protected abstract suspend fun createSpecification(resourceId: Long, specification: Spec, session: AsyncDBConnection)

    override suspend fun create(
        actorAndProject: ActorAndProject,
        request: BulkRequest<Spec>,
    ): BulkResponse<FindByStringId?> {
        val session = db.openSession()
        var lastBatchOfIds: List<Long>? = null
        val adjustedResponse = ArrayList<FindByStringId?>()
        try {
            proxy.bulkProxy(
                this::endpointForCreate,
                actorAndProject,
                request,
                isUserRequest = true,
                verifyAndFetchResources = { _, _ ->
                    db.withSession { session ->
                        verifyMembership(actorAndProject, session)
                    }

                    request.items.map { it to ProductRefOrResource.SomeRef(it.product!!) }
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

    protected abstract fun endpointForUpdateAcl(
        comms: Comms
    ): CallDescription<BulkRequest<UpdatedAcl>, BulkResponse<Unit?>, CommonErrorMessage>

    protected abstract fun verifyProviderSupportsUpdateAcl(
        spec: UpdatedAcl,
        res: ProductRefOrResource<Res>,
        support: Support
    )

    override suspend fun updateAcl(
        actorAndProject: ActorAndProject,
        request: BulkRequest<UpdatedAcl>
    ): BulkResponse<Unit?> {
        val session = db.openSession()
        return try {
            proxy.bulkProxy(
                this::endpointForUpdateAcl,
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
                                        entityAndPermissions.permissions.forEach {
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
                },
                afterCall = { _, _, _ -> db.commit(session) },
                onProviderFailure = { _, _, _ -> db.rollback(session) }
            )
        } finally {
            db.closeSession(session)
        }
    }

    protected abstract fun endpointForDelete(
        comms: Comms
    ): CallDescription<BulkRequest<FindByStringId>, BulkResponse<Unit?>, CommonErrorMessage>

    protected abstract fun verifyProviderSupportsDelete(
        id: FindByStringId,
        res: ProductRefOrResource<Res>,
        support: Support
    )


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
        val session = db.openSession()
        return try {
            proxy.bulkProxy(
                this::endpointForDelete,
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

                    session.sendPreparedStatement(block,
                        "delete from provider.resource_acl_entry where resource_id = some(:ids)")

                    session.sendPreparedStatement(block,
                        "delete from provider.resource_update where resource = some(:ids)")

                    session.sendPreparedStatement(block,
                        "delete from provider.resource where id = some(:ids)")
                },
                afterCall = { _, _, _ -> db.commit(session) },
                onProviderFailure = { _, _, _ -> db.rollback(session) }
            )
        } finally {
            db.closeSession(session)
        }
    }

    override suspend fun addUpdate(
        actorAndProject: ActorAndProject,
        updates: BulkRequest<ResourceUpdateAndId<Update>>
    ) {
        db.withSession { session ->

        }
    }
}

