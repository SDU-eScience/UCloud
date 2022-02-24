package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.LocalSyncthingDevice
import dk.sdu.cloud.file.ucloud.SyncConfiguration
import dk.sdu.cloud.service.DistributedLockFactory
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.net.ConnectException
import java.util.concurrent.atomic.AtomicLong

@Serializable
data class SyncthingIgnoredFolder(
    val id: String,
    val label: String,
    val time: String
)

@Serializable
data class SyncthingRemoteIgnoredDevices(
    val address: String,
    val deviceID: String,
    val name: String,
    val time: String
)

@Serializable
data class SyncthingDevice(
    val addresses: List<String> = listOf("dynamic"),
    val allowedNetworks: List<String> = emptyList(),
    val autoAcceptFolders: Boolean = false,
    val certName: String = "",
    val compression: String = "metadata",
    val deviceID: String = "",
    val ignoredFolders: List<SyncthingIgnoredFolder> = emptyList(),
    val introducedBy: String = "",
    val introducer: Boolean = false,
    val maxRecvKbps: Int = 0,
    val maxRequestKiB: Int = 0,
    val maxSendKbps: Int = 0,
    val name: String = "",
    val paused: Boolean = false,
    val remoteGUIPort: Int = 0,
    val skipIntroductionRemovals: Boolean = false,
    val untrusted: Boolean = false
)

@Serializable
data class SyncthingDefaults(
    val device: SyncthingDevice = SyncthingDevice(),
    val folder: SyncthingFolder = SyncthingFolder()
)

@Serializable
data class SyncthingGui(
    val address: String = "",
    val apiKey: String = "",
    val authMode: String = "static",
    val debugging: Boolean = false,
    val enabled: Boolean = true,
    val insecureAdminAccess: Boolean = false,
    val insecureAllowFrameLoading: Boolean = false,
    val insecureSkipHostcheck: Boolean = false,
    val password: String = "",
    val theme: String = "default",
    val unixSocketPermissions: String = "",
    val useTLS: Boolean = false,
    val user: String = ""
)

@Serializable
data class SyncthingLdap(
    val address: String = "",
    val bindDN: String = "",
    val insecureSkipVerify: Boolean = false,
    val searchBaseDN: String = "",
    val searchFilter: String = "",
    val transport: String = "plain"
)

@Serializable
data class SyncthingOptions(
    val alwaysLocalNets: List<String> = emptyList(),
    val announceLANAddresses: Boolean = true,
    val autoUpgradeIntervalH: Int = 12,
    val cacheIgnoredFiles: Boolean = false,
    val connectionLimitEnough: Int = 0,
    val connectionLimitMax: Int = 0,
    val crURL: String = "https://crash.syncthing.net/newcrash",
    val crashReportingEnabled: Boolean = false,
    val databaseTuning: String = "auto",
    val featureFlags: List<String> = emptyList(),
    val globalAnnounceEnabled: Boolean = true,
    val globalAnnounceServers: List<String> = listOf("default"),
    val keepTemporariesH: Int = 24,
    val limitBandwidthInLan: Boolean = false,
    val listenAddresses: List<String> = listOf("default"),
    val localAnnounceEnabled: Boolean = true,
    val localAnnounceMCAddr: String = "[ff12::8384]:21027",
    val localAnnouncePort: Int = 21027,
    val maxConcurrentIncomingRequestKiB: Int = 0,
    val maxFolderConcurrency: Int = 0,
    val maxRecvKbps: Int = 0,
    val maxSendKbps: Int = 0,
    val minHomeDiskFree: MinDiskFree = MinDiskFree(),
    val natEnabled: Boolean = true,
    val natLeaseMinutes: Int = 60,
    val natRenewalMinutes: Int = 30,
    val natTimeoutSeconds: Int = 10,
    val overwriteRemoteDeviceNamesOnConnect: Boolean = false,
    val progressUpdateIntervalS: Int = 5,
    val reconnectionIntervalS: Int = 60,
    val relayReconnectIntervalM: Int = 10,
    val relaysEnabled: Boolean = true,
    val releasesURL: String = "https://upgrades.syncthing.net/meta.json",
    val restartOnWakeup: Boolean = true,
    val sendFullIndexOnUpgrade: Boolean = false,
    val setLowPriority: Boolean = true,
    val startBrowser: Boolean = true,
    val stunKeepaliveMinS: Int = 20,
    val stunKeepaliveStartS: Int = 180,
    val stunServers: List<String> = listOf("default"),
    val tempIndexMinBlocks: Int = 10,
    val trafficClass: Int = 0,
    val unackedNotificationIDs: List<String> = emptyList(),
    val upgradeToPreReleases: Boolean = false,
    val urAccepted: Int = -1,
    val urInitialDelayS: Int = 1800,
    val urPostInsecurely: Boolean = false,
    val urSeen: Int = 3,
    val urURL: String = "https://data.syncthing.net/newdata",
    val urUniqueId: String = ""
)

@Serializable
data class SyncthingConfig(
    val defaults: SyncthingDefaults,
    val devices: List<SyncthingDevice> = emptyList(),
    val folders: List<SyncthingFolder> = emptyList(),
    val gui: SyncthingGui,
    val ldap: SyncthingLdap,
    val options: SyncthingOptions,
    val remoteIgnoredDevices: List<SyncthingRemoteIgnoredDevices> = emptyList(),
    val version: Int = 35
)

@Serializable
data class SyncthingFolder(
    val autoNormalize: Boolean = true,
    val blockPullOrder: String = "standard",
    val caseSensitiveFS: Boolean = false,
    val copiers: Int = 0,
    val copyOwnershipFromParent: Boolean = false,
    val copyRangeMethod: String = "standard",
    val devices: List<SyncthingFolderDevice> = emptyList(),
    val disableFsync: Boolean = false,
    val disableSparseFiles: Boolean = false,
    val disableTempIndexes: Boolean = false,
    val filesystemType: String = "basic",
    val fsWatcherDelayS: Int = 10,
    val fsWatcherEnabled: Boolean = true,
    val hashers: Int = 0,
    val id: String = "",
    val ignoreDelete: Boolean = false,
    val ignorePerms: Boolean = false,
    val junctionsAsDirs: Boolean = false,
    val label: String = "",
    val markerName: String = ".stfolder",
    val maxConcurrentWrites: Int = 2,
    val maxConflicts: Int = 10,
    val minDiskFree: MinDiskFree = MinDiskFree(),
    val modTimeWindowS: Int = 0,
    val order: String = "random",
    val path: String = "",
    val paused: Boolean = false,
    val pullerMaxPendingKiB: Int = 0,
    val pullerPauseS: Int = 0,
    val rescanIntervalS: Int = 3600,
    val scanProgressIntervalS: Int = 0,
    val type: String = SynchronizationType.SEND_RECEIVE.syncthingValue,
    val versioning: Versioning = Versioning(),
    val weakHashThresholdPct: Int = 25
)

@Serializable
data class MinDiskFree(
    val unit: String = "%",
    val value: Int = 1
)

@Serializable
data class Versioning(
    val cleanupIntervalS: Int = 3600,
    val fsPath: String = "",
    val fsType: String = "basic",
    val params: VersioningParams = Unit,
    val type: String = ""
)

typealias VersioningParams = Unit


@Serializable
data class SyncthingFolderDevice(
    val deviceID: String,
    val encryptionPassword: String = "",
    val introducedBy: String = ""
)

class SyncthingClient(
    val config: SyncConfiguration,
    val db: DBContext,
    val distributedLocks: DistributedLockFactory,
    val lastWrite: AtomicLong
) {
    private val httpClient = HttpClient(OkHttp) {
        expectSuccess = false

        engine {
            threadsCount = 1
            pipelining = false
        }
    }
    private val mutex = Mutex()
    private val lock = distributedLocks.create("syncthing-client-writer", duration = 5_000)

    private fun deviceEndpoint(device: LocalSyncthingDevice, path: String): String {
        return "http://${device.hostname}:${device.port}/${path.removePrefix("/")}"
    }

    suspend fun writeConfig(
        toDevices: List<LocalSyncthingDevice> = emptyList()
    ) {
        if (lock.acquire()) {
            var pendingDevices = toDevices.ifEmpty {
                config.devices
            }

            val timeout = Time.now() + 30_000

            log.debug("Writing config to Syncthing")

            // NOTE(Brian): We are waiting for changes to be written to all syncthing devices, sending requests every
            // 5 seconds, for 30 seconds. Syncthing will start rejecting all requests if we send too many in a row.
            while (pendingDevices.isNotEmpty()) {
                if (Time.now() > lastWrite.get() + 5_000) {
                    mutex.withLock {
                        val result = db.withSession { session ->
                            session.sendPreparedStatement(
                                {
                                    setParameter("devices", pendingDevices.map { it.id })
                                },
                                """
                                       select f.id, f.path, f.sync_type, f.device_id as local_device_id, d.device_id, d.user_id, f.user_id
                                       from
                                          file_ucloud.sync_folders f join
                                          file_ucloud.sync_devices d on f.user_id = d.user_id
                                       where
                                          f.device_id in (select unnest(:devices::text[]))
                                    """
                            )
                        }.rows

                        pendingDevices.forEach { device ->
                            val newConfig = SyncthingConfig(
                                devices = result
                                    .filter {
                                        it.getString("local_device_id") == device.id
                                    }
                                    .distinctBy { it.getField(SyncDevicesTable.device) }
                                    .map { row ->
                                        SyncthingDevice(
                                            deviceID = row.getField(SyncDevicesTable.device),
                                            name = row.getField(SyncDevicesTable.device)
                                        )
                                    } + listOf(SyncthingDevice(deviceID = device.id, name = device.name)),
                                folders = result
                                    .filter {
                                        it.getString("local_device_id") == device.id
                                    }
                                    .distinctBy { it.getField(SyncFoldersTable.id) }
                                    .map { row ->
                                        SyncthingFolder(
                                            id = row.getField(SyncFoldersTable.id).toString(),
                                            label = row.getField(SyncFoldersTable.path).substringAfterLast("/"),
                                            devices = result
                                                .filter {
                                                    it.getString("local_device_id") == device.id &&
                                                        it.getField(SyncFoldersTable.id) == row.getField(
                                                        SyncFoldersTable.id
                                                    ) &&
                                                        it.getField(SyncDevicesTable.user) == row.getField(
                                                        SyncFoldersTable.user
                                                    )
                                                }.map {
                                                    SyncthingFolderDevice(it.getField(SyncDevicesTable.device))
                                                },
                                            path = File(
                                                "/mnt/sync",
                                                row.getField(SyncFoldersTable.id).toString()
                                            ).absolutePath,
                                            type = SynchronizationType.valueOf(row.getField(SyncFoldersTable.syncType)).syncthingValue,
                                            rescanIntervalS = device.rescanIntervalSeconds
                                        )
                                    },
                                defaults = SyncthingDefaults(),
                                gui = SyncthingGui(
                                    address = device.hostname,
                                    apiKey = device.apiKey,
                                    user = device.username,
                                    password = device.password
                                ),
                                ldap = SyncthingLdap(),
                                options = SyncthingOptions()
                            )

                            try {
                                val resp = httpClient.put<HttpResponse>(deviceEndpoint(device, "/rest/config")) {
                                    body = TextContent(
                                        defaultMapper.encodeToString(newConfig),
                                        ContentType.Application.Json
                                    )
                                    headers {
                                        append("X-API-Key", device.apiKey)
                                    }
                                }

                                if (resp.status != HttpStatusCode.OK) {
                                    throw RPCException(
                                        resp.content.toByteArray().toString(Charsets.UTF_8),
                                        dk.sdu.cloud.calls.HttpStatusCode.BadRequest
                                    )
                                } else {
                                    pendingDevices = pendingDevices.filter { it.id != device.id }
                                }

                            } catch (ex: RPCException) {
                                throw RPCException("Invalid Syncthing Configuration", dk.sdu.cloud.calls.HttpStatusCode.BadRequest)
                            } catch (ex: Throwable) {
                                // Do nothing
                            }
                        }
                    }
                    lastWrite.set(Time.now())

                    if (Time.now() > timeout) {
                        throw RPCException(
                            "Unable to contact one or more syncthing clients",
                            dk.sdu.cloud.calls.HttpStatusCode.ServiceUnavailable
                        )
                    }
                }
            }
            lock.release()
        }
    }

    suspend fun drainConfig() {
        if (lock.acquire()) {
            mutex.withLock {
                config.devices.forEach { device ->
                    val newConfig = SyncthingConfig(
                        devices = emptyList(),
                        folders = emptyList(),
                        defaults = SyncthingDefaults(),
                        gui = SyncthingGui(
                            address = device.hostname,
                            apiKey = device.apiKey,
                            user = device.username,
                            password = device.password
                        ),
                        ldap = SyncthingLdap(),
                        options = SyncthingOptions()
                    )

                    try {
                        val resp = httpClient.put<HttpResponse>(deviceEndpoint(device, "/rest/config")) {
                            body = TextContent(
                                defaultMapper.encodeToString(newConfig),
                                ContentType.Application.Json
                            )
                            headers {
                                append("X-API-Key", device.apiKey)
                            }
                        }

                        if (resp.status != HttpStatusCode.OK) {
                            throw RPCException(
                                resp.content.toByteArray().toString(Charsets.UTF_8),
                                dk.sdu.cloud.calls.HttpStatusCode.BadRequest
                            )
                        }
                    } catch (e: ConnectException) {
                        e.printStackTrace()
                        throw RPCException(
                            "The synchronization feature is offline. Please try again later.",
                            dk.sdu.cloud.calls.HttpStatusCode.ServiceUnavailable,
                        )
                    } finally {
                        lock.release()
                    }
                }
            }
        }
    }

    suspend fun isReady(device: LocalSyncthingDevice): Boolean? {
        if (lock.acquire()) {
            mutex.withLock {
                return try {
                    val resp = httpClient.get<HttpResponse>(deviceEndpoint(device, "/rest/system/ping")) {
                        headers {
                            append("X-API-Key", device.apiKey)
                        }
                    }
                    resp.status == HttpStatusCode.OK
                } catch (ex: Throwable) {
                    false
                } finally {
                    lock.release()
                }
            }
        }
        return null
    }

    suspend fun rescan(devices: List<LocalSyncthingDevice> = emptyList()) {
        if (lock.acquire()) {
            mutex.withLock {
                val pendingDevices = devices.ifEmpty { config.devices }
                log.info("Attempting rescan of syncthing")
                pendingDevices.forEach { device ->
                    try {
                        httpClient.post<HttpResponse>(deviceEndpoint(device, "/rest/db/scan")) {
                            headers {
                                append("X-API-Key", device.apiKey)
                            }
                        }
                    } catch (ex: Throwable) {
                        // do nothing
                    }
                }
            }
            lock.release()
        }
    }

    suspend fun addFolders(toDevices: List<LocalSyncthingDevice> = emptyList()) {
        if (lock.acquire()) {
            var pendingDevices = toDevices.ifEmpty {
                config.devices
            }

            val timeout = Time.now() + 5_000

            log.debug("Adding folders to Syncthing")

            while (pendingDevices.isNotEmpty()) {
                if (Time.now() > lastWrite.get() + 1_000) {
                    mutex.withLock {
                        val result = db.withSession { session ->
                            session.sendPreparedStatement(
                                {
                                    setParameter("devices", pendingDevices.map { it.id })
                                },
                                """
                                       select f.id, f.path, f.sync_type, f.device_id as local_device_id, d.device_id, d.user_id, f.user_id
                                       from
                                          file_ucloud.sync_folders f join
                                          file_ucloud.sync_devices d on f.user_id = d.user_id
                                       where
                                          f.device_id in (select unnest(:devices::text[]))
                                    """
                            )
                        }.rows

                        pendingDevices.forEach { device ->
                            val newFolders = result
                                .filter {
                                    it.getString("local_device_id") == device.id
                                }
                                .distinctBy { it.getField(SyncFoldersTable.id) }
                                .map { row ->
                                    SyncthingFolder(
                                        id = row.getField(SyncFoldersTable.id).toString(),
                                        label = row.getField(SyncFoldersTable.path).substringAfterLast("/"),
                                        devices = result
                                            .filter {
                                                it.getString("local_device_id") == device.id &&
                                                    it.getField(SyncFoldersTable.id) == row.getField(
                                                    SyncFoldersTable.id
                                                ) &&
                                                    it.getField(SyncDevicesTable.user) == row.getField(
                                                    SyncFoldersTable.user
                                                )
                                            }.map {
                                                SyncthingFolderDevice(it.getField(SyncDevicesTable.device))
                                            },
                                        path = File(
                                            "/mnt/sync",
                                            row.getField(SyncFoldersTable.id).toString()
                                        ).absolutePath,
                                        type = SynchronizationType.valueOf(row.getField(SyncFoldersTable.syncType)).syncthingValue,
                                        rescanIntervalS = device.rescanIntervalSeconds
                                    )
                                }

                            try {
                                val resp = httpClient.put<HttpResponse>(deviceEndpoint(device, "/rest/config/folders")) {
                                    body = TextContent(
                                        defaultMapper.encodeToString(newFolders),
                                        ContentType.Application.Json
                                    )
                                    headers {
                                        append("X-API-Key", device.apiKey)
                                    }
                                }

                                if (resp.status != HttpStatusCode.OK) {
                                    throw RPCException(
                                        resp.content.toByteArray().toString(Charsets.UTF_8),
                                        dk.sdu.cloud.calls.HttpStatusCode.BadRequest
                                    )
                                } else {
                                    pendingDevices = pendingDevices.filter { it.id != device.id }
                                }

                            } catch (ex: RPCException) {
                                throw RPCException("Invalid Syncthing Configuration", dk.sdu.cloud.calls.HttpStatusCode.BadRequest)
                            } catch (ex: Throwable) {
                                // Do nothing
                            }
                        }
                    }
                    lastWrite.set(Time.now())

                    if (Time.now() > timeout) {
                        throw RPCException(
                            "Unable to contact one or more syncthing clients",
                            dk.sdu.cloud.calls.HttpStatusCode.ServiceUnavailable
                        )
                    }
                }
            }
            lock.release()
        }
    }

    suspend fun removeFolders(folders: Map<LocalSyncthingDevice, List<Long>>) {
        if (lock.acquire()) {
            log.debug("Removing folders from Syncthing")

            folders.forEach { deviceFolders ->
                mutex.withLock {
                    deviceFolders.value.forEach { folder ->
                        try {
                            val resp = httpClient.delete<HttpResponse>(deviceEndpoint(deviceFolders.key, "/rest/config/folders/$folder")) {
                                headers {
                                    append("X-API-Key", deviceFolders.key.apiKey)
                                }
                            }

                            if (resp.status != HttpStatusCode.OK) {
                                throw RPCException(
                                    resp.content.toByteArray().toString(Charsets.UTF_8),
                                    dk.sdu.cloud.calls.HttpStatusCode.BadRequest
                                )
                            }
                        } catch (ex: Throwable) {
                            log.error("Syncthing responded: ${ex.message}")
                            throw RPCException(
                                "Invalid Syncthing Configuration",
                                dk.sdu.cloud.calls.HttpStatusCode.BadRequest
                            )
                        }
                    }
                }
                lock.release()
            }
        }
    }

    suspend fun addDevices(toDevices: List<LocalSyncthingDevice> = emptyList()) {
        if (lock.acquire()) {
            var pendingDevices = toDevices.ifEmpty {
                config.devices
            }

            val timeout = Time.now() + 10_000

            log.debug("Adding devices to Syncthing")

            while (pendingDevices.isNotEmpty()) {
                if (Time.now() > lastWrite.get() + 1_000) {
                    mutex.withLock {
                        val result = db.withSession { session ->
                            session.sendPreparedStatement(
                                {
                                    setParameter("devices", pendingDevices.map { it.id })
                                },
                                """
                                       select f.id, f.path, f.sync_type, f.device_id as local_device_id, d.device_id, d.user_id, f.user_id
                                       from
                                          file_ucloud.sync_folders f join
                                          file_ucloud.sync_devices d on f.user_id = d.user_id
                                       where
                                          f.device_id in (select unnest(:devices::text[]))
                                    """
                            )
                        }.rows

                        pendingDevices.forEach { device ->
                            val newDevices = result
                                .filter {
                                    it.getString("local_device_id") == device.id
                                }
                                .distinctBy { it.getField(SyncDevicesTable.device) }
                                .map { row ->
                                    SyncthingDevice(
                                        deviceID = row.getField(SyncDevicesTable.device),
                                        name = row.getField(SyncDevicesTable.device)
                                    )
                                }

                            try {
                                val resp = httpClient.put<HttpResponse>(deviceEndpoint(device, "/rest/config/devices")) {
                                    body = TextContent(
                                        defaultMapper.encodeToString(newDevices),
                                        ContentType.Application.Json
                                    )
                                    headers {
                                        append("X-API-Key", device.apiKey)
                                    }
                                }

                                if (resp.status != HttpStatusCode.OK) {
                                    throw RPCException(
                                        resp.content.toByteArray().toString(Charsets.UTF_8),
                                        dk.sdu.cloud.calls.HttpStatusCode.BadRequest
                                    )
                                } else {
                                    pendingDevices = pendingDevices.filter { it.id != device.id }
                                }

                            } catch (ex: Throwable) {
                                log.error("Syncthing responded: ${ex.message}")
                                throw RPCException("Invalid Syncthing Configuration", dk.sdu.cloud.calls.HttpStatusCode.BadRequest)
                            }
                        }
                    }
                    lastWrite.set(Time.now())

                    if (Time.now() > timeout) {
                        throw RPCException(
                            "Unable to contact one or more syncthing clients",
                            dk.sdu.cloud.calls.HttpStatusCode.ServiceUnavailable
                        )
                    }
                }
            }
            lock.release()

        }

        // When a device is added to syncthing, the folders the user have already added to sync should be readded.
        addFolders(toDevices)
    }

    suspend fun removeDevices(devices: List<String>) {
        if (lock.acquire()) {
            val timeout = Time.now() + 10_000
            log.debug("Removing devices from Syncthing")

            // Check if there's devices with the same (deleted) Device IDs left. If so the device should not be
            // removed from syncthing.
            val deviceCount = db.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("devices", devices)
                    },
                    """
                       select count(d.id)
                       from
                          file_ucloud.sync_devices d
                       where
                          d.device_id in (select unnest(:devices::text[]))
                    """
                )
            }.rows.firstOrNull()?.getLong(0)

            if (deviceCount != null && deviceCount > 0) {
                return
            }

            while (Time.now() < timeout) {
                if (Time.now() > lastWrite.get() + 1_000) {
                    mutex.withLock {
                        config.devices.forEach { localDevice ->
                            devices.forEach { device ->
                                try {
                                    val resp = httpClient.delete<HttpResponse>(
                                        deviceEndpoint(
                                            localDevice,
                                            "/rest/config/devices/$device"
                                        )
                                    ) {
                                        headers {
                                            append("X-API-Key", localDevice.apiKey)
                                        }
                                    }

                                    if (resp.status != HttpStatusCode.OK) {
                                        throw RPCException(
                                            resp.content.toByteArray().toString(Charsets.UTF_8),
                                            dk.sdu.cloud.calls.HttpStatusCode.BadRequest
                                        )
                                    }

                                } catch (ex: Throwable) {
                                    log.error("Syncthing responded: ${ex.message}")
                                    throw RPCException(
                                        "Invalid Syncthing Configuration",
                                        dk.sdu.cloud.calls.HttpStatusCode.BadRequest
                                    )
                                }
                            }
                        }
                    }
                    lastWrite.set(Time.now())

                    if (Time.now() > timeout) {
                        throw RPCException(
                            "Unable to contact one or more syncthing clients",
                            dk.sdu.cloud.calls.HttpStatusCode.ServiceUnavailable
                        )
                    }
                }
            }
            lock.release()
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
