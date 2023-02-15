package dk.sdu.cloud.file.orchestrator.service

import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.WithPaginationRequestV2
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.ProductType
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.accounting.util.ProviderSupport
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.notification.api.NotificationType
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import java.time.OffsetDateTime
import java.util.*

typealias ShareSvc = ResourceService<Share, Share.Spec, Share.Update, ShareFlags, Share.Status, Product.Storage,
        ShareSupport, StorageCommunication>

class ShareService(
    projectCache: ProjectCache,
    db: AsyncDBSessionFactory,
    providers: Providers<StorageCommunication>,
    support: ProviderSupport<StorageCommunication, Product.Storage, ShareSupport>,
    serviceClient: AuthenticatedClient,
    private val files: FilesService,
    private val collections: FileCollectionService
) : ShareSvc(projectCache, db, providers, support, serviceClient) {
    override val table = SqlObject.Table("file_orchestrator.shares")
    override val defaultSortColumn = SqlObject.Column(table, "original_file_path")
    override val sortColumns: Map<String, SqlObject.Column> = mapOf(
        "sourceFilePath" to defaultSortColumn
    )

    override val serializer = Share.serializer()
    override val updateSerializer = Share.Update.serializer()
    override val productArea = ProductType.STORAGE

    override fun userApi() = Shares
    override fun controlApi() = SharesControl
    override fun providerApi(comms: ProviderComms) = SharesProvider(comms.provider.id)

    init {
        files.addMoveHandler(::onFilesMoved)
        files.addTrashHandler(::onFilesDeleted)
        collections.addDeleteHandler(::onFileCollectionDeleted)
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

    private suspend fun onFilesDeleted(request: List<FindByPath>) {
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    request.split { into("paths") { it.id.normalize() } }
                    request.split { into("available_at") { "/${it.id.normalize().split('/')[1]}" }}
                },
                """
                    with
                        entries as (
                            select
                                unnest(:paths::text[]) path,
                                unnest(:available_at::text[]) available_at
                        ),
                        original_paths as (
                            select resource as share_id, original_file_path, s.available_at, path share_path
                            from file_orchestrator.shares s join entries on s.available_at=entries.available_at
                        ),
                        sub_shares as (
                            select s.original_file_path
                            from original_paths op
                            join file_orchestrator.shares s on (
                                s.original_file_path like (
                                    select (
                                        op.original_file_path || (select regexp_replace(op.share_path, '([\/]\d*)', '') || '/%')
                                    )
                                )
                            )
                        ),
                        affected_shares as (
                            delete from file_orchestrator.shares s
                            using entries e left join sub_shares sub on original_file_path = e.path or original_file_path = sub.original_file_path
                            where
                                e.path = s.original_file_path or
                                sub.original_file_path = s.original_file_path or
                                s.original_file_path like e.path || '/%'
                            returning s.resource, s.shared_with, s.permissions, s.state, split_part(s.available_at, '/', 2) available_at
                        ),
                        affected_file_collections as (
                            delete from file_orchestrator.file_collections fc
                            using affected_shares
                            where
                            fc.resource::text = affected_shares.available_at
                            returning fc.resource
                        ),
                        delete_resources_updates as (
                            delete from provider.resource_update ru
                            using affected_shares share , affected_file_collections fc
                            where
                            ru.resource = share.resource or ru.resource = fc.resource
                        ),
                        delete_resource_acl as (
                            delete from provider.resource_acl_entry entry
                            using affected_shares share, affected_file_collections fc
                            where
                                share.resource = entry.resource_id or fc.resource = entry.resource_id
                        )
                    delete from provider.resource r
                            using affected_shares share, affected_file_collections fc
                            where
                            r.id = share.resource or r.id = fc.resource
                """
            )
        }
    }

    private suspend fun onFileCollectionDeleted(request: BulkRequest<FindByStringId>) {
        db.withSession { session ->
            val shares = session.sendPreparedStatement(
                {
                    setParameter("ids", request.items.map { "/${it.id}/%" })
                },
                """
                    select s.resource, s.available_at
                    from
                        file_orchestrator.shares s
                    where original_file_path like any((select unnest(:ids::text[])))
                """
            ).rows.associate {
                Pair(it.getLong(0)!!, it.getString(1)!!.removePrefix("/"))
            }

            if (shares.isNotEmpty()) {
                session.sendPreparedStatement(
                    {
                        setParameter("ids", shares.values.toList())
                    },
                    "delete from file_orchestrator.file_collections where resource = any(:ids::bigint[])"
                )

                val shareIds = shares.keys.toList()

                session.sendPreparedStatement(
                    {
                        setParameter("ids", shareIds)
                    },
                    "delete from file_orchestrator.shares where resource = some(:ids::bigint[])"
                )

                session.sendPreparedStatement(
                    {
                        setParameter("ids", shareIds)
                    },
                    "delete from provider.resource_acl_entry where resource_id = some(:ids)"
                )

                session.sendPreparedStatement(
                    {
                        setParameter("ids", shareIds)
                    },
                    "delete from provider.resource_update where resource = some(:ids)"
                )

                session.sendPreparedStatement(
                    {
                        setParameter("ids", shareIds)
                    },
                    "delete from provider.resource where id = some(:ids)"
                )
            }
        }
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
        if (actorAndProject.project != null) {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Shares not possible from projects")
        }

        val shareWithSelf = idWithSpec.find { it.second.sharedWith == actorAndProject.actor.safeUsername() }
        if (shareWithSelf != null) {
            throw RPCException(
                "You cannot share '${shareWithSelf.second.sourceFilePath.fileName()}' with yourself",
                HttpStatusCode.BadRequest
            )
        }
        //Check users exists
        val returnedUsers = session.sendPreparedStatement(
            {
                idWithSpec.split {
                    into("username") { it.second.sharedWith }
                }
            },
            """
                select *
                from "auth".principals
                where id in (select unnest(:username::text[]))
            """
        ).rows

        if (idWithSpec.size != returnedUsers.size) {
            throw RPCException("Unknown user. Did you write the correct user ID?", HttpStatusCode.BadRequest)
        }

        val collIds = idWithSpec.map { extractPathMetadata(it.second.sourceFilePath).collection }.toSet()
        collections.retrieveBulk(
            actorAndProject,
            collIds,
            listOf(Permission.ADMIN),
            ctx = session
        )

        try {
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
        } catch (ex: GenericDatabaseException) {
            if (ex.errorCode == "23505") {
                throw RPCException.fromStatusCode(HttpStatusCode.Conflict, "File has already been shared. Check shares page.")
            }
            else throw ex
        }

        idWithSpec.forEach {
            NotificationDescriptions.create.call(
                CreateNotification(
                    it.second.sharedWith,
                    Notification(
                        NotificationType.SHARE_REQUEST.name,
                        "${actorAndProject.actor.safeUsername()} wants to share a folder with you",
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

    override suspend fun browseQuery(actorAndProject: ActorAndProject, flags: ShareFlags?, query: String?): PartialQuery {
        return PartialQuery(
            {
                setParameter("filter_path", flags?.filterOriginalPath?.normalize())
                setParameter("filter_ingoing", flags?.filterIngoing)
                setParameter("filter_rejected", flags?.filterRejected)
                setParameter("query", query)
            },
            """
                select s.*
                from
                    accessible_resources resc join
                    file_orchestrator.shares s on (resc.r).id = resource
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

    suspend fun createInviteLink(actorAndProject: ActorAndProject, request: SharesCreateInviteLinkRequest, ctx: DBContext = db): ShareInviteLink {
        val token = UUID.randomUUID().toString()

        if (actorAndProject.project != null) {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Shares not possible from projects")
        }

        val collectionId = extractPathMetadata(request.path).collection
        val provider = collections.retrieve(actorAndProject, collectionId, null, ctx = ctx).specification.product.provider
        val products = support.retrieveProducts(listOf(provider))[provider]

        if (products.isNullOrEmpty()) {
            throw RPCException("Shares not supported by provider", HttpStatusCode.BadRequest)
        }

        return ctx.withSession { session ->
            val count = session.sendPreparedStatement(
                {
                    setParameter("file_path", request.path)
                },
                """
                    select count(*) from file_orchestrator.shares_links where file_path = :file_path and now() < expires
                """
            ).rows.first().getLong("count") ?: 0

            if (count >= 2) {
                throw RPCException(
                    "Unable to create more invitation links for this folder",
                    HttpStatusCode.BadRequest
                )
            }

            session.sendPreparedStatement(
                {
                    setParameter("file_path", request.path)
                    setParameter("token", token)
                    setParameter("shared_by", actorAndProject.actor.safeUsername())
                },
                """
                    insert into file_orchestrator.shares_links (file_path, shared_by, token, expires) values
                        (:file_path, :shared_by, :token, now() + '10 days')
                        returning token, expires, permissions
                """
            ).rows.firstNotNullOf { row ->
                val permissions =  row.getAs<List<String>>("permissions") ?:
                    throw RPCException("An error occurred while trying to create link", HttpStatusCode.BadRequest)

                ShareInviteLink(
                    row.getAs<UUID>("token").toString(),
                    row.getAs<OffsetDateTime>("expires").toInstant().toEpochMilli(),
                    permissions.map { Permission.valueOf(it) }
                )
            }
        }
    }

    suspend fun browseInviteLinks(actorAndProject: ActorAndProject, request: SharesBrowseInviteLinksRequest, ctx: DBContext = db): PageV2<ShareInviteLink> {
        // TODO(Brian): Check permissions

        val result = ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("file_path", request.path)
                },
                """
                    select token, expires, permissions from file_orchestrator.shares_links
                    where file_path = :file_path and now() < expires
                    order by expires desc
                """
            ).rows.groupBy { it.getAs<UUID>("token") }.map { entry ->
                ShareInviteLink(
                    entry.key.toString(),
                    entry.value.first().getAs<OffsetDateTime>("expires").toInstant().toEpochMilli(),
                    entry.value.first().getAs<List<String>>("permissions").map { Permission.valueOf(it) }
                )
            }
        }

        return PageV2(20, result, null)
    }

    suspend fun deleteInviteLink(
        actorAndProject: ActorAndProject,
        request: SharesDeleteInviteLinkRequest,
        ctx: DBContext = db
    ) {
        // TODO(Brian): Check permissions

        ctx.withSession { session ->
            val success = session.sendPreparedStatement(
                {
                    setParameter("file_path", request.path)
                    setParameter("token", request.token)
                },
                """
                    delete from file_orchestrator.shares_links where file_path = :file_path and token = :token
                """
            ).rowsAffected > 0

            if (!success) {
                throw RPCException(
                    "Unable to delete share link",
                    HttpStatusCode.BadRequest
                )
            }
        }
    }

    suspend fun updateInviteLinkPermissions(
        actorAndProject: ActorAndProject,
        request: SharesUpdateInviteLinkPermissionsRequest,
        ctx: DBContext = db
    ) {
        // TODO(Brian): Check permissions

        ctx.withSession { session ->
            val success = session.sendPreparedStatement(
                {
                    setParameter("file_path", request.path)
                    setParameter("token", request.token)
                    setParameter("permissions", "{" +
                        request.permissions.filter { it.canBeGranted }.joinToString(",") { it.name } + "}")
                },
                """
                    update file_orchestrator.shares_links
                    set permissions = :permissions::text[]
                    where file_path = :file_path and token = :token
                """
            ).rowsAffected > 0

            if (!success) {
                throw RPCException(
                    "Link might not be valid anymore or have been deleted",
                    HttpStatusCode.BadRequest
                )
            }
        }
    }

    suspend fun acceptInviteLink(
        actorAndProject: ActorAndProject,
        request: SharesAcceptInviteLinkRequest,
        ctx: DBContext = db
    ) : SharesAcceptInviteLinkResponse {
        data class ShareLinkInfo(
            val sharedBy: String,
            val permissions: List<Permission>,
            val path: String
        )

        return ctx.withSession { session ->
            val linkInfo = session.sendPreparedStatement(
                {
                    setParameter("token", request.token)
                },
                """
                    select file_path, permissions, shared_by
                    from file_orchestrator.shares_links l
                    where
                        now() < expires and
                        token = cast(:token as uuid)
                """
            ).rows.map { entry ->
                val filePath = entry.getString("file_path")
                val permissions = entry.getAs<List<String>>("permissions")?.map { Permission.valueOf(it) }
                val sharedBy = entry.getString("shared_by")

                if (filePath == null || permissions == null || sharedBy == null) {
                    throw RPCException("Link expired", HttpStatusCode.BadRequest)
                }

                ShareLinkInfo(sharedBy, permissions, filePath)
            }.firstOrNull() ?: throw RPCException("Link expired", HttpStatusCode.BadRequest)

            val owner = ActorAndProject(Actor.SystemOnBehalfOfUser(linkInfo.sharedBy), null)

            // TODO(Brian): Check if file still exists
            files.retrieve(owner, linkInfo.path, null, ctx)

            val collectionId = extractPathMetadata(linkInfo.path).collection
            val provider = collections.retrieve(owner, collectionId, null, ctx = ctx).specification.product.provider

            log.debug("$provider")
            val product = support.retrieveProducts(listOf(provider))[provider]?.firstOrNull()?.product
                ?: throw RPCException("Shares not supported by provider", HttpStatusCode.BadRequest)

            // Create share
            val share = this.create(
                owner,
                bulkRequestOf(
                    Share.Spec(
                        actorAndProject.actor.safeUsername(),
                        linkInfo.path,
                        linkInfo.permissions,
                        ProductReference(product.name, product.category.name, provider)
                    )
                ),
                ctx
            ).responses[0] ?: throw RPCException("Failed to accept invitation to share", HttpStatusCode.BadRequest)

            // Accept share
            this.approve(
                actorAndProject,
                bulkRequestOf(FindByStringId(share.id)),
            )

            this.retrieve(actorAndProject, share.id, null, ctx)
        }
    }

    suspend fun cleanUpInviteLinks(ctx: DBContext = db) {
        ctx.withSession { session ->
            val cleaned = session.sendPreparedStatement("""
                delete from file_orchestrator.shares_links where expires < now()
            """).rowsAffected

            log.debug("Cleaned up $cleaned expired shares invite links")
        }
    }
}
