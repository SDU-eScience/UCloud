package dk.sdu.cloud.file.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.file.LocalSyncthingDevice
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.synchronization.services.SyncthingClient
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.sync.mounter.api.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.content.*
import io.ktor.http.*
import java.io.File
import java.net.http.HttpResponse
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
        val affectedRows: Long = db.withSession { session ->
            request.items.sumOf { folder ->
                val internalFile = File(fsPath, folder.path)
                if (!internalFile.exists() || !internalFile.isDirectory) {
                    throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
                }

                val accessType = if (aclService.hasPermission(folder.path, actor.username, AccessRight.WRITE)) {
                    SynchronizationType.SEND_RECEIVE
                } else if (aclService.hasPermission(folder.path, actor.username, AccessRight.READ)) {
                    SynchronizationType.SEND_ONLY
                } else {
                    throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
                }

                if (CephFsFastDirectoryStats.getRecursiveFileCount(internalFile) > 1_000_000) {
                    throw RPCException(
                        "Number of files in directory exceeded for synchronization",
                        HttpStatusCode.Forbidden
                    )
                }

                val id = UUID.randomUUID().toString()
                val device = chooseFolderDevice(session)

                Mounts.mount.call(
                    MountRequest(
                        listOf(MountFolder(id, folder.path))
                    ),
                    authenticatedClient
                ).orRethrowAs {
                    throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Failed to prepare folder for synchronization")
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
                ).rowsAffected
            }
        }

        if (affectedRows > 0) {
            syncthing.writeConfig()
        }
    }

    suspend fun removeFolder(actor: Actor, request: SynchronizationRemoveFolderRequest) {
        val affectedRows = db.withSession { session ->
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

        Mounts.unmount.call(
            UnmountRequest(
                request.items.map { MountFolderId(it.id) }
            ),
            authenticatedClient
        ).orRethrowAs {
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
        }

        if (affectedRows > 0) {
            syncthing.writeConfig()
        }
    }

    suspend fun browseFolders(
        principal: SecurityPrincipal,
        request: SynchronizationBrowseFoldersRequest
    ): List<SynchronizedFolderBrowseItem> {
        if (principal.role != Role.SERVICE) {
            throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
        }

        return db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("device", request.device)
                },
                """
                        select id, path
                        from storage.synchronized_folders
                        where device_id = :device
                    """
            ).rows.map { folder ->
                SynchronizedFolderBrowseItem(
                    folder.getField(SynchronizedFoldersTable.id),
                    folder.getField(SynchronizedFoldersTable.path)
                )
            }
        }
    }

    suspend fun addDevice(actor: Actor, request: SynchronizationAddDeviceRequest) {
        request.items.forEach { item ->
            if (syncthing.config.devices.any { it.id == item.id }) {
                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
            }
        }

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
