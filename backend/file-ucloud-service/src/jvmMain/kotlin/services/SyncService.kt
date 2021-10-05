package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
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
import io.ktor.http.*
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
    private val authenticatedClient: AuthenticatedClient,
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
                            SyncFolder.Update(Time.now(), "", deviceId = device.id)
                        )
                    ),
                    authenticatedClient
                )

                Mounts.mount.call(
                    MountRequest(
                        listOf(MountFolder(folder.id.toLong(), folder.specification.path))
                    ),
                    authenticatedClient //.withMounterInfo(device)
                ).orRethrowAs {
                    throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Failed to prepare folder for synchronization")
                }

                devices.add(device)
            }

            println("Inserting")
            session.sendPreparedStatement(
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
                    )
            """
            ).rowsAffected
        }

        affectedDevices.forEach { device ->
            val mounter = Mounts.ready.call(Unit, authenticatedClient) //.withMounterInfo(device))

            // Mounter is ready when all folders added to syncthing on that device is mounted.
            // Syncthing is ready when it's accessible.
            if (mounter.statusCode != HttpStatusCode.OK || !mounter.orThrow().ready || !syncthing.isReady(device)) {
                throw RPCException(
                    "The synchronization feature is offline at the moment. Your folders will be synchronized when it returns online.",
                    HttpStatusCode.ServiceUnavailable
                )
            }
        }

        if (affectedRows > 0) {
            syncthing.writeConfig(affectedDevices.toList())
        }

        return BulkResponse(emptyList())
    }

    suspend fun removeFolders(request: BulkRequest<SyncFolder>) {
        db.withSession { session ->
            val devices = session.sendPreparedStatement(
                {
                    setParameter("ids", request.items.map { it.id.toLong() })
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

            val grouped = devices.groupBy { it.second.id }
            grouped.forEach { (_, requests) ->
                Mounts.unmount.call(
                    UnmountRequest(
                        requests.map { MountFolderId(it.first) }
                    ),
                    authenticatedClient//.withMounterInfo(requests[0].second)
                ).orRethrowAs {
                    throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
                }
            }

            devices.size
        }

        syncthing.writeConfig()
    }

    internal suspend fun removeSubfolders(path: String) {
        val affectedRows = db.withSession { session ->
            val devices = session.sendPreparedStatement(
                {
                    setParameter("path", path)
                },
                """
                    delete from file_ucloud.sync_folders
                    where path like :path || '/%' or path = :path
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

            val grouped = devices.groupBy { it.second.id }
            grouped.forEach { (_, requests) ->
                Mounts.unmount.call(
                    UnmountRequest(
                        requests.map { MountFolderId(it.first) }
                    ),
                    authenticatedClient.withMounterInfo(requests[0].second)
                ).orRethrowAs {
                    throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
                }
            }

            devices.size
        }

        if (affectedRows > 0) {
            syncthing.writeConfig()
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
            syncthing.writeConfig()
        }

        return BulkResponse(emptyList())
    }

    suspend fun removeDevices(devices: BulkRequest<SyncDevice>) {
        val affectedRows = db.withSession { session ->
            devices.items.sumOf { device ->
                session.sendPreparedStatement(
                    {
                        setParameter("id", device.id)
                    },
                    """
                        delete from file_ucloud.sync_devices
                        where id = :id
                    """
                ).rowsAffected
            }
        }

        if (affectedRows > 0) {
            syncthing.writeConfig()
        }
    }
}

val syncProducts = listOf(
    SyncFolderSupport(ProductReference("u1-sync", "u1-sync", UCLOUD_PROVIDER)),
)
