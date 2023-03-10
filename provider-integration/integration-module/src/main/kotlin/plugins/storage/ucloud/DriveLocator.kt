package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ProviderRegisteredResource
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.config.ConfigSchema
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.file.orchestrator.api.FileCollection
import dk.sdu.cloud.file.orchestrator.api.FileCollectionIncludeFlags
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsControl
import dk.sdu.cloud.file.orchestrator.api.MemberFilesFilter
import dk.sdu.cloud.plugins.InternalFile
import dk.sdu.cloud.plugins.normalize
import dk.sdu.cloud.sql.DBContext
import dk.sdu.cloud.sql.ResultCursor
import dk.sdu.cloud.sql.SQL_TYPE_HINT_INT8
import dk.sdu.cloud.sql.SQL_TYPE_HINT_TEXT
import dk.sdu.cloud.sql.bindLongNullable
import dk.sdu.cloud.sql.bindStringNullable
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession

sealed class UCloudDrive {
    abstract val ucloudId: Long
    abstract val localReference: String?
    abstract val type: Type
    open var project: String? = null

    open fun toProviderId(): String? = null
    abstract fun withUCloudId(newId: Long): UCloudDrive

    data class PersonalWorkspace(
        override val ucloudId: Long,
        val username: String
    ) : UCloudDrive() {
        override val localReference: String = username
        override val type: Type = Type.PERSONAL_WORKSPACE

        override fun withUCloudId(newId: Long): UCloudDrive = PersonalWorkspace(newId, username)
        override fun toProviderId(): String = "h-$username"
    }

    data class ProjectRepository(
        override val ucloudId: Long,
        override var project: String?,
        val repository: String
    ) : UCloudDrive() {
        override val localReference: String = repository
        override val type: Type = Type.PROJECT_REPOSITORY

        override fun withUCloudId(newId: Long): UCloudDrive = ProjectRepository(newId, project, repository)
        override fun toProviderId(): String = "p-$project/$repository"
    }

    data class ProjectMemberFiles(
        override val ucloudId: Long,
        override var project: String?,
        val username: String
    ) : UCloudDrive() {
        override val localReference: String = username
        override val type: Type = Type.PROJECT_MEMBER_FILES

        override fun withUCloudId(newId: Long): UCloudDrive = ProjectMemberFiles(newId, project, username)
        override fun toProviderId(): String = "pm-$project/$username"
    }

    data class Collection(
        override val ucloudId: Long,
    ) : UCloudDrive() {
        override val localReference: String = ucloudId.toString()
        override val type: Type = Type.COLLECTION

        override fun withUCloudId(newId: Long): UCloudDrive = Collection(newId)
    }

    data class Share(
        override val ucloudId: Long,
        val shareId: String
    ) : UCloudDrive() {
        override val localReference: String = shareId
        override val type: Type = Type.SHARE

        override fun withUCloudId(newId: Long): UCloudDrive = Share(newId, shareId)
        override fun toProviderId(): String = "s-$shareId"
    }

    enum class Type {
        PERSONAL_WORKSPACE,
        PROJECT_REPOSITORY,
        PROJECT_MEMBER_FILES,
        COLLECTION,
        SHARE
    }

    companion object {
        const val PLACEHOLDER_ID = -1L

        fun parse(ucloudId: Long, providerId: String?): UCloudDrive {
            return when {
                providerId == null -> Collection(ucloudId)
                providerId.startsWith("s-") -> Share(ucloudId, providerId.removePrefix("s-"))
                providerId.startsWith("h-") -> PersonalWorkspace(ucloudId, providerId.removePrefix("h-"))
                providerId.startsWith("p-") -> {
                    val (project, repo) = providerId.removePrefix("p-").split("/")
                    ProjectRepository(ucloudId, project, repo)
                }

                providerId.startsWith("pm-") -> {
                    val (project, username) = providerId.removePrefix("pm-").split("/")
                    ProjectMemberFiles(ucloudId, project, username)
                }

                else -> Collection(ucloudId)
            }
        }
    }
}

typealias FsType = ConfigSchema.Plugins.Files.UCloud.FsType
typealias FsSystem = ConfigSchema.Plugins.Files.UCloud.System

data class DriveAndSystem(
    val drive: UCloudDrive,
    val system: FsSystem,
    val inMaintenanceMode: Boolean,
    val driveRoot: InternalFile?,
)

class DriveLocator(
    private val products: List<Product.Storage>,
    private val config: ConfigSchema.Plugins.Files.UCloud,
    private val serviceClient: AuthenticatedClient,
) {
    private val defaultSystem = config.systems.getValue(config.defaultSystem!!)
    private val legacySystem =
        if (config.systemUsedForLegacyDrives != null) config.systems.getValue(config.systemUsedForLegacyDrives)
        else defaultSystem
    private val allSystems = config.systems.values.toList()
    private val systemsSortedByMountPrefix = allSystems.sortedBy { it.mountPath }.reversed()

    private val defaultProduct by lazy {
        products.find { it.name != driveProjectHomeName && it.name != driveShareName }
            ?: error("Unable to resolve default product")
    }

    private val shareProduct by lazy {
        products.find { it.name == driveShareName }
            ?: error("Could not find product by name $driveShareName")
    }

    private val projectHomeProduct by lazy {
        products.find { it.name == driveProjectHomeName }
            ?: error("Could not find product by name $driveProjectHomeName")
    }

    suspend fun fillDriveDatabase() {
        val drives = ArrayList<FileCollection>()

        var next: String? = null
        while (true) {
            val page = FileCollectionsControl.browse.call(
                ResourceBrowseRequest(
                    FileCollectionIncludeFlags(MemberFilesFilter.DONT_FILTER_COLLECTIONS),
                    itemsPerPage = 250,
                    next = next
                ),
                serviceClient
            ).orThrow()
            drives.addAll(page.items)
            next = page.next ?: break
        }

        dbConnection.withSession { session ->
            drives.mapNotNull { drive ->
                runCatching {
                    UCloudDrive.parse(drive.id.toLong(), drive.providerGeneratedId).also {
                        it.project = drive.owner.project
                    }
                }.getOrNull()
            }.chunked(100).forEach { chunk ->
                session.prepareStatement(
                    """
                        insert into ucloud_storage_drives (collection_id, local_reference, project, type, system)
                        select 
                            unnest(:collection_ids::bigint[]), 
                            unnest(:references::text[]), 
                            unnest(:projects::text[]), 
                            unnest(:types::text[]),
                            :default_system
                        on conflict do nothing
                    """
                ).useAndInvokeAndDiscard {
                    bindList("collection_ids", chunk.map { it.ucloudId }, SQL_TYPE_HINT_INT8)
                    bindList("references", chunk.map { it.localReference }, SQL_TYPE_HINT_TEXT)
                    bindList("projects", chunk.map { it.project }, SQL_TYPE_HINT_TEXT)
                    bindList("types", chunk.map { it.type.name }, SQL_TYPE_HINT_TEXT)
                    bindString("default_system", legacySystem.name)
                }
            }
        }
    }

    /**
     * Use UCloudDrive.PLACEHOLDER_ID for the ucloudId if the drive isn't coming through the create call
     */
    suspend fun register(
        title: String,
        drive: UCloudDrive,
        ownedByProject: String? = null,
        createdByUser: String? = null,
        initiatedByEndUser: Boolean = false,
    ): DriveAndSystem {
        require(ownedByProject != null || createdByUser != null || initiatedByEndUser)
        require(!initiatedByEndUser || title.isEmpty())
        require(initiatedByEndUser || title.isNotEmpty())

        if (ownedByProject != null) {
            drive.project = ownedByProject
        }

        return dbConnection.withSession { session ->
            val (system, isAllowed) = when (drive) {
                is UCloudDrive.ProjectMemberFiles,
                is UCloudDrive.ProjectRepository -> {
                    var systemName: String? = null
                    var maintenanceMode = false

                    session.prepareStatement(
                        """
                            select system, in_maintenance_mode
                            from ucloud_storage_drives
                            where project = :project
                        """
                    ).useAndInvoke(
                        prepare = {
                            bindStringNullable("project", drive.project)
                        },
                        readRow = { row ->
                            systemName = row.getString(0)!!
                            maintenanceMode = row.getBoolean(1)!!
                        }
                    )

                    val system = allSystems.find { it.name == systemName }
                        ?: throw RPCException("Unknown system", HttpStatusCode.InternalServerError)

                    Pair(system, !maintenanceMode)
                }

                else -> Pair(defaultSystem, true)
            }

            if (!isAllowed) {
                throw RPCException(
                    "This drive is currently in maintenance mode. Try again later.",
                    HttpStatusCode.BadGateway,
                    "MAINTENANCE"
                )
            }

            var responseCode = HttpStatusCode.OK
            val id = if (initiatedByEndUser) {
                drive.ucloudId
            } else {
                val response = FileCollectionsControl.register.call(
                    bulkRequestOf(
                        ProviderRegisteredResource(
                            FileCollection.Spec(title, driveToProduct(drive)),
                            providerGeneratedId = drive.toProviderId(),
                            createdBy = createdByUser,
                            project = ownedByProject
                        )
                    ),
                    serviceClient
                )

                responseCode = response.statusCode
                response.orNull()?.responses?.single()?.id?.toLong()
            }

            if (id != null) {
                session.prepareStatement(
                    """
                        insert into ucloud_storage_drives(collection_id, local_reference, project, type, system)
                        values (:collection_id::bigint, :local_reference::text, :project::text, :type::text, :system::text)
                        on conflict (collection_id) do update set system = excluded.system
                    """
                ).useAndInvokeAndDiscard {
                    bindLong("collection_id", id)
                    bindStringNullable("local_reference", drive.localReference)
                    bindStringNullable("project", drive.project)
                    bindString("type", drive.type.name)
                    bindString("system", system.name)
                }

                DriveAndSystem(
                    drive.withUCloudId(id),
                    system,
                    false,
                    driveToInternalFile(system, drive)
                )
            } else {
                if (responseCode == HttpStatusCode.Conflict) {
                    val resolvedId = FileCollectionsControl.browse.call(
                        ResourceBrowseRequest(
                            FileCollectionIncludeFlags(
                                MemberFilesFilter.DONT_FILTER_COLLECTIONS,
                                filterProviderIds = drive.toProviderId()
                            ),
                        ),
                        serviceClient
                    ).orThrow().items.firstOrNull()?.id?.toLongOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)

                    resolveDrive(resolvedId, ctx = session)
                } else {
                    throw RPCException.fromStatusCode(responseCode)
                }
            }
        }
    }

    suspend fun resolveDrive(drive: UCloudDrive): DriveAndSystem {
        val resolvedId = FileCollectionsControl.browse.call(
            ResourceBrowseRequest(
                FileCollectionIncludeFlags(
                    MemberFilesFilter.DONT_FILTER_COLLECTIONS,
                    filterProviderIds = drive.toProviderId()
                ),
            ),
            serviceClient
        ).orThrow().items.firstOrNull()?.id?.toLongOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)

        return resolveDrive(resolvedId)
    }

    suspend fun resolveDrive(
        driveId: Long,
        allowMaintenanceMode: Boolean = false,
        ctx: DBContext = dbConnection,
    ): DriveAndSystem {
        return ctx.withSession { session ->
            var driveAndSystem: DriveAndSystem? = null
            session.prepareStatement(
                """
                    select collection_id, local_reference, project, type, system, in_maintenance_mode
                    from ucloud_storage_drives
                    where collection_id = :collection_id
                """
            ).useAndInvoke(
                prepare = {
                    bindLong("collection_id", driveId)
                },

                readRow = { row ->
                    driveAndSystem = mapDriveRow(row, allowMaintenanceMode)
                }
            )

            driveAndSystem ?: throw RPCException("Unknown drive: $driveId", HttpStatusCode.NotFound)
        }
    }

    suspend fun resolveDriveByInternalFile(path: InternalFile): DriveAndSystem {
        fun unknownFile(): Nothing =
            throw RPCException("Unrecognizable file at: ${path.path}", HttpStatusCode.InternalServerError)

        val system = systemsSortedByMountPrefix.find { path.normalize().path.startsWith(it.mountPath) } ?: unknownFile()

        val components = path.path.removePrefix(system.mountPath).split("/")
        if (components.isEmpty()) unknownFile()

        val type: UCloudDrive.Type
        val localReference: String
        var project: String? = null

        when (components[0]) {
            PathConverter.HOME_DIRECTORY -> {
                if (components.size < 2) unknownFile()
                type = UCloudDrive.Type.PERSONAL_WORKSPACE
                localReference = components[1]
            }

            PathConverter.PROJECT_DIRECTORY -> {
                if (components.size < 3) unknownFile()
                project = components[1]
                if (components[2] == PathConverter.PERSONAL_REPOSITORY) {
                    if (components.size < 4) unknownFile()
                    type = UCloudDrive.Type.PROJECT_MEMBER_FILES
                    localReference = components[3]
                } else {
                    type = UCloudDrive.Type.PROJECT_REPOSITORY
                    localReference = components[2]
                }
            }

            PathConverter.COLLECTION_DIRECTORY -> {
                type = UCloudDrive.Type.COLLECTION
                localReference = components[1]
            }

            else -> unknownFile()
        }

        var driveAndSystem: DriveAndSystem? = null

        dbConnection.withSession { session ->
            session.prepareStatement(
                """
                    select collection_id, local_reference, project, type, system, in_maintenance_mode
                    from ucloud_storage_drives
                    where
                        type = :type and
                        local_reference = :local_reference and
                        (:project::text is null or project = :project::text)
                """
            ).useAndInvoke(
                prepare = {
                    bindString("type", type.name)
                    bindString("local_reference", localReference)
                    bindStringNullable("project", project)
                },

                readRow = { row ->
                    if (driveAndSystem != null) {
                        error("Found more than one drive matching: ${path.path}")
                    }

                    driveAndSystem = mapDriveRow(row, true)
                }
            )
        }

        return driveAndSystem ?: unknownFile()
    }

    suspend fun enumerateDrives(
        filterType: UCloudDrive.Type? = null,
        next: String? = null
    ): PageV2<DriveAndSystem> {
        val items = ArrayList<DriveAndSystem>()
        dbConnection.withSession { session ->
            session.prepareStatement(
                """
                    select collection_id, local_reference, project, type, system, in_maintenance_mode
                    from ucloud_storage_drives drive
                    where
                        (
                            :type::text is null or
                            :type::text = drive.type
                        ) and
                        (
                            :next::bigint is null or
                            drive.collection_id > :next::bigint
                        )
                    order by collection_id asc
                    limit 1000
                """
            ).useAndInvoke(
                prepare = {
                    bindLongNullable("next", next?.toLongOrNull())
                    bindStringNullable("type", filterType?.name)
                },

                readRow = { row ->
                    items.add(mapDriveRow(row, true))
                }
            )
        }

        return PageV2(
            itemsPerPage = 1000,
            items = items,
            next = if (items.size < 1000) {
                items.lastOrNull()?.drive?.ucloudId?.toString()
            } else {
                null
            }
        )
    }

    private suspend fun mapDriveRow(
        row: ResultCursor,
        allowMaintenanceMode: Boolean,
    ): DriveAndSystem {
        val driveId = row.getLong(0)!!
        val localReference = row.getString(1)
        val project = row.getString(2)
        val type = UCloudDrive.Type.valueOf(row.getString(3)!!)
        val system = row.getString(4)!!
        val maintenanceMode = row.getBoolean(5)!!
        val resolvedSystem = allSystems.find { it.name == system }
            ?: error("Unknown system: $system")

        if (!allowMaintenanceMode && maintenanceMode) {
            throw RPCException(
                "This drive is currently in maintenance mode. Try again later.",
                HttpStatusCode.BadGateway,
                "MAINTENANCE"
            )
        }

        val drive = when (type) {
            UCloudDrive.Type.PERSONAL_WORKSPACE -> {
                UCloudDrive.PersonalWorkspace(driveId, localReference!!)
            }

            UCloudDrive.Type.PROJECT_REPOSITORY -> {
                UCloudDrive.ProjectRepository(driveId, project!!, localReference!!)
            }

            UCloudDrive.Type.PROJECT_MEMBER_FILES -> {
                UCloudDrive.ProjectMemberFiles(driveId, project!!, localReference!!)
            }

            UCloudDrive.Type.COLLECTION -> {
                UCloudDrive.Collection(driveId)
            }

            UCloudDrive.Type.SHARE -> {
                UCloudDrive.Share(driveId, localReference!!)
            }
        }

        return DriveAndSystem(
            drive,
            resolvedSystem,
            maintenanceMode,
            driveToInternalFile(resolvedSystem, drive),
        )
    }

    fun driveToProduct(drive: UCloudDrive): ProductReference {
        return when (drive) {
            is UCloudDrive.Collection -> defaultProduct.toReference()
            is UCloudDrive.PersonalWorkspace -> defaultProduct.toReference()
            is UCloudDrive.ProjectMemberFiles -> projectHomeProduct.toReference()
            is UCloudDrive.ProjectRepository -> defaultProduct.toReference()
            is UCloudDrive.Share -> shareProduct.toReference()
        }
    }

    private fun driveToInternalFile(system: FsSystem, drive: UCloudDrive): InternalFile? {
        val subpath = driveToSubPath(drive) ?: return null
        return InternalFile(system.mountPath.removeSuffix("/") + "/" + subpath)
    }

    private fun driveToSubPath(drive: UCloudDrive): String? {
        return when (drive) {
            is UCloudDrive.Collection -> listOf(
                PathConverter.COLLECTION_DIRECTORY,
                drive.ucloudId
            )

            is UCloudDrive.PersonalWorkspace -> listOf(
                PathConverter.HOME_DIRECTORY,
                drive.username
            )

            is UCloudDrive.ProjectMemberFiles -> listOf(
                PathConverter.PROJECT_DIRECTORY,
                drive.project,
                PathConverter.PERSONAL_REPOSITORY,
                drive.username
            )

            is UCloudDrive.ProjectRepository -> listOf(
                PathConverter.PROJECT_DIRECTORY,
                drive.project,
                drive.repository
            )

            is UCloudDrive.Share -> null
        }?.joinToString("/")
    }

    private fun Product.toReference() = ProductReference(name, category.name, category.provider)
}
