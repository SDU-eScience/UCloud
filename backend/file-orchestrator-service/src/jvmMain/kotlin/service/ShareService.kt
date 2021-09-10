package dk.sdu.cloud.file.orchestrator.service

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductType
import dk.sdu.cloud.accounting.util.ProviderComms
import dk.sdu.cloud.accounting.util.ProviderSupport
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.accounting.util.ResourceService
import dk.sdu.cloud.accounting.util.SqlObject
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.file.orchestrator.api.FilesDeleteRequest
import dk.sdu.cloud.file.orchestrator.api.FilesMoveRequest
import dk.sdu.cloud.file.orchestrator.api.FilesMoveRequestItem
import dk.sdu.cloud.file.orchestrator.api.FindByPath
import dk.sdu.cloud.file.orchestrator.api.Share
import dk.sdu.cloud.file.orchestrator.api.ShareFlags
import dk.sdu.cloud.file.orchestrator.api.ShareSupport
import dk.sdu.cloud.file.orchestrator.api.Shares
import dk.sdu.cloud.file.orchestrator.api.SharesControl
import dk.sdu.cloud.file.orchestrator.api.SharesProvider
import dk.sdu.cloud.file.orchestrator.api.extractPathMetadata
import dk.sdu.cloud.file.orchestrator.api.normalize
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.parameterList
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*
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

    override suspend fun createSpecifications(
        actorAndProject: ActorAndProject,
        idWithSpec: List<Pair<Long, Share.Spec>>,
        session: AsyncDBConnection,
        allowDuplicates: Boolean
    ) {
        val collIds = idWithSpec.map { extractPathMetadata(it.second.sourceFilePath).collection }.toSet()
        collections.retrieveBulk(
            actorAndProject,
            collIds,
            listOf(Permission.Admin),
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
            listOf(Permission.Read),
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
            val shares = retrieveBulk(actorAndProject, request.items.map { it.id }, listOf(Permission.Read),
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
                                regexp_split_to_array(s.available_at, '/')[2]::bigint collection
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
            val shares = retrieveBulk(actorAndProject, request.items.map { it.id }, listOf(Permission.Read),
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
                                    else regexp_split_to_array(s.available_at, '/')[2]::bigint
                                end as collection
                        )
                    delete from provider.resource_acl_entry entry
                    using affected_shares share
                    where
                        share.collection = entry.resource_id and
                        share.shared_with = entry.username and
                        entry.permission = some(entry.permission)
                """
            )
        }
    }
}
