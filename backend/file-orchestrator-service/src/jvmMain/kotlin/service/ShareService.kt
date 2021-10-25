package dk.sdu.cloud.file.orchestrator.service

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.WithPaginationRequestV2
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductType
import dk.sdu.cloud.accounting.util.PartialQuery
import dk.sdu.cloud.accounting.util.ProviderComms
import dk.sdu.cloud.accounting.util.ProviderSupport
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.accounting.util.ResourceService
import dk.sdu.cloud.accounting.util.SqlObject
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.FilesMoveRequestItem
import dk.sdu.cloud.file.orchestrator.api.OutgoingShareGroup
import dk.sdu.cloud.file.orchestrator.api.Share
import dk.sdu.cloud.file.orchestrator.api.ShareFlags
import dk.sdu.cloud.file.orchestrator.api.ShareSupport
import dk.sdu.cloud.file.orchestrator.api.Shares
import dk.sdu.cloud.file.orchestrator.api.SharesControl
import dk.sdu.cloud.file.orchestrator.api.SharesProvider
import dk.sdu.cloud.file.orchestrator.api.SharesUpdatePermissionsRequest
import dk.sdu.cloud.file.orchestrator.api.extractPathMetadata
import dk.sdu.cloud.file.orchestrator.api.fileName
import dk.sdu.cloud.file.orchestrator.api.normalize
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.notification.api.NotificationType
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.provider.api.UpdatedAcl
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.paginateV2
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

typealias ShareSvc = ResourceService<Share, Share.Spec, Share.Update, ShareFlags, Share.Status, Product.Storage,
        ShareSupport, StorageCommunication>

class ShareService(
    db: AsyncDBSessionFactory,
    providers: Providers<StorageCommunication>,
    support: ProviderSupport<StorageCommunication, Product.Storage, ShareSupport>,
    serviceClient: AuthenticatedClient,
    private val files: FilesService,
    private val collections: FileCollectionService
) : ShareSvc(db, providers, support, serviceClient) {
    override val table = SqlObject.Table("file_orchestrator.shares")
    override val defaultSortColumn = SqlObject.Column(table, "original_file_path")
    override val sortColumns: Map<String, SqlObject.Column> = mapOf(
        "sourceFilePath" to defaultSortColumn
    )

    override val serializer = serializer<Share>()
    override val updateSerializer = serializer<Share.Update>()
    override val productArea = ProductType.STORAGE

    override fun userApi() = Shares
    override fun controlApi() = SharesControl
    override fun providerApi(comms: ProviderComms) = SharesProvider(comms.provider.id)

    init {
        files.addMoveHandler(::onFilesMoved)
        files.addDeleteHandler(::onFilesDeleted)
    }

    private suspend fun onFilesMoved(batch: List<FilesMoveRequestItem>) {
        // TODO(Dan): This is not guaranteed to work if some conflict policy is applied
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    batch.split {
                        into("old_paths") { it.oldId.normalize() }
                        into("new_paths") { it.newId.normalize() }
                    }
                },
                """
                    with entries as (
                        select unnest(:old_paths::text[]) old_path, unnest(:new_paths::text[]) new_path
                    )
                    update file_orchestrator.shares
                    set
                        original_file_path = e.new_path || substring(original_file_path, length(e.old_path) + 1)
                    from entries e
                    where
                        (original_file_path = e.old_path or original_file_path like e.old_path || '/%');
                """
            )
        }
    }

    private suspend fun onFilesDeleted(request: List<FindByStringId>) {
        /*
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    request.split { into("paths") { it.id.normalize() } }
                },
                """
                    with
                        entries as (
                            select unnest(:paths::text[]) path
                        ),
                        affected_shares as (
                            delete from file_orchestrator.shares s
                            using entries e
                            where
                                e.path = original_file_path or
                                original_file_path like e.path || '/%'
                            returning s.resource, s.shared_with, s.permissions, s.state
                        )
                    delete from provider.resource_acl_entry entry
                    using affected_shares share
                    where
                        share.state = 'APPROVED' and
                        share.resource = entry.resource_id and
                        share.shared_with = entry.username and
                        entry.permission = some(share.permissions)
                """
            )
        }
         */
    }

    override suspend fun updateAcl(
        actorAndProject: ActorAndProject,
        request: BulkRequest<UpdatedAcl>
    ): BulkResponse<Unit?> {
        throw RPCException(
            "The permissions of a share cannot be changed through this endpoint",
            HttpStatusCode.BadRequest
        )
    }

    override suspend fun createSpecifications(
        actorAndProject: ActorAndProject,
        idWithSpec: List<Pair<Long, Share.Spec>>,
        session: AsyncDBConnection,
        allowDuplicates: Boolean
    ) {
        val shareWithSelf = idWithSpec.find { it.second.sharedWith == actorAndProject.actor.safeUsername() }
        if (shareWithSelf != null) {
            throw RPCException(
                "You cannot share '${shareWithSelf.second.sourceFilePath.fileName()}' with yourself",
                HttpStatusCode.BadRequest
            )
        }
        //Check user exists
        val returnedUsers = session.sendPreparedStatement(
            {
                idWithSpec.split {
                    into("username") {it.second.sharedWith}
                }
            },
            """
                select *
                from "auth".principals
                where id in (select unnest(:username::text[]))
            """
        ).rows
        if (idWithSpec.size != returnedUsers.size) {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
        }

        val collIds = idWithSpec.map { extractPathMetadata(it.second.sourceFilePath).collection }.toSet()
        collections.retrieveBulk(
            actorAndProject,
            collIds,
            listOf(Permission.ADMIN),
            ctx = session
        )

        session.sendPreparedStatement(
            {
                idWithSpec.split {
                    into("ids") { it.first }
                    into("file_paths") { it.second.sourceFilePath.normalize() }
                    into("shared_with") { it.second.sharedWith }
                    into("permissions") {
                        it.second.permissions.filter { it.canBeGranted }.joinToString(",") { it.name }
                    }
                }
            },
            """
                with 
                    requests as (
                        select unnest(:ids::bigint[]) id, unnest(:file_paths::text[]) file_path,
                               unnest(:shared_with::text[]) shared_with,
                               regexp_split_to_array(unnest(:permissions::text[]), ',') permissions
                    ),
                    inserted_specs as (
                        insert into file_orchestrator.shares (resource, original_file_path, shared_with, permissions) 
                        select id, file_path, shared_with, permissions
                        from requests
                    )
                insert into provider.resource_acl_entry (group_id, username, permission, resource_id) 
                select null, shared_with, unnest(permissions), id
                from requests
            """
        )
        idWithSpec.forEach {
            NotificationDescriptions.create.call(
                CreateNotification(
                    it.second.sharedWith,
                    Notification(
                        NotificationType.SHARE_REQUEST.name,
                        "${actorAndProject.actor.safeUsername()} wants to share a file with you",
                        meta = JsonObject(emptyMap())
                    )
                ),
                serviceClient
            )
        }
    }

    override suspend fun onUpdate(
        resources: List<Share>,
        updates: List<ResourceUpdateAndId<Share.Update>>,
        session: AsyncDBConnection
    ) {
        if (resources.isEmpty()) return
        val expectedCollectionIds = updates
            .mapNotNull { it.update.shareAvailableAt }
            .map { extractPathMetadata(it).collection }
            .toSet()

        val providerCollection = extractPathMetadata(resources.first().specification.sourceFilePath).collection
        val allCollections = collections.retrieveBulk(
            ActorAndProject(Actor.System, null),
            expectedCollectionIds + setOf(providerCollection),
            listOf(Permission.READ),
            ctx = session
        )

        val provider = allCollections.find { it.id == providerCollection }?.specification?.product?.provider
            ?: error("Could not find provider")

        val availableAtSameProvider = allCollections.all { it.specification.product.provider == provider }
        if (!availableAtSameProvider) {
            throw RPCException("Cannot expose a share on a different provider", HttpStatusCode.Forbidden)
        }

        session.sendPreparedStatement(
            {
                updates.split {
                    into("ids") { it.id }
                    into("new_states") { it.update.newState.name }
                    into("share_available_at") { it.update.shareAvailableAt?.normalize() }
                }
            },
            """
                with requests as (
                    select
                        unnest(:ids::bigint[]) id,
                        unnest(:new_states::file_orchestrator.share_state[]) new_state,
                        unnest(:share_available_at::text[]) new_location
                )
                update file_orchestrator.shares s
                set
                    state = coalesce(new_state, state),
                    -- NOTE(Dan): Don't change available_at if it is already present
                    available_at = coalesce(available_at, new_location)
                from
                    requests req
                where
                    s.resource = req.id
            """
        )
    }

    suspend fun approve(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>
    ) {
        db.withSession { session ->
            val shares = retrieveBulk(actorAndProject, request.items.map { it.id }, listOf(Permission.READ),
                ctx = session)

            if (shares.any { it.specification.sharedWith != actorAndProject.actor.safeUsername() }) {
                throw RPCException("You are not allowed to approve your own share", HttpStatusCode.Forbidden)
            }

            if (shares.any { it.status.state != Share.State.PENDING }) {
                throw RPCException("Cannot accept a share which has already been handled", HttpStatusCode.Forbidden)
            }

            if (shares.any { it.status.shareAvailableAt == null }) {
                throw RPCException(
                    "Share is not ready yet. UCloud is waiting for the provider to reply. Try again later.",
                    HttpStatusCode.BadGateway
                )
            }

            session.sendPreparedStatement(
                {
                    request.items.split {
                        into("ids") { it.id.toLongOrNull() }
                    }
                },
                """
                    with
                        requests as (
                            select unnest(:ids::bigint[]) id
                        ),
                        affected_shares as (
                            update file_orchestrator.shares s
                            set state = 'APPROVED'
                            from requests req
                            where s.resource = req.id    
                            returning
                                s.shared_with, 
                                s.permissions, 
                                (regexp_split_to_array(s.available_at, '/'))[2]::bigint collection
                        )
                    insert into provider.resource_acl_entry (group_id, username, permission, resource_id) 
                    select null, shared_with, unnest(permissions), collection
                    from affected_shares
                    on conflict do nothing
                """
            )
        }
    }

    suspend fun reject(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>
    ) {
        db.withSession { session ->
            val shares = retrieveBulk(actorAndProject, request.items.map { it.id }, listOf(Permission.READ),
                ctx = session)

            if (shares.any { it.specification.sharedWith != actorAndProject.actor.safeUsername() }) {
                throw RPCException("You are not allowed to reject your own share", HttpStatusCode.Forbidden)
            }

            session.sendPreparedStatement(
                {
                    request.items.split {
                        into("ids") { it.id.toLongOrNull() }
                    }
                },
                """
                    with
                        requests as (
                            select unnest(:ids::bigint[]) id
                        ),
                        affected_shares as (
                            update file_orchestrator.shares s
                            set state = 'REJECTED'
                            from requests req
                            where s.resource = req.id    
                            returning
                                s.shared_with, 
                                s.permissions, 
                                case
                                    when s.available_at is null then null
                                    else (regexp_split_to_array(s.available_at, '/'))[2]::bigint
                                end as collection
                        )
                    delete from provider.resource_acl_entry entry
                    using affected_shares share
                    where
                        share.collection = entry.resource_id and
                        share.shared_with = entry.username and
                        entry.permission = some(share.permissions)
                """
            )

            cleanupAfterShare(shares, session)
        }
    }

    private suspend fun cleanupAfterShare(shares: Collection<Share>, session: AsyncDBConnection) {
        val collectionsToDelete = shares
            .filter { it.status.state == Share.State.APPROVED && it.status.shareAvailableAt != null }
            .map { extractPathMetadata(it.status.shareAvailableAt!!).collection }
            .toSet()

        val allCollections = collections.retrieveBulk(
            ActorAndProject(Actor.System, null),
            collectionsToDelete,
            listOf(Permission.ADMIN),
            ctx = session
        )

        collections.deleteFromDatabaseSkipProvider(collectionsToDelete.map { it.toLong() }, allCollections, session)
    }

    override suspend fun deleteSpecification(
        resourceIds: List<Long>,
        resources: List<Share>,
        session: AsyncDBConnection
    ) {
        super.deleteSpecification(resourceIds, resources, session)
        cleanupAfterShare(resources, session)
    }

    suspend fun updatePermissions(
        actorAndProject: ActorAndProject,
        request: SharesUpdatePermissionsRequest
    ) {
        db.withSession { session ->
            val ids = request.items.map { it.id }.toSet()
            val sharesById = retrieveBulk(actorAndProject, ids, listOf(Permission.ADMIN), ctx = session)
                .associateBy { it.id }

            val rejectedShare = sharesById.values.find { it.status.state == Share.State.REJECTED }
            if (rejectedShare != null) {
                throw RPCException(
                    "'${rejectedShare.specification.sourceFilePath.fileName()}' " +
                            "(${rejectedShare.specification.sharedWith}) has been rejected and cannot be " +
                            "updated further",
                    HttpStatusCode.BadRequest
                )
            }

            session.sendPreparedStatement(
                {
                    request.items.split {
                        into("ids") { it.id }
                        into("permissions") { reqItem ->
                            reqItem.permissions.filter { it.canBeGranted }.joinToString(",") { it.name }
                        }
                        into("should_update_permissions") { reqItem ->
                            sharesById.getValue(reqItem.id).status.state == Share.State.APPROVED
                        }
                    }
                },
                """
                    with 
                        requests as (
                            select unnest(:ids::bigint[]) id,
                                   unnest(:should_update_permissions::boolean[]) should_update_permissions,
                                   regexp_split_to_array(unnest(:permissions::text[]), ',') new_permissions
                        ),
                        updated_shares as (
                            update file_orchestrator.shares s
                            set permissions = new_permissions
                            from requests req
                            where s.resource = req.id
                            returning resource, shared_with, available_at
                        ),
                        deleted_entries as (
                            delete from provider.resource_acl_entry entry
                            using
                                requests req join
                                updated_shares share on req.id = share.resource
                            where
                                share.available_at is not null and
                                entry.username = share.shared_with and
                                entry.resource_id = (regexp_split_to_array(share.available_at, '/'))[2]::bigint and
                                not (entry.permission = some(req.new_permissions))
                        )
                    insert into provider.resource_acl_entry (group_id, username, permission, resource_id) 
                    select 
                        null, 
                        share.shared_with, 
                        unnest(req.new_permissions),
                        (regexp_split_to_array(share.available_at, '/'))[2]::bigint
                    from
                        requests req join
                        updated_shares share on req.id = share.resource
                    where
                        share.available_at is not null
                    on conflict do nothing
                """
            )
        }
    }

    suspend fun browseOutgoing(
        actorAndProject: ActorAndProject,
        pagination: WithPaginationRequestV2
    ): PageV2<OutgoingShareGroup> {
        @Suppress("SqlResolve")
        return db.paginateV2(
            actorAndProject.actor,
            pagination.normalize(),
            create = { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("username", actorAndProject.actor.safeUsername())
                        },
                        """
                            declare c cursor for
                            with
                                spec as (
                                    select original_file_path, shared_with, permissions, state, id, name, category,
                                           provider
                                    from (
                                        select
                                            rank() over (partition by share.original_file_path order by shared_with) rn,
                                            share.original_file_path,
                                            share.shared_with,
                                            share.permissions,
                                            share.state,
                                            resc.id,
                                            p.name,
                                            pc.category,
                                            pc.provider
                                        from
                                            provider.resource resc join
                                            accounting.products p on resc.product = p.id join
                                            accounting.product_categories pc on p.category = pc.id join
                                            file_orchestrator.shares share on resc.id = share.resource
                                        where
                                            resc.created_by = :username
                                    ) t
                                    where t.rn <= 11
                                )
                            select jsonb_build_object(
                                'sourceFilePath', spec.original_file_path,
                                'storageProduct', jsonb_build_object(
                                    'id', name,
                                    'category', category,
                                    'provider', provider
                                ),
                                'sharePreview', jsonb_agg(
                                    jsonb_build_object(
                                        'sharedWith', spec.shared_with,
                                        'permissions', spec.permissions,
                                        'state', spec.state,
                                        'shareId', spec.id::text
                                    )
                                )
                            )
                            from spec
                            group by spec.original_file_path, spec.name, spec.category, spec.provider
                            order by spec.original_file_path
                        """,
                    )
            },
            mapper = { _, rows ->
                rows.map { defaultMapper.decodeFromString(it.getString(0)!!) }
            }
        )
    }

    override suspend fun browseQuery(flags: ShareFlags?, query: String?): PartialQuery {
        return PartialQuery(
            {
                setParameter("filter_path", flags?.filterOriginalPath?.normalize())
                setParameter("filter_ingoing", flags?.filterIngoing)
                setParameter("filter_rejected", flags?.filterRejected)
                setParameter("query", query)
            },
            """
                select *
                from file_orchestrator.shares s
                where
                    (:filter_path::text is null or :filter_path = s.original_file_path) and
                    (
                        :filter_ingoing::boolean is null or
                        (:filter_ingoing = true and :username = s.shared_with) or
                        (:filter_ingoing = false and :username is distinct from s.shared_with)
                    ) and
                    (
                        :filter_rejected::boolean is null or
                        (:filter_rejected = true and 'REJECTED' is distinct from s.state) or
                        (:filter_rejected = false and true)
                    ) and
                    (
                        :query::text is null or
                        s.original_file_path ilike '%' || :query || '%' or
                        s.shared_with ilike '%' || :query || '%'
                    )
            """
        )
    }
}
