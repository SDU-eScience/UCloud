package dk.sdu.cloud.file.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.LocalSyncthingDevice
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.synchronization.services.SyncthingClient
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.db.async.*
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
    private val db: DBContext,
    private val aclService: AclService
) {
    private val folderDeviceCache = SimpleCache<Unit, LocalSyncthingDevice> {
        println("Init device")
        db.withSession { session ->
            syncthing.config.devices.minByOrNull { device ->
                session.sendPreparedStatement(
                    {
                        setParameter("device", device.id)
                    },
                    """
                                select path
                                from synchronized_folders
                                where device_id = :device
                            """
                ).rows.sumOf {
                    CephFsFastDirectoryStats.getRecursiveSize(File(fsPath, it.getField(SynchronizedFoldersTable.path)))
                }
            }
        }
    }

    private suspend fun chooseFolderDevice(session: AsyncDBConnection): LocalSyncthingDevice {
        val resolvedDevice = folderDeviceCache.get(Unit)

        if (resolvedDevice != null) {
            println("Using cached device")
            return resolvedDevice
        } else {
            println("Lookup device")
            val deviceLookup = syncthing.config.devices.minByOrNull { device ->
                session.sendPreparedStatement(
                    {
                        setParameter("device", device.id)
                    },
                    """
                            select path
                            from synchronized_folders
                            where device_id = :device
                        """
                ).rows.sumOf {
                    CephFsFastDirectoryStats.getRecursiveSize(File(fsPath, it.getField(SynchronizedFoldersTable.path)))
                }
            }
            if (deviceLookup != null) {
                folderDeviceCache.insert(Unit, deviceLookup)
                return deviceLookup
            } else {
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "No internal device found")
            }
        }
    }

    suspend fun addFolder(actor: Actor, request: SynchronizationAddFolderRequest) {
        db.withSession { session ->
            request.items.forEach { folder ->
                if (CephFsFastDirectoryStats.getRecursiveFileCount(File(fsPath, folder.path)) > 1_000_000) {
                    throw RPCException(
                        "Number of files in directory exceeded for synchronization",
                        HttpStatusCode.Forbidden
                    )
                }

                val id = UUID.randomUUID().toString()
                val device = chooseFolderDevice(session)
                val accessType = if (aclService.hasPermission(folder.path, actor.username, AccessRight.WRITE)) {
                    SynchronizationType.SEND_RECEIVE
                } else {
                    SynchronizationType.SEND_ONLY
                }

                session.sendPreparedStatement(
                    {
                        setParameter("id", id)
                        setParameter("device", device.id)
                        setParameter("path", folder.path)
                        setParameter("user", actor.username)
                        setParameter("access", accessType.name)
                    },
                    """
                        insert into storage.synchronized_folders(
                            id, 
                            device_id, 
                            path,
                            user_id,
                            access_type
                        ) values (
                            :id,
                            :device,
                            :path,
                            :user,
                            :access
                        )
                    """
                )
            }

            syncthing.writeConfig()
        }
    }

    suspend fun removeFolder(actor: Actor, request: SynchronizationRemoveFolderRequest) {
        db.withSession { session ->
            request.items.forEach { item ->
                session.sendPreparedStatement(
                    {
                        setParameter("id", item.id)
                        setParameter("user", actor.username)
                    },
                    """
                        delete from storage.synchronized_folders
                        where id = :id and user_id = :user
                    """
                )
            }
        }

        syncthing.writeConfig()
    }

    suspend fun addDevice(actor: Actor, request: SynchronizationAddDeviceRequest) {
        db.withSession { session ->
            request.items.forEach { item ->
                if (syncthing.config.devices.find { it.id == item.id } != null) {
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
                )
            }
        }

        syncthing.writeConfig()
    }

    suspend fun removeDevice(actor: Actor, request: SynchronizationRemoveDeviceRequest) {
        db.withSession { session ->
            request.items.forEach { item ->
                session.sendPreparedStatement(
                    {
                        setParameter("id", item.id)
                        setParameter("user", actor.username)
                    },
                    """
                        delete from storage.user_devices
                        where device_id = :id and user_id = :user
                    """
                )
            }
        }

        syncthing.writeConfig()
    }

    suspend fun browseDevices(actor: Actor): PageV2<SynchronizationDevice> {
        val devices = db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("user", actor.username)
                },
                """
                        select device_id
                        from storage.user_devices
                        where user_id = :user
                        limit 100
                    """
            ).rows.map { SynchronizationDevice(it.getField(UserDevicesTable.device)) }
        }

        return PageV2(100, devices, null)
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
                        limit 100
                    """
            ).rows

            if (folder.isEmpty()) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }

            SynchronizedFolder(
                id = folder.first().getField(SynchronizedFoldersTable.id),
                path = path,
                device_id = folder.first().getField(SynchronizedFoldersTable.device)
            )
        }
    }
}
