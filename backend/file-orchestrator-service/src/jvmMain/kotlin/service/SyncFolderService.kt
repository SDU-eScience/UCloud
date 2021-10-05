package dk.sdu.cloud.file.orchestrator.service

import com.github.jasync.sql.db.util.length
import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.ProductType
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.provider.api.ResourcePermissions
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.db.async.*
import kotlinx.serialization.serializer

typealias FolderSvcSuper = ResourceService<SyncFolder, SyncFolder.Spec, SyncFolder.Update, SyncFolderIncludeFlags,
    SyncFolder.Status, Product.Synchronization, SyncFolderSupport, SimpleProviderCommunication>

class SyncFolderService(
    db: AsyncDBSessionFactory,
    providers: Providers<SimpleProviderCommunication>,
    support: ProviderSupport<SimpleProviderCommunication, Product.Synchronization, SyncFolderSupport>,
    serviceClient: AuthenticatedClient,
    private val files: FilesService,
    private val fileCollectionService: FileCollectionService,
) : FolderSvcSuper(db, providers, support, serviceClient) {
    override val table = SqlObject.Table("file_orchestrator.sync_folders")
    override val defaultSortColumn = SqlObject.Column(table, "resource")
    override val sortColumns: Map<String, SqlObject.Column> = mapOf("resource" to defaultSortColumn)
    override val serializer = serializer<SyncFolder>()
    override val updateSerializer = serializer<SyncFolder.Update>()
    override val productArea = ProductType.SYNCHRONIZATION

    override fun userApi() = SyncFolders
    override fun controlApi() = SyncFolderControl
    override fun providerApi(comms: ProviderComms) = SyncFolderProvider(comms.provider.id)

    init {
        files.addMoveHandler(::onFilesMoved)
        files.addDeleteHandler(::onFilesDeleted)
    }

    private suspend fun onFilesMoved(batch: List<FilesMoveRequestItem>) {
        removeSyncFolders(batch.map { it.oldId })
    }

    private suspend fun onFilesDeleted(request: List<FindByStringId>) {
        removeSyncFolders(request.map { it.id })
    }

    private suspend fun removeSyncFolders(paths: List<String>) {
        db.withSession { session ->
            val affectedFolders = session.sendPreparedStatement(
                {
                    setParameter("ids", paths)
                    setParameter("parentIds", paths.map { "$it/%" })
                },
                """
                        select * from provider.resource r
                        join file_orchestrator.sync_folders f on f.resource = r.id
                        join accounting.product_categories c on r.product = c.id
                        where type = 'sync_folder' and (
                            f.path in (select unnest(:ids::text[])) or
                            f.path like any((select unnest(:parentIds::text[])))
                        )
                    """
            ).rows.map {
                SyncFolder(
                    (it.getLong("resource") ?: 0).toString(),
                    SyncFolder.Spec(
                        it.getString("path").orEmpty(),
                        ProductReference(
                            (it.getLong("product") ?: 0).toString(),
                            it.getString("category").orEmpty(),
                            it.getString("provider").orEmpty(),
                        )
                    ),
                    0L,
                    SyncFolder.Status(),
                    emptyList(),
                    ResourceOwner(
                        it.getString("created_by").orEmpty(),
                        null
                    ),
                    ResourcePermissions(emptyList(), emptyList())
                )
            }

            println(affectedFolders)

            if (affectedFolders.length > 0) {
                proxy.bulkProxy(
                    ActorAndProject(Actor.System, null),
                    bulkRequestOf(affectedFolders.map { it.id }),
                    BulkProxyInstructions.pureProcedure(
                        service = this,
                        retrieveCall = { providerApi(it).delete },
                        requestToId = { it },
                        resourceToRequest = { req, res -> res },
                        verifyRequest = { _, _, _ -> {} }
                    )
                )

                session.sendPreparedStatement(
                    {
                        setParameter("ids", affectedFolders.map { it.id })
                    },
                    """
                            delete from file_orchestrator.sync_folders
                            where resource in (select unnest(:ids::bigint[]))
                        """
                )

                session.sendPreparedStatement(
                    {
                        setParameter("ids", affectedFolders.map { it.id })
                    },
                    """
                            delete from provider.resource_acl_entry
                            where resource_id in (select unnest(:ids::bigint[]))
                        """
                )

                session.sendPreparedStatement(
                    {
                        setParameter("ids", affectedFolders.map { it.id })
                    },
                    """               
                            delete from provider.resource_update
                            where resource in (select unnest(:ids::bigint[]))
                        """
                )

                session.sendPreparedStatement(
                    {
                        setParameter("ids", affectedFolders.map { it.id })
                    },
                    """
                            delete from provider.resource
                            where id in (select unnest(:ids::bigint[]))
                        """
                )
            }
        }
    }

    override suspend fun createSpecifications(
        actorAndProject: ActorAndProject,
        idWithSpec: List<Pair<Long, SyncFolder.Spec>>,
        session: AsyncDBConnection,
        allowDuplicates: Boolean
    ) {
        val collectionIds = idWithSpec.map {
            extractPathMetadata(it.second.path).collection
        }.toSet()

        val fileCollections = fileCollectionService.retrieveBulk(
            actorAndProject,
            collectionIds,
            listOf(Permission.READ)
        )

        session
            .sendPreparedStatement(
                {
                    idWithSpec.split {
                        into("ids") { it.first }
                        into("paths") { it.second.path }
                        into("permissions") { (_, spec) ->
                            val permissions = fileCollections.find {
                                it.id == extractPathMetadata(spec.path).collection
                            }!!.permissions!!.myself


                            if (permissions.contains(Permission.EDIT) || permissions.contains(Permission.ADMIN)) {
                                SynchronizationType.SEND_RECEIVE.name
                            } else {
                                SynchronizationType.SEND_ONLY.name
                            }

                        }
                    }
                },
                """
                    insert into file_orchestrator.sync_folders (resource, path, sync_type)
                    select unnest(:ids::bigint[]), unnest(:paths::text[]), unnest(:permissions::text[])
                    on conflict (resource) do nothing
                """
            )
    }

    override suspend fun browseQuery(flags: SyncFolderIncludeFlags?, query: String?): PartialQuery {
        println("Browsing with filterByPath: ${flags?.filterByPath}")
        return PartialQuery(
            {
                setParameter("query", query)
                setParameter("filter_path", flags?.filterByPath)
                setParameter("filter_devices", flags?.filterDeviceId)
            },
            """
                select *
                from file_orchestrator.sync_folders
                where
                    (:query::text is null or path ilike ('%' || :query || '%')) and
                    (:filter_path::text is null or :filter_path::text = path) and
                    (:filter_devices::text[] is null or
                        array_length(:filter_devices::text[], 1) < 1 or 
                        device_id in (select unnest(:filter_devices::text[]))
                    )
            """
        )
    }

    override suspend fun onUpdate(
        resources: List<SyncFolder>,
        updates: List<ResourceUpdateAndId<SyncFolder.Update>>,
        session: AsyncDBConnection
    ) {
        session.sendPreparedStatement(
            {
                setParameter("ids", updates.map { it.id })
                setParameter("devices", updates.map { it.update.deviceId })
            },
            """
                update file_orchestrator.sync_folders as f
                set device_id = c.device_id
                from (
                    select unnest(:ids::bigint[]), unnest(:devices::text[])
                ) as c(resource, device_id)
                where c.resource = f.resource
            """
        )

        super.onUpdate(resources, updates, session)
    }
}