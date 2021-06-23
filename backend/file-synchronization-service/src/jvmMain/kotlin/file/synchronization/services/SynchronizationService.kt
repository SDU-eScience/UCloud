package dk.sdu.cloud.file.synchronization.services

import com.github.jasync.sql.db.util.length
import dk.sdu.cloud.Actor
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.synchronization.LocalSyncthingDevice
import dk.sdu.cloud.file.synchronization.api.*
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*
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
) {
    private fun chooseFolderDevice(): LocalSyncthingDevice? {
        // NOTE(Brian): Chooses a random device. In the storage-implementation, the device with the least amount of
        // storage attached (size-wise) will be used.

        val deviceIndex = (0..syncthing.config.devices.length).shuffled().first()
        return syncthing.config.devices[deviceIndex]
    }

    suspend fun addFolder(actor: Actor, request: SynchronizationAddFolderRequest) {
        // TODO Check for number of files in folder before adding (limit to 1000_000)

        val id = UUID.randomUUID().toString()
        val device = chooseFolderDevice()
        val accessType = "SEND_RECEIVE"

        if (device == null) {
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
        }

        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("id", id)
                    setParameter("device", device.id)
                    setParameter("path", request.path)
                    setParameter("user", actor.username)
                    setParameter("access", accessType)
                },
                """
                    insert into file_ucloud.synchronized_folders(
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

    suspend fun removeFolder(actor: Actor, request: SynchronizationRemoveFolderRequest) {
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("id", request.id)
                    setParameter("user", actor.username)
                },
                """
                    delete from file_ucloud.synchronized_folders
                    where id = :id and user_id = :user
                """
            )
        }

        syncthing.writeConfig()
    }

    suspend fun addDevice(actor: Actor, request: SynchronizationAddDeviceRequest) {
        if (syncthing.config.devices.find { it.id == request.id } != null) {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
        }

        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("device", request.id)
                    setParameter("user", actor.username)
                },
                """
                    insert into file_ucloud.user_devices(
                        device_id,
                        user_id
                    ) values (
                        :device,
                        :user
                    )
                """
            )
        }

        syncthing.writeConfig()
    }

    suspend fun removeDevice(actor: Actor, request: SynchronizationRemoveDeviceRequest) {
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("id", request.id)
                    setParameter("user", actor.username)
                },
                """
                    delete from file_ucloud.user_devices
                    where device_id = :id and user_id = :user
                """
            )
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
                        from file_ucloud.user_devices
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
                        from file_ucloud.synchronized_folders
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
