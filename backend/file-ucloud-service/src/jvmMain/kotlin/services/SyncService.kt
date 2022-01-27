package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.LocalSyncthingDevice
import dk.sdu.cloud.file.ucloud.api.SyncFolderBrowseItem
import dk.sdu.cloud.file.ucloud.withMounterInfo
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.sync.mounter.api.*
import java.io.File

object SyncFoldersTable : SQLTable("sync_folders") {
    val id = long("id", notNull = true)
    val device = varchar("device_id", 64, notNull = true)
    val path = text("path", notNull = true)
    val syncType = varchar("sync_type", 20, notNull = true)
    val user = text("user_id", notNull = true)
}

object SyncDevicesTable : SQLTable("sync_devices") {
    val id = long("id", notNull = true)
    val device = varchar("device_id", 64, notNull = true)
    val user = text("user_id", notNull = true)
}


class SyncService(
    private val syncthing: SyncthingClient,
    private val db: AsyncDBSessionFactory,
    val authenticatedClient: AuthenticatedClient,
    private val cephStats: CephFsFastDirectoryStats,
    private val pathConverter: PathConverter
) {
    private val folderDeviceCache = SimpleCache<Unit, LocalSyncthingDevice> {
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
                ).rows.map { it.getField(SyncFoldersTable.path) }
            }.filter { it.value.size < 1000 }
             .minByOrNull { (_, folders) ->
                folders.sumOf { folder ->
                    cephStats.getRecursiveSize(
                        pathConverter.ucloudToInternal(UCloudFile.create(folder))
                    ) ?: 0
                }
            }?.key
        }
    }

    private suspend fun chooseFolderDevice(session: AsyncDBConnection): LocalSyncthingDevice {
        return folderDeviceCache.get(Unit)
            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound, "Syncthing device not found")
    }

    suspend fun addFolders(request: BulkRequest<SyncFolder>) : BulkResponse<FindByStringId?> {
        val affectedDevices: MutableSet<LocalSyncthingDevice> = mutableSetOf()
        val devices: MutableList<LocalSyncthingDevice> = mutableListOf()

        val affectedRows: Long = db.withSession { session ->
            request.items.forEach { folder ->
                val internalFile = pathConverter.ucloudToInternal(UCloudFile.create(folder.specification.path))

                if (!File(internalFile.path).exists() || !File(internalFile.path).isDirectory) {
                    throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
                }

                if (cephStats.getRecursiveFileCount(internalFile) > 1_000_000) {
                    throw RPCException(
                        "Number of files in directory exceeded for synchronization",
                        HttpStatusCode.Forbidden
                    )
                }

                val device = chooseFolderDevice(session)
                affectedDevices.add(device)

                SyncFolderControl.update.call(
                    bulkRequestOf(
                        ResourceUpdateAndId(
                            folder.id,
                            SyncFolder.Update(
                                Time.now(),
                                "Updated synchronized folder",
                                deviceId = device.id,
                                syncType = folder.status.syncType)
                        )
                    ),
                    authenticatedClient
                )

                Mounts.mount.call(
                    MountRequest(
                        listOf(MountFolder(folder.id.toLong(), internalFile.path))
                    ),
                    authenticatedClient //.withMounterInfo(device)
                ).orRethrowAs {
                    throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Failed to prepare folder for synchronization")
                }

                devices.add(device)
            }

            val affectedRows = session.sendPreparedStatement(
                {
                    setParameter("ids", request.items.map { folder -> folder.id.toLong() })
                    setParameter("devices", devices.map { it.id })
                    setParameter("paths", request.items.map { folder -> folder.specification.path })
                    setParameter("users", request.items.map { folder -> folder.owner.createdBy })
                    setParameter("type", request.items.map { folder -> folder.status.syncType})
                },
                """
                    insert into file_ucloud.sync_folders(
                        id, 
                        device_id, 
                        path,
                        user_id,
                        sync_type
                    ) values (
                        unnest(:ids::bigint[]),
                        unnest(:devices::text[]),
                        unnest(:paths::text[]),
                        unnest(:users::text[]),
                        unnest(:type::text[])
                    ) on conflict do nothing
            """
            ).rowsAffected

            affectedDevices.forEach { device ->

                // Mounter is ready when all folders added to syncthing on that device is mounted.
                // Syncthing is ready when it's accessible.

                var retries = 0
                while (true) {
                    if (retries == 3) {
                        throw RPCException(
                            "The synchronization feature is offline. Please try again later.",
                            HttpStatusCode.ServiceUnavailable
                        )
                    }

                    try {
                        val mounter = Mounts.ready.call(Unit, authenticatedClient) //.withMounterInfo(device))
                        val syncthingReady = syncthing.isReady(device)

                        if (
                            mounter.statusCode != HttpStatusCode.OK ||
                            !mounter.orThrow().ready ||
                            syncthingReady == null ||
                            !syncthingReady
                        ) {
                            retries++
                        } else {
                            break
                        }
                    } catch (ex: Throwable) {
                        retries++
                    }
                }
            }

            affectedRows
        }

        if (affectedRows > 0) {
            try {
                syncthing.writeConfig(affectedDevices.toList())
            } catch (ex: Throwable) {
                request.items.forEach { folder ->
                    Mounts.unmount.call(
                        UnmountRequest(
                            listOf(MountFolderId(folder.id.toLong()))
                        ),
                        authenticatedClient //.withMounterInfo(device)
                    )
                }

                db.withSession { session ->
                    session.sendPreparedStatement(
                        {
                            setParameter("ids", request.items.map { folder -> folder.id.toLong() })
                        },
                        """
                            delete from file_ucloud.sync_folders
                            where id in (select unnest(:ids::bigint[]))
                        """
                    )
                }
            }
        }

        return BulkResponse(emptyList())
    }

    suspend fun removeFolders(ids: List<Long>) {
        data class DeletedFolder(
            val id: Long,
            val path: String,
            val localDevice: LocalSyncthingDevice,
            val syncType: SynchronizationType,
            val userId: String
        )

        val deleted = db.withSession { session ->
            val deleted = session.sendPreparedStatement(
                {
                    setParameter("ids", ids)
                },
                """
                    delete from file_ucloud.sync_folders
                    where id in (select unnest(:ids::bigint[]))
                    returning id, device_id, path, sync_type, user_id 
                """
            ).rows.mapNotNull {
                val id = it.getField(SyncFoldersTable.id)
                val deviceId = it.getField(SyncFoldersTable.device)
                val path = it.getField(SyncFoldersTable.path)
                val syncType = it.getField(SyncFoldersTable.syncType)
                val userId = it.getField(SyncFoldersTable.user)

                val device = syncthing.config.devices.find { it.id == deviceId }
                if (device != null) {
                    DeletedFolder(id, path, device, SynchronizationType.valueOf(syncType), userId)
                } else {
                    null
                }
            }

            deleted.groupBy { it.localDevice }.forEach { deviceFolders ->
                Mounts.unmount.call(
                    UnmountRequest(
                        deviceFolders.value.map { MountFolderId(it.id) }
                    ),
                    authenticatedClient//.withMounterInfo(requests[0].second)
                ).orRethrowAs {
                    throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
                }
            }

            deleted
        }

        try {
            syncthing.writeConfig(deleted.map { it.localDevice })
        } catch (ex: Throwable) {
            deleted.groupBy { it.localDevice }.forEach { deviceFolders ->
                Mounts.mount.call(
                    MountRequest(
                        deviceFolders.value.map {
                            val internalFile = pathConverter.ucloudToInternal(UCloudFile.create(it.path))
                            MountFolder(it.id, internalFile.path)
                        }
                    ),
                    authenticatedClient //.withMounterInfo(device)
                )
            }

            db.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("ids", deleted.map { it.id })
                        setParameter("devices", deleted.map { it.localDevice.id })
                        setParameter("paths", deleted.map { it.path })
                        setParameter("users", deleted.map { it.userId })
                        setParameter("types", deleted.map { it.syncType.name })
                    },
                    """
                        insert into file_ucloud.sync_folders(
                            id, 
                            device_id, 
                            path,
                            user_id,
                            sync_type
                        ) values (
                            unnest(:ids::bigint[]),
                            unnest(:devices::text[]),
                            unnest(:paths::text[]),
                            unnest(:users::text[]),
                            unnest(:types::text[])
                        ) on conflict do nothing
                    """
                )
            }
        }
    }

    suspend fun browseFolders(
        device: String
    ): List<SyncFolderBrowseItem> {
        return db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("device", device)
                },
                """
                    select id, path, sync_type
                    from file_ucloud.sync_folders
                    where device_id = :device
                """
            ).rows.map { folder ->
                SyncFolderBrowseItem(
                    folder.getField(SyncFoldersTable.id),
                    folder.getField(SyncFoldersTable.path),
                    SynchronizationType.valueOf(folder.getField(SyncFoldersTable.syncType))
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

        val affectedRows = db.withSession { session ->
            devices.items.sumOf { device ->
                if (syncthing.config.devices.any { it.id == device.id }) {
                    throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                }

                session.sendPreparedStatement(
                    {
                        setParameter("id", device.id)
                        setParameter("device", device.specification.deviceId)
                        setParameter("user", device.owner.createdBy)
                    },
                    """
                        insert into file_ucloud.sync_devices(
                            id,
                            device_id,
                            user_id
                        ) values (
                            :id,
                            :device,
                            :user
                        )
                    """
                ).rowsAffected
            }
        }

        if (affectedRows > 0) {
            try {
                syncthing.writeConfig()
            } catch (ex: Throwable) {
                db.withSession { session ->
                    session.sendPreparedStatement(
                        {
                            setParameter("ids", devices.items.map { it.id })
                        },
                        """
                            delete from file_ucloud.sync_devices
                            where id in (select unnest(:ids::bigint[]))
                        """
                    )
                }
                throw RPCException("Invalid device ID", HttpStatusCode.BadRequest)
            }
        }

        return BulkResponse(emptyList())
    }

    suspend fun removeDevices(devices: BulkRequest<SyncDevice>) {
        data class DeletedDevice(
            val id: Long,
            val deviceId: String,
            val userId: String
        )

        val deleted = db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("ids", devices.items.map { it.id })
                },
                """
                    delete from file_ucloud.sync_devices
                    where id in (select unnest(:ids::bigint[]))
                    returning id, device_id, user_id
                """
            ).rows.mapNotNull {
                DeletedDevice(
                    it.getField(SyncDevicesTable.id),
                    it.getField(SyncDevicesTable.device),
                    it.getField(SyncDevicesTable.user)
                )
            }
        }

        if (deleted.isNotEmpty()) {
            try {
                syncthing.writeConfig()
            } catch (ex: Throwable) {
                db.withSession { session ->
                    session.sendPreparedStatement(
                        {
                            setParameter("ids", deleted.map { it.id })
                            setParameter("deviceIds", deleted.map { it.deviceId })
                            setParameter("userIds", deleted.map { it.userId })
                        },
                        """
                            insert into file_ucloud.sync_devices(
                                id,
                                device_id,
                                user_id
                            ) values (
                                unnest(:ids::bigint[]),
                                unnest(:deviceIds::text[]),
                                unnest(:userIds::text[])
                            )
                        """
                    )
                }
            }
        }
    }

    suspend fun updatePermissions(folders: BulkRequest<SyncFolder>): BulkResponse<Unit?> {
        db.withSession { session ->
            if (folders.items.any { it.status.syncType == null }) {
                val devices = session.sendPreparedStatement(
                    {
                        setParameter("ids", folders.items.filter { it.status.syncType == null }.map { it.id.toLong() })
                    },
                    """
                    delete from file_ucloud.sync_folders
                    where id in (select unnest(:ids::bigint[]))
                    returning id, device_id
                """
                ).rows.mapNotNull {
                    val id = it.getLong(0)!!
                    val deviceId = it.getString(1)!!
                    val device = syncthing.config.devices.find { it.id == deviceId }
                    if (device != null) {
                        id to device
                    } else {
                        null
                    }
                }

                devices.groupBy { it.second.id }.forEach { (_, requests) ->
                    Mounts.unmount.call(
                        UnmountRequest(
                            requests.map { MountFolderId(it.first) }
                        ),
                        authenticatedClient//.withMounterInfo(requests[0].second)
                    ).orRethrowAs {
                        throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
                    }
                }
            }

            if (folders.items.any { it.status.syncType != null }) {
                session.sendPreparedStatement(
                    {
                        setParameter("ids", folders.items.filter { it.status.syncType != null }.map { it.id })
                        setParameter(
                            "new_sync_types",
                            folders.items.filter { it.status.syncType != null }.map { it.status.syncType })
                    },
                    """
                        update file_ucloud.sync_folders f set
                            sync_type = updates.sync_type
                        from (select unnest(:ids::bigint[]), unnest(:new_sync_types::text[])) updates(id, sync_type)
                        where f.id = updates.id
                    """
                )
            }
        }

        if (folders.items.isNotEmpty()) {
            syncthing.writeConfig()
        }
        return BulkResponse(emptyList())
    }

    companion object {
        // NOTE(Dan): This is truly a beautiful regex. I refuse to write something better (unless someone has a good
        // reason).
        private val deviceIdRegex = Regex("""[\w\d][\w\d][\w\d][\w\d][\w\d][\w\d][\w\d]-[\w\d][\w\d][\w\d][\w\d][\w\d][\w\d][\w\d]-[\w\d][\w\d][\w\d][\w\d][\w\d][\w\d][\w\d]-[\w\d][\w\d][\w\d][\w\d][\w\d][\w\d][\w\d]-[\w\d][\w\d][\w\d][\w\d][\w\d][\w\d][\w\d]-[\w\d][\w\d][\w\d][\w\d][\w\d][\w\d][\w\d]-[\w\d][\w\d][\w\d][\w\d][\w\d][\w\d][\w\d]-[\w\d][\w\d][\w\d][\w\d][\w\d][\w\d][\w\d]""")
    }
}

val syncProducts = listOf(
    SyncFolderSupport(ProductReference("u1-sync", "u1-sync", UCLOUD_PROVIDER)),
)
