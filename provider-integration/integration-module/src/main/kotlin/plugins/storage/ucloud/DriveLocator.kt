package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ProviderRegisteredResource
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.calls.BulkRequest
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
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.DBContext
import dk.sdu.cloud.sql.SQL_TYPE_HINT_BOOL
import dk.sdu.cloud.sql.SQL_TYPE_HINT_INT8
import dk.sdu.cloud.sql.SQL_TYPE_HINT_TEXT
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
import dk.sdu.cloud.toReadableStacktrace
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

private object DriveAndSystemStore {
    private val entries = ArrayList<DriveAndSystem>()
    private val mutex = Mutex()

    suspend fun fill(
        serviceClient: AuthenticatedClient,
        legacySystem: FsSystem,
        allSystems: List<FsSystem>,
        skipUCloudSynchronization: Boolean,
    ) {
        mutex.withLock {
            dbConnection.withSession { session ->
                session.prepareStatement(
                    """
                        select collection_id, local_reference, project, type, system, in_maintenance_mode
                        from ucloud_storage_drives
                    """
                ).useAndInvoke(
                    readRow = { row ->
                        val driveId = row.getLong(0)!!
                        val localReference = row.getString(1)
                        val project = row.getString(2)
                        val type = UCloudDrive.Type.valueOf(row.getString(3)!!)
                        val system = row.getString(4)!!
                        val maintenanceMode = row.getBoolean(5)!!
                        val resolvedSystem = allSystems.find { it.name == system }
                            ?: error("Unknown system: $system")

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

                        entries.add(DriveAndSystem(drive, resolvedSystem, maintenanceMode, null))
                    }
                )
            }
        }

        if (!skipUCloudSynchronization) {
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

            drives
                .mapNotNull { drive ->
                    runCatching {
                        UCloudDrive.parse(drive.id.toLong(), drive.providerGeneratedId).also {
                            if (drive.providerGeneratedId != null) it.project = drive.owner.project
                        }
                    }.getOrNull()
                }
                .chunked(100)
                .forEach { chunk ->
                    insert(chunk.map { DriveAndSystem(it, legacySystem, false, null) }, allowUpsert = false)
                }
        }
    }

    suspend fun insert(items: List<DriveAndSystem>, allowUpsert: Boolean, ctx: DBContext = dbConnection) {
        if (items.isEmpty()) return

        mutex.withLock {
            ctx.withSession { session ->
                session.prepareStatement(
                    buildString {
                        append(
                            """
                                insert into ucloud_storage_drives (collection_id, local_reference, project, type, 
                                    system, in_maintenance_mode)
                                select 
                                    unnest(:collection_ids::bigint[]), 
                                    unnest(:references::text[]), 
                                    unnest(:projects::text[]), 
                                    unnest(:types::text[]),
                                    unnest(:systems::text[]),
                                    unnest(:maintenance_mode::bool[])
                            """
                        )

                        if (allowUpsert) {
                            appendLine("on conflict (collection_id) do update set system = excluded.system")
                        } else {
                            appendLine("on conflict do nothing")
                        }
                    }
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindList("collection_ids", items.map { it.drive.ucloudId }, SQL_TYPE_HINT_INT8)
                        bindList("references", items.map { it.drive.localReference }, SQL_TYPE_HINT_TEXT)
                        bindList("projects", items.map { it.drive.project }, SQL_TYPE_HINT_TEXT)
                        bindList("types", items.map { it.drive.type.name }, SQL_TYPE_HINT_TEXT)
                        bindList("systems", items.map { it.system.name }, SQL_TYPE_HINT_TEXT)
                        bindList("maintenance_mode", items.map { it.inMaintenanceMode }, SQL_TYPE_HINT_BOOL)
                    }
                )
            }

            val itemsToSkip = ArrayList<Int>(items.size)
            for (idx in entries.indices) {
                val entry = entries[idx]
                val entryToUpdateIdx = items.indexOfFirst { it.drive.ucloudId == entry.drive.ucloudId }
                if (entryToUpdateIdx != -1) {
                    itemsToSkip.add(entryToUpdateIdx)

                    if (allowUpsert) {
                        entries[idx] = entry.copy(system = items[entryToUpdateIdx].system)
                    }
                }
            }

            for ((index, item) in items.withIndex()) {
                if (index in itemsToSkip) continue
                entries.add(item)
            }
        }
    }

    suspend fun retrieve(id: Long): DriveAndSystem? {
        return mutex.withLock {
            entries.find { it.drive.ucloudId == id }
        }
    }

    suspend fun findByProperties(type: UCloudDrive.Type, localReference: String?, project: String?): DriveAndSystem? {
        return mutex.withLock {
            entries.find {
                it.drive.type == type &&
                        it.drive.localReference == localReference &&
                        it.drive.project == project
            }
        }
    }

    suspend fun findSystemByProject(project: String): Pair<FsSystem, Boolean>? {
        mutex.withLock {
            val entry = entries.find { it.drive.project == project }
            if (entry != null) {
                return Pair(entry.system, entry.inMaintenanceMode)
            } else {
                return null
            }
        }
    }

    suspend fun retrieveSystemsByProject(project: String): List<DriveAndSystem> {
        return mutex.withLock {
            entries.filter { it.drive.project == project }
        }
    }

    suspend fun enumerate(): List<DriveAndSystem> {
        return mutex.withLock {
            buildList { addAll(entries) }
        }
    }

    suspend fun updateMaintenanceStatus(systemIds: List<Long>, maintenanceMode: Boolean) {
        if (systemIds.isEmpty()) return

        dbConnection.withSession { session ->
            mutex.withLock {
                for (idx in entries.indices) {
                    val entry = entries[idx]
                    if (entry.drive.ucloudId in systemIds) {
                        entries[idx] = entry.copy(inMaintenanceMode = maintenanceMode)
                    }
                }

                session.prepareStatement(
                    """
                        update ucloud_storage_drives
                        set in_maintenance_mode = :maintenance_mode
                        where
                            collection_id = some(:system_ids::bigint[])
                    """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindList("system_ids", systemIds, SQL_TYPE_HINT_INT8)
                        bindBoolean("maintenance_mode", maintenanceMode)
                    }
                )
            }
        }
    }

    suspend fun updateSystem(systemIds: List<Long>, newSystem: FsSystem) {
        if (systemIds.isEmpty()) return
        dbConnection.withSession { session ->
            mutex.withLock {
                for (idx in entries.indices) {
                    val entry = entries[idx]
                    if (entry.drive.ucloudId in systemIds) {
                        entries[idx] = entry.copy(
                            system = newSystem
                        )
                    }
                }

                session.prepareStatement(
                    """
                        update ucloud_storage_drives
                        set
                            system = :new_system
                        where
                            collection_id = some(:system_ids::bigint[])
                    """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindList("system_ids", systemIds, SQL_TYPE_HINT_INT8)
                        bindString("new_system", newSystem.name)
                    }
                )
            }
        }
    }
}

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

    private val enteringMaintenanceModeListeners = ArrayList<suspend () -> Unit>()
    private val enteringMaintenanceModeListenersMutex = Mutex()

    // Maps providerId to ucloudId
    private val registeredDrives = HashMap<String, Long>()
    private val registeredDrivesMutex = Mutex()

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

    suspend fun onEnteringMaintenanceMode(listener: suspend () -> Unit) {
        enteringMaintenanceModeListenersMutex.withLock {
            enteringMaintenanceModeListeners.add(listener)
        }
    }

    suspend fun fillDriveDatabase() {
        DriveAndSystemStore.fill(serviceClient, legacySystem, allSystems, config.skipUCloudSynchronization)
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

        var (system, isAllowed) = when (drive) {
            is UCloudDrive.ProjectMemberFiles,
            is UCloudDrive.ProjectRepository -> {
                val systemName: String?
                var maintenanceMode: Boolean

                val systemAndMaintenance = DriveAndSystemStore.findSystemByProject(drive.project!!)
                systemName = systemAndMaintenance?.first?.name
                maintenanceMode = systemAndMaintenance?.second ?: false

                val system = if (systemName == null) {
                    maintenanceMode = false
                    defaultSystem
                } else {
                    allSystems.find { it.name == systemName }
                        ?: throw RPCException("Unknown system", HttpStatusCode.InternalServerError)
                }

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

        val id = if (initiatedByEndUser) {
            drive.ucloudId
        } else {
            registeredDrivesMutex.withLock {
                val cachedDriveId = registeredDrives[drive.toProviderId()]
                if (cachedDriveId == null) {
                    val providerId = drive.toProviderId()
                        ?: error("$drive was expected to have a provider ID but didn't")

                    val response = FileCollectionsControl.register.call(
                        bulkRequestOf(
                            ProviderRegisteredResource(
                                FileCollection.Spec(title, driveToProduct(drive)),
                                providerGeneratedId = providerId,
                                createdBy = createdByUser,
                                project = ownedByProject
                            )
                        ),
                        serviceClient
                    )

                    val freshId = if (response.statusCode == HttpStatusCode.Conflict) {
                        FileCollectionsControl.browse
                            .call(
                                ResourceBrowseRequest(
                                    FileCollectionIncludeFlags(
                                        MemberFilesFilter.DONT_FILTER_COLLECTIONS,
                                        filterProviderIds = providerId
                                    ),
                                ),
                                serviceClient
                            )
                            .orThrow()
                            .items
                            .firstOrNull()
                            ?.id
                            ?.toLongOrNull()
                            ?: throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
                    } else {
                        response.orThrow().responses.first().id.toLong()
                    }

                    registeredDrives[providerId] = freshId
                    freshId
                } else {
                    cachedDriveId
                }
            }
        }

        // NOTE(Dan): At this point we should know everything. We try to see if the drive already exists, because in
        // that case we want to just re-use the data. This function is not trying to create something new if it already
        // exists.
        val existingDrive = DriveAndSystemStore.retrieve(id)
        if (existingDrive != null) return existingDrive.normalize()

        DriveAndSystemStore.insert(
            listOf(DriveAndSystem(drive.withUCloudId(id), system, false, driveToInternalFile(system, drive))),
            allowUpsert = false
        )

        return DriveAndSystemStore.retrieve(id) ?: error("internal error - could not find drive after insertion")
    }

    suspend fun resolveDriveByProviderId(drive: UCloudDrive): DriveAndSystem {
        val resolvedId = FileCollectionsControl.browse.call(
            ResourceBrowseRequest(
                FileCollectionIncludeFlags(
                    MemberFilesFilter.DONT_FILTER_COLLECTIONS,
                    filterProviderIds = drive.toProviderId()
                ),
            ),
            serviceClient
        ).orThrow().items.firstOrNull()?.id?.toLongOrNull()
            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        return resolveDrive(resolvedId, allowMaintenanceMode = true)
    }

    suspend fun resolveDrive(
        driveId: Long,
        allowMaintenanceMode: Boolean = false,
    ): DriveAndSystem {
        val drive = DriveAndSystemStore.retrieve(driveId)
            ?: throw RPCException("Unknown drive: $driveId", HttpStatusCode.NotFound)

        if (!allowMaintenanceMode && drive.inMaintenanceMode) {
            throw RPCException(
                "This drive is currently in maintenance mode. Try again later.",
                HttpStatusCode.BadGateway,
                "MAINTENANCE"
            )
        }

        return drive.normalize()
    }

    suspend fun resolveDriveByInternalFile(path: InternalFile): DriveAndSystem {
        fun unknownFile(): Nothing =
            throw RPCException("Unrecognizable file at: ${path.path}", HttpStatusCode.InternalServerError)

        val system = systemsSortedByMountPrefix.find { path.normalize().path.startsWith(it.mountPath) } ?: unknownFile()

        val components = path.normalize().path.removePrefix(system.mountPath).split("/")
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

        return DriveAndSystemStore
            .findByProperties(type, localReference, project)
            ?.normalize()
            ?: unknownFile()
    }

    suspend fun enumerateDrives(
        filterType: UCloudDrive.Type? = null,
        next: String? = null
    ): PageV2<DriveAndSystem> {
        if (next != null) return PageV2(itemsPerPage = 0, items = emptyList(), next = null)

        val items = DriveAndSystemStore.enumerate().mapNotNull {
            if (filterType != null && it.drive.type != filterType) return@mapNotNull null
            it.normalize()
        }
        return PageV2(itemsPerPage = items.size, items = items, next = null)
    }

    suspend fun enableMaintenanceModeByInternalPath(path: InternalFile, description: String) {
        enableMaintenanceMode(resolveDriveByInternalFile(path).drive.ucloudId, description)
    }

    suspend fun disableMaintenanceModeByInternalPath(path: InternalFile) {
        disableMaintenanceMode(resolveDriveByInternalFile(path).drive.ucloudId)
    }

    suspend fun enableMaintenanceMode(driveId: Long, description: String) {
        setMaintenanceMode(driveId, description, true)
    }

    suspend fun disableMaintenanceMode(driveId: Long) {
        setMaintenanceMode(driveId, null, false)
    }

    fun systemByName(name: String): FsSystem? {
        return allSystems.find { it.name.equals(name, ignoreCase = true) }
    }

    private suspend fun setMaintenanceMode(driveId: Long, description: String?, maintenanceMode: Boolean) {
        require(!maintenanceMode || description != null)

        val driveAndSystem = resolveDrive(driveId, allowMaintenanceMode = true)
        if (driveAndSystem.inMaintenanceMode && maintenanceMode) {
            throw RPCException("This drive is already in maintenance mode!", HttpStatusCode.BadRequest)
        }

        val drive = driveAndSystem.drive

        val affectedDrives = when (drive) {
            is UCloudDrive.ProjectMemberFiles,
            is UCloudDrive.ProjectRepository -> {
                DriveAndSystemStore.retrieveSystemsByProject(drive.project!!)
            }

            else -> {
                listOf(driveAndSystem)
            }
        }

        DriveAndSystemStore.updateMaintenanceStatus(
            affectedDrives.map { it.drive.ucloudId },
            maintenanceMode
        )

        val timestamp = Time.now()
        FileCollectionsControl.update.call(
            BulkRequest(
                affectedDrives.map { (d) ->
                    ResourceUpdateAndId(
                        d.ucloudId.toString(),
                        FileCollection.Update(
                            timestamp,
                            if (maintenanceMode) {
                                "Maintenance is starting. $description"
                            } else {
                                "Maintenance has ended."
                            }
                        )
                    )
                }
            ),
            serviceClient
        ).orThrow()

        if (maintenanceMode) {
            enteringMaintenanceModeListenersMutex.withLock {
                for (listener in enteringMaintenanceModeListeners) {
                    try {
                        listener()
                    } catch (ex: Throwable) {
                        log.warn(
                            "Caught exception while notifying listeners about maintenance of $driveId:\n" +
                                    "${ex.toReadableStacktrace()}"
                        )
                    }
                }
            }
        }
    }

    suspend fun updateSystem(driveId: Long, newSystem: FsSystem) {
        val driveAndSystem = resolveDrive(driveId, allowMaintenanceMode = true)
        if (!driveAndSystem.inMaintenanceMode) {
            throw RPCException("This drive is not in maintenance mode!", HttpStatusCode.BadRequest)
        }

        val drive = driveAndSystem.drive

        val affectedDrives = when (drive) {
            is UCloudDrive.ProjectMemberFiles,
            is UCloudDrive.ProjectRepository -> {
                DriveAndSystemStore.retrieveSystemsByProject(drive.project!!)
            }

            else -> {
                listOf(driveAndSystem)
            }
        }

        DriveAndSystemStore.updateSystem(
            affectedDrives.map { it.drive.ucloudId },
            newSystem
        )

        setMaintenanceMode(driveId, null, false)
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

    private fun DriveAndSystem.normalize(): DriveAndSystem {
        return copy(driveRoot = driveToInternalFile(system, drive))
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

    companion object : Loggable {
        override val log = logger()
    }
}
