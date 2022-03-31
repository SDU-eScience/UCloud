package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.calls.* import dk.sdu.cloud.calls.client.AuthenticatedClient import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withFixedHost
import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.LocalSyncthingDevice
import dk.sdu.cloud.file.ucloud.api.SyncFolderBrowseItem
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.file.orchestrator.api.fileName
import dk.sdu.cloud.sync.mounter.api.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SyncService(
    private val providerId: String,
    private val syncthing: SyncthingClient,
    private val db: AsyncDBSessionFactory,
    private val authenticatedClient: AuthenticatedClient,
    private val cephStats: CephFsFastDirectoryStats,
    private val pathConverter: PathConverter,
    private val fs: NativeFS,
    private val baseMounterClient: AuthenticatedClient,
) {
    suspend fun addFolders(request: BulkRequest<SyncFolder>): BulkResponse<FindByStringId?> {
        class State(
            val request: SyncFolder,
            val internalFile: InternalFile,
            val file: NativeStat,
            val loadBalancedDevice: LocalSyncthingDevice,
        )
        val stateList = ArrayList<State>()

        // Verify feature whitelist
        if (!syncthing.config.userWhiteList.containsAll(request.items.map { it.owner.createdBy })) {
            log.debug(buildString {
                appendLine("Some users are not allowed to synchronize a folder!")
                appendLine("  Users requesting creation:")
                for (item in request.items) {
                    append("  - ")
                    appendLine(item.owner.createdBy)
                }
                appendLine("  Allowed users:")
                for (item in syncthing.config.userWhiteList) {
                    append("  - ")
                    appendLine(item)
                }
            })
            throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
        }

        // Verify files involved in request and select an appropriate target
        for (folder in request.items) {
            val internalFile = pathConverter.ucloudToInternal(UCloudFile.create(folder.specification.path))
            val fileName = folder.specification.path.fileName()
            val fileStats = try {
                fs.stat(internalFile)
            } catch (ex: FSException.NotFound) {
                throw RPCException(
                    "One of the files you requested no longer exists! " +
                        "Double-check if $fileName is still present.",
                    HttpStatusCode.BadRequest
                )
            } catch (ex: Throwable) {
                log.info("Unexpected exception thrown while creating a sync-folder: ${ex.stackTraceToString()}")
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }

            if (fileStats.fileType != FileType.DIRECTORY) {
                throw RPCException(
                    "You can only synchronize folders and '$fileName' does not appear to " +
                        "be one. Try refreshing or select a different folder!",
                    HttpStatusCode.BadRequest
                )
            }

            if (cephStats.getRecursiveFileCount(internalFile) > 1_000_000) {
                throw RPCException(
                    "The folder '$fileName' has too many files for synchronization. Try selecting a different folder!",
                    HttpStatusCode.BadRequest
                )
            }

            stateList.add(State(folder, internalFile, fileStats, loadBalanceToDevice()))
        }

        // Verify that all syncthing devices and mounters are ready
        val affectedDevices = stateList.asSequence().map { it.loadBalancedDevice }.toSet()
        for (device in affectedDevices) {
            verifyMounterIsReady(device)
        }

        // Perform mount
        val foldersByDevice = stateList.groupBy { it.loadBalancedDevice }
        for ((remoteDevice, state) in foldersByDevice) {
            Mounts.mount.call(
                MountRequest(state.map { MountFolder(it.request.id.toLong(), it.internalFile.path) }),
                mounterClient(remoteDevice),
            ).orRethrowAs {
                log.info("Failed to prepare folder for synchronization: $stateList")
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }
        }
        
        // Notify syncthing and insert record of our change
        try {
            db.withSession { session ->
                val affectedRows = session.sendPreparedStatement(
                    {
                        setParameter("ids", request.items.map { folder -> folder.id.toLong() })
                        setParameter("remote_devices", affectedDevices.map { it.id })
                        setParameter("paths", request.items.map { folder -> folder.specification.path })
                        setParameter("users", request.items.map { folder -> folder.owner.createdBy })
                        setParameter("type", request.items.map { folder ->
                            when (folder.status.permission) {
                                Permission.READ -> SynchronizationType.SEND_ONLY.name
                                Permission.EDIT -> SynchronizationType.SEND_RECEIVE.name
                                Permission.ADMIN -> SynchronizationType.SEND_RECEIVE.name
                                Permission.PROVIDER -> SynchronizationType.SEND_ONLY.name
                            }
                        })
                    },
                    """
                        insert into file_ucloud.sync_folders(id, device_id, path, user_id, sync_type)
                        values (unnest(:ids::bigint[]), unnest(:remote_devices::text[]), unnest(:paths::text[]), 
                                unnest(:users::text[]), unnest(:type::text[])) 
                        on conflict do nothing
                    """
                ).rowsAffected

                if (affectedRows > 0L) {
                    syncthing.addFolders(affectedDevices.toList(), session)
                    syncthing.addDevices(affectedDevices.toList(), session)
                }

                // NOTE(Dan): If we have a failure after syncthing has been notified and before the database
                // commits. Then we end up in the weird situation of Syncthing being aware of the folders + devices,
                // which means the end-user will see them, but they will disappear immediately after the next update
                // to syncthing. We assume that the end-user will simply add it again, since they most likely received
                // an error message in the frontend.
            }
        } catch (ex: Throwable) {
            // Result is ignored, since we are failing regardless
            for ((remoteDevice, state) in foldersByDevice) {
                runCatching { unmountFolders(remoteDevice, state.map { MountFolderId(it.request.id.toLong()) }) }
            }
            throw ex
        }

        // NOTE(Dan): Not throwing since this is a purely optional step and the user _will_ receive the share if the
        // previous steps were successful.
        SyncFolderControl.update.call(
            BulkRequest(
                stateList.map {
                    ResourceUpdateAndId(
                        it.request.id,
                        SyncFolder.Update(
                            Time.now(),
                            "Remote device has been attached! ${it.loadBalancedDevice.id}",
                            remoteDeviceId = it.loadBalancedDevice.id,
                            permission = it.request.status.permission
                        )
                    )
                }
            ),
            authenticatedClient
        )

        return BulkResponse(emptyList())
    }

    suspend fun removeFolders(ids: List<Long>) {
        val deleted = db.withSession { session ->
            val deleted = session.sendPreparedStatement(
                {
                    setParameter("ids", ids)
                },
                """
                    delete from file_ucloud.sync_folders
                    where id in (select unnest(:ids::bigint[]))
                    returning id, device_id
                """
            ).rows.mapNotNull {
                val id = it.getLong(0)!!
                val deviceId = it.getString(1)!!
                val device = syncthing.config.devices.find { it.id == deviceId } ?: return@mapNotNull null

                device to id
            }.groupBy(
                keySelector = { it.first },
                valueTransform = { it.second }
            )

            syncthing.removeFolders(deleted) // We keep this inside withSession to make it slightly more robust
            deleted
        }

        // It shouldn't matter significantly if unmounts fail at this point. Worst case we leak a few file descriptors.
        // Chances are, something is very wrong if these requests fail.
        for ((device, items) in deleted) {
            try {
                unmountFolders(device, items.map { MountFolderId(it) })
            } catch (ex: Throwable) {
                log.warn(buildString {
                    appendLine("Failed to unmount folders in response to a folder being removed in UCloud!")
                    appendLine("  Request: $ids")
                    appendLine("  Exception: ${ex.stackTraceToString()}")
                })
            }
        }
    }

    suspend fun browseFolders(
        device: String
    ): List<SyncFolderBrowseItem> {
        return db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("device", device) },
                """
                    select id, path, sync_type
                    from file_ucloud.sync_folders
                    where device_id = :device
                """
            ).rows.map { folder ->
                SyncFolderBrowseItem(
                    folder.getLong(0)!!,
                    pathConverter.ucloudToInternal(UCloudFile.create(folder.getString(1)!!)).path,
                    SynchronizationType.valueOf(folder.getString(2)!!)
                )
            }
        }
    }

    suspend fun addDevices(devices: BulkRequest<SyncDevice>): BulkResponse<FindByStringId?> {
        for (item in devices.items) {
            if (!item.specification.deviceId.matches(deviceIdRegex)) {
                throw RPCException("Invalid device ID: ${item.specification.deviceId}", HttpStatusCode.BadRequest)
            }
        }

        db.withSession { session ->
            val didInsert = session.sendPreparedStatement(
                {
                    devices.items.split {
                        into("ids") { it.id }
                        into("devices") { it.specification.deviceId }
                        into("users") { it.owner.createdBy }
                    }
                },
                """
                    insert into file_ucloud.sync_devices (id, device_id, user_id)
                    select unnest(:ids::bigint[]), unnest(:devices::text[]), unnest(:users::text[])
                    on conflict do nothing 
                """
            ).rowsAffected > 0L

            if (didInsert) {
                syncthing.addDevices(ctx = session)

                // NOTE(Dan): If the database fails to commit, then we accidentally leave a few devices in the 
                // Syncthing configuration. They should, however, disappear eventually, since there is no trace of them
                // in the actual database which is used for all configuration. In other words, we should recover from
                // this error automatically.
            }
        }

        return BulkResponse(emptyList())
    }

    suspend fun removeDevices(devices: BulkRequest<SyncDevice>) {
        db.withSession { session ->
            val deleted = session.sendPreparedStatement(
                {
                    setParameter("ids", devices.items.map { it.id })
                },
                """
                    delete from file_ucloud.sync_devices
                    where id in (select unnest(:ids::bigint[]))
                    returning device_id, user_id
                """
            ).rows.map { it.getString(0)!! }

            syncthing.removeDevices(deleted, session)

            // NOTE(Dan): If the database fails to commit, then we will accidentally remove devices prematurely. They
            // should automatically be added soon (whenever a new change is triggered on the same UCloud device).
        }
    }

    suspend fun updatePermissions(folders: BulkRequest<SyncFolderPermissionsUpdatedRequestItem>): BulkResponse<Unit?> {
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    folders.items.split {
                        into("ids") { it.resourceId.toLong() }
                        into("permissions") {
                            when (it.newPermission) {
                                Permission.READ -> SynchronizationType.SEND_ONLY.name
                                Permission.EDIT -> SynchronizationType.SEND_RECEIVE.name
                                Permission.ADMIN -> SynchronizationType.SEND_RECEIVE.name
                                Permission.PROVIDER -> SynchronizationType.SEND_ONLY.name
                            }
                        }
                    }
                },
                """
                    update file_ucloud.sync_folders f
                    set sync_type = updates.sync_type
                    from (
                        select
                            unnest(:ids::bigint[]) id, 
                            unnest(:permissions::text[]) sync_type
                    ) updates
                    where
                        f.id = updates.id
                """
            )

            if (folders.items.isNotEmpty()) syncthing.addFolders(ctx = session)

            // TODO, FIXME(Dan): It is not obvious to me if this will lead to a situation where permissions are never
            // updated.
        }

        SyncFolderControl.update.call(
            BulkRequest(folders.items.map {
                ResourceUpdateAndId(
                    it.resourceId,
                    SyncFolder.Update(
                        permission = it.newPermission
                    )
                )
            }),
            authenticatedClient
        )

        return BulkResponse(folders.items.map { })
    }

    suspend fun verifyFolders(folders: List<SyncFolder>) {
        return // NOTE(Dan): Not yet implemented
    }

    private suspend inline fun withRetries(attempts: Int = 3, block: () -> Unit) {
        for (i in 1..attempts) {
            @Suppress("TooGenericExceptionCaught")
            try {
                return block()
            } catch (ex: Throwable) {
                if (i == attempts) throw ex
                delay(500)
            }
        }
    }

    // TODO(Dan): Do we need to invoke this periodically as well? Just to ensure that we actually push configuration
    // soon after restart. Technically, it shouldn't be needed since Syncthing is storing the configuration in a 
    // persistent manner.
    private val configMutex = Mutex()
    private var lastSeenConfigurationIds = HashMap<String, String>()
    private suspend fun verifyMounterIsReady(device: LocalSyncthingDevice) {
        configMutex.withLock {
            val configId = lastSeenConfigurationIds[device.id] ?: null
            withRetries(attempts = 20) {
                val readyResponse = Mounts.ready.call(
                    ReadyRequest(configId), 
                    mounterClient(device)
                ).orThrow()

                if (!readyResponse.ready) {
                    if (readyResponse.requireConfigurationId != configId) {
                        syncthing.writeConfig(listOf(device))
                        syncthing.rescan(listOf(device))
                    }

                    throw RPCException(
                        "The synchronization feature is offline. Please try again later.",
                        HttpStatusCode.ServiceUnavailable
                    )
                }

                lastSeenConfigurationIds[device.id] = readyResponse.requireConfigurationId
            }
        }
    }

    private suspend fun unmountFolders(device: LocalSyncthingDevice, folders: List<MountFolderId>) {
        withRetries {
            Mounts.unmount.call(
                UnmountRequest(folders),
                mounterClient(device)
            ).orThrow()
        }
    }

    private val syncthingLoadBalancerCached = SimpleCache<Unit, LocalSyncthingDevice>(maxAge = 60_000 * 60) {
        db.withSession { session ->
            syncthing.config.devices.associateWith { device ->
                session.sendPreparedStatement(
                    {
                        setParameter("device", device.id)
                    },
                    """
                        select path
                        from file_ucloud.sync_folders
                        where device_id = :device
                    """
                ).rows.map { it.getString(0)!! }
            }.filter { it.value.size < 1000 }
                .minByOrNull { (_, folders) ->
                    folders.sumOf { folder ->
                        runCatching {
                            cephStats.getRecursiveSize(
                                pathConverter.ucloudToInternal(UCloudFile.create(folder))
                            )
                        }.getOrNull() ?: 0
                    }
                }?.key
        }
    }

    private suspend fun loadBalanceToDevice(): LocalSyncthingDevice {
        return syncthingLoadBalancerCached.get(Unit)
            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound, "Syncthing device not found")
    }

    private fun mounterClient(device: LocalSyncthingDevice): AuthenticatedClient {
        return if (device.doNotChangeHostNameForMounter) {
            baseMounterClient
        } else {
            baseMounterClient.withFixedHost(HostInfo(device.hostname, port = 8080))
        }
    }

    val syncProducts = listOf(
        SyncFolderSupport(ProductReference("u1-sync", "u1-sync", providerId)),
    )

    companion object : Loggable {
        override val log = logger()
        private val deviceIdRegex = Regex("""([a-zA-Z\d]{7}-){7}[a-zA-Z\d]{7}""")

    }
}

