package dk.sdu.cloud.file.services

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.LocalSyncthingDevice
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.synchronization.services.SyncthingClient
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.sync.mounter.api.*
import io.ktor.http.*
import java.io.File
import java.util.*

object SynchronizedFoldersTable : SQLTable("synchronized_folders") {
    val id = varchar("id", 36, notNull = true)
    val device = varchar("device_id", 64, notNull = true)
    val path = text("path", notNull = true)
    val accessType = varchar("access_type", 20, notNull = true)
    val user = text("user_id", notNull = true)
}

object UserDevicesTable : SQLTable("user_devices") {
    val device = varchar("device_id", 64, notNull = true)
    val user = text("user_id", notNull = true)
}

class SynchronizationService(
    private val syncthing: SyncthingClient,
    private val fsPath: String,
    private val db: AsyncDBSessionFactory,
    private val aclService: AclService,
    private val authenticatedClient: AuthenticatedClient
) {
    private val folderDeviceCache = SimpleCache<Unit, LocalSyncthingDevice> {
        db.withSession { session ->
            syncthing.config.devices.minByOrNull { device ->
                session.sendPreparedStatement(
                    {
                        setParameter("device", device.id)
                    },
                    """
                        select path
                        from storage.synchronized_folders
                        where device_id = :device
                    """
                ).rows.sumOf {
                    CephFsFastDirectoryStats.getRecursiveSize(File(fsPath, it.getField(SynchronizedFoldersTable.path)))
                }
            }
        }
    }

    private suspend fun chooseFolderDevice(session: AsyncDBConnection): LocalSyncthingDevice {
        return folderDeviceCache.get(Unit)
            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound, "Syncthing device not found")
    }

    suspend fun addFolder(actor: Actor, request: SynchronizationAddFolderRequest) {
        val affectedDevices: MutableSet<LocalSyncthingDevice> = mutableSetOf()
        val ids: MutableList<String> = mutableListOf()
        val syncTypes: MutableList<SynchronizationType> = mutableListOf()
        val devices: MutableList<LocalSyncthingDevice> = mutableListOf()

        val affectedRows: Long = db.withSession { session ->
            request.items.forEach { folder ->
                val internalFile = File(fsPath, folder.path)
                if (!internalFile.exists() || !internalFile.isDirectory) {
                    throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
                }

                val syncType = if (aclService.hasPermission(folder.path, actor.username, AccessRight.WRITE)) {
                    SynchronizationType.SEND_RECEIVE
                } else if (aclService.hasPermission(folder.path, actor.username, AccessRight.READ)) {
                    SynchronizationType.SEND_ONLY
                } else {
                    throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                }

                if (CephFsFastDirectoryStats.getRecursiveFileCount(internalFile) > 1_000_000) {
                    throw RPCException(
                        "Number of files in directory exceeded for synchronization",
                        HttpStatusCode.Forbidden
                    )
                }

                val id = UUID.randomUUID().toString()
                val device = chooseFolderDevice(session)
                affectedDevices.add(device)

                Mounts.mount.call(
                    MountRequest(
                        listOf(MountFolder(id, folder.path))
                    ),
                    authenticatedClient
                ).orRethrowAs {
                    throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Failed to prepare folder for synchronization")
                }

                ids.add(id)
                devices.add(device)
                syncTypes.add(syncType)
            }

            session.sendPreparedStatement(
                {
                    setParameter("ids", ids)
                    setParameter("devices", devices.map { it.id })
                    setParameter("paths", request.items.map { folder -> folder.path.normalize() })
                    setParameter("users", ids.map { actor.username })
                    setParameter("access", syncTypes.map { it.name })
                },
                """
                    insert into storage.synchronized_folders(
                        id, 
                        device_id, 
                        path,
                        user_id,
                        access_type
                    ) values (
                        unnest(:ids::text[]),
                        unnest(:devices::text[]),
                        unnest(:paths::text[]),
                        unnest(:users::text[]),
                        unnest(:access::text[])
                    )
            """
            ).rowsAffected
        }

        affectedDevices.forEach { device ->
            val mounter = Mounts.ready.call(Unit, authenticatedClient)

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
    }

    suspend fun removeFolder(actor: Actor, request: SynchronizationRemoveFolderRequest) {
        val affectedRows = db.withSession { session ->
            Mounts.unmount.call(
                UnmountRequest(
                    request.items.map { MountFolderId(it.id) }
                ),
                authenticatedClient
            ).orRethrowAs {
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }

            session.sendPreparedStatement(
                {
                    setParameter("ids", request.items.map { it.id })
                    setParameter("user", actor.username)
                },
                """
                    delete from storage.synchronized_folders
                    where id in (select unnest(:ids::text[])) and user_id = :user
                """
            ).rowsAffected
        }

        if (affectedRows > 0) {
            syncthing.writeConfig()
        }
    }

    internal suspend fun removeSubfolders(path: String) {
        val affectedRows = db.withSession { session ->
            val idsToDelete = session.sendPreparedStatement(
                {
                    setParameter("path", path)
                },
                """
                select id from storage.synchronized_folders
                where path like :path || '/%' or path = :path
            """
            ).rows.map { it.getField(SynchronizedFoldersTable.id) }

            Mounts.unmount.call(
                UnmountRequest(
                    idsToDelete.map { MountFolderId(it) }
                ),
                authenticatedClient
            ).orRethrowAs {
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }

            session.sendPreparedStatement(
                {
                    setParameter("ids", idsToDelete)
                },
                """
                    delete from storage.synchronized_folders
                    where id in (select unnest(:ids::text[]))
                """
            ).rowsAffected
        }

        if (affectedRows > 0) {
            syncthing.writeConfig()
        }
    }

    suspend fun browseFolders(
        request: SynchronizationBrowseFoldersRequest
    ): List<SynchronizedFolderBrowseItem> {
        return db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("device", request.device)
                },
                """
                    select id, path, access_type
                    from storage.synchronized_folders
                    where device_id = :device
                """
            ).rows.map { folder ->
                SynchronizedFolderBrowseItem(
                    folder.getField(SynchronizedFoldersTable.id),
                    folder.getField(SynchronizedFoldersTable.path),
                    SynchronizationType.valueOf(folder.getField(SynchronizedFoldersTable.accessType))
                )
            }
        }
    }

    suspend fun addDevice(actor: Actor, request: SynchronizationAddDeviceRequest) {
        val affectedRows = db.withSession { session ->
            request.items.sumOf { item ->
                if (syncthing.config.devices.any { it.id == item.id }) {
                    throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                }

                session.sendPreparedStatement(
                    {
                        setParameter("device", item.id)
                        setParameter("user", actor.username)
                    },
                    """
                        insert into storage.user_devices(
                            device_id,
                            user_id
                        ) values (
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
    }

    suspend fun removeDevice(actor: Actor, request: SynchronizationRemoveDeviceRequest) {
        val affectedRows = db.withSession { session ->
            request.items.sumOf { item ->
                session.sendPreparedStatement(
                    {
                        setParameter("id", item.id)
                        setParameter("user", actor.username)
                    },
                    """
                        delete from storage.user_devices
                        where device_id = :id and user_id = :user
                    """
                ).rowsAffected
            }
        }

        if (affectedRows > 0) {
            syncthing.writeConfig()
        }
    }

    suspend fun browseDevices(actor: Actor, request: SynchronizationBrowseDevicesRequest): PageV2<SynchronizationDevice> {
        return db.paginateV2(
            actor,
            request.normalize(),
            create = { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("user", actor.username)
                    },
                    """
                        declare c cursor for
                        select device_id
                        from storage.user_devices
                        where user_id = :user
                        order by device_id
                    """
                )
            },
            mapper = { _, rows -> rows.map { SynchronizationDevice(it.getField(UserDevicesTable.device)) } }
       )
    }

    suspend fun retrieveFolder(actor: Actor, path: String): SynchronizedFolder {
        return db.withSession { session ->
            val folder = session.sendPreparedStatement(
                {
                    setParameter("user", actor.username)
                    setParameter("path", path)
                },
                """
                        select id, path, device_id
                        from storage.synchronized_folders
                        where user_id = :user and path = :path
                    """
            ).rows.singleOrNull()

            if (folder.isNullOrEmpty()) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }

            SynchronizedFolder(
                id = folder.getField(SynchronizedFoldersTable.id),
                path = path,
                device = folder.getField(SynchronizedFoldersTable.device)
            )
        }
    }


}
