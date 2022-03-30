package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.LocalSyncthingDevice
import dk.sdu.cloud.file.ucloud.SyncConfiguration
import dk.sdu.cloud.service.Loggable
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

class SyncthingClient(
    val config: SyncConfiguration,
    private val db: DBContext,
) {
    private val httpClient = HttpClient(OkHttp) {
        expectSuccess = false
    }
    private fun deviceEndpoint(device: LocalSyncthingDevice, path: String): String {
        return "http://${device.hostname}:${device.port}/${path.removePrefix("/")}"
    }

    data class SyncedFolder(
        val id: Long,
        val path: String,
        val synchronizationType: SynchronizationType,
        val ucloudDevice: String,
        val endUserDevice: String,
        val username: String,
    )

    private suspend fun findSyncedFolders(
        devicesAnyOf: List<LocalSyncthingDevice>, 
        ctx: DBContext = db,
    ): List<SyncedFolder> {
        return ctx.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("devices", devicesAnyOf.map { it.id }) },
                """
                    select
                        f.id, f.path, f.sync_type, f.device_id as local_device_id, d.device_id, d.user_id
                    from
                       file_ucloud.sync_folders f join
                       file_ucloud.sync_devices d on f.user_id = d.user_id
                    where
                       f.device_id in (select unnest(:devices::text[]))
                """
            )
        }.rows.map {
            SyncedFolder(
                it.getLong(0)!!,
                it.getString(1)!!,
                SynchronizationType.valueOf(it.getString(2)!!),
                it.getString(3)!!,
                it.getString(4)!!,
                it.getString(5)!!
            )
        }
    }

    suspend fun writeConfig(pendingDevices: List<LocalSyncthingDevice> = config.devices) {
        log.debug("Writing config to Syncthing")

        val syncedFolders = findSyncedFolders(pendingDevices).groupBy { it.ucloudDevice }

        val devicesByUser = syncedFolders.values.flatMap { folders ->
            folders.map { it.username to it.endUserDevice }
        }.groupBy(
            keySelector = { it.first },
            valueTransform = { it.second }
        )

        pendingDevices.forEach { device ->
            val folders = syncedFolders[device.id] ?: emptyList()

            val newConfig = SyncthingConfig(
                devices = folders
                    .asSequence()
                    .distinctBy { it.endUserDevice }
                    .map { row ->
                        SyncthingDevice(
                            deviceID = row.endUserDevice,
                            name = row.endUserDevice
                        )
                    }
                    .toList() + listOf(
                    SyncthingDevice(
                        deviceID = device.id,
                        name = device.name,
                        addresses = device.addresses + SyncthingDevice().addresses
                    )
                ),
                folders = folders.asSequence()
                    .distinctBy { it.id }
                    .map { row ->
                        SyncthingFolder(
                            id = row.id.toString(),
                            label = row.path.fileName(),
                            devices = devicesByUser.getOrDefault(row.username, emptyList()).map {
                                SyncthingFolderDevice(it)
                            },
                            path = "/mnt/sync/${row.id}",
                            type = row.synchronizationType.syncthingValue,
                            rescanIntervalS = device.rescanIntervalSeconds
                        )
                    }
                    .toList(),
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

            runCatching {
                httpClient.put<HttpResponse>(
                    deviceEndpoint(device, "/rest/config"),
                    apiRequestWithBody(device, newConfig)
                ).orThrow()
            }
        }
    }

    suspend fun isReady(device: LocalSyncthingDevice): Boolean {
        return try {
            httpClient.get<HttpResponse>(
                deviceEndpoint(device, "/rest/system/ping"),
                apiRequest(device)
            ).status.isSuccess()
        } catch (ex: Throwable) {
            false
        }
    }

    suspend fun rescan(localDevices: List<LocalSyncthingDevice> = config.devices) {
        log.info("Attempting rescan of syncthing")
        localDevices.forEach { device ->
            runCatching {
                httpClient.post<HttpResponse>(deviceEndpoint(device, "/rest/db/scan"), apiRequest(device))
            }
        }
    }

    suspend fun addFolders(
        localDevices: List<LocalSyncthingDevice> = config.devices,
        ctx: DBContext = db,
    ) {
        val syncedFolders = findSyncedFolders(localDevices).groupBy { it.ucloudDevice }
        val devicesByUser = syncedFolders.values.flatMap { folders ->
            folders.map { it.username to it.endUserDevice }
        }.groupBy(
            keySelector = { it.first },
            valueTransform = { it.second }
        )

        localDevices.forEach { device ->
            val folders = syncedFolders[device.id] ?: emptyList()
            val newFolders = folders.asSequence()
                .distinctBy { it.id }
                .map { row ->
                    SyncthingFolder(
                        id = row.id.toString(),
                        label = row.path.fileName(),
                        devices = devicesByUser.getOrDefault(row.username, emptyList()).map {
                            SyncthingFolderDevice(it)
                        },
                        path = "/mnt/sync/${row.id}",
                        type = row.synchronizationType.syncthingValue,
                        rescanIntervalS = device.rescanIntervalSeconds
                    )
                }
                .toList()
            
            httpClient.put<HttpResponse>(
                deviceEndpoint(device, "/rest/config/folders"),
                apiRequestWithBody(device, newFolders)
            ).orThrow()
        }
    }

    suspend fun removeFolders(folders: Map<LocalSyncthingDevice, List<Long>>) {
        log.debug("Removing folders from Syncthing")

        folders.forEach { deviceFolders ->
            deviceFolders.value.forEach { folder ->
                httpClient.delete<HttpResponse>(
                    deviceEndpoint(deviceFolders.key, "/rest/config/folders/$folder"),
                    apiRequest(deviceFolders.key)
                ).orThrow()
            }
        }
    }

    suspend fun addDevices(localDevices: List<LocalSyncthingDevice> = config.devices, ctx: DBContext = db) {
        ctx.withSession { session ->
            val syncedFolders = findSyncedFolders(localDevices).groupBy { it.ucloudDevice }

            localDevices.forEach { localDevice ->
                val folders = syncedFolders[localDevice.id] ?: emptyList()
                val newDevices = folders
                    .asSequence()
                    .distinctBy { it.endUserDevice }
                    .map { row ->
                        SyncthingDevice(
                            deviceID = row.endUserDevice,
                            name = row.endUserDevice
                        )
                    }
                    .toList()

                httpClient.put<HttpResponse>(
                    deviceEndpoint(localDevice, "/rest/config/devices"),
                    apiRequestWithBody(localDevice, newDevices)
                ).orThrow()
            }

            // When a device is added to Syncthing, the folders the user have already added to sync should be re-added.
            addFolders(localDevices, session)
        }
    }

    suspend fun removeDevices(devices: List<String>, ctx: DBContext = db) {
        log.debug("Removing devices from Syncthing")

        // Check if there's devices with the same (deleted) Device IDs left. If so the device should not be
        // removed from syncthing.
        val deviceCount = ctx.withSession { session ->
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
        }.rows.firstOrNull()?.getLong(0) ?: 0L

        if (deviceCount > 0) return

        config.devices.forEach { localDevice ->
            devices.forEach { device ->
                httpClient.delete<HttpResponse>(
                    deviceEndpoint(localDevice, "/rest/config/devices/$device"),
                    apiRequest(localDevice)
                ).orThrow()
            }
        }
    }

    private fun apiRequest(device: LocalSyncthingDevice): HttpRequestBuilder.() -> Unit {
        return apiRequestWithBody<Unit>(device, null)
    }

    private inline fun <reified T> apiRequestWithBody(
        device: LocalSyncthingDevice,
        payload: T?
    ): HttpRequestBuilder.() -> Unit {
        return {
            if (payload != null) {
                body = TextContent(
                    defaultMapper.encodeToString(payload),
                    ContentType.Application.Json
                )
            }

            headers {
                append("X-API-Key", device.apiKey)
            }
        }
    }

    private suspend fun HttpResponse.orThrow() {
        if (!status.isSuccess()) {
            throw RPCException(
                content.toByteArray().toString(Charsets.UTF_8),
                dk.sdu.cloud.calls.HttpStatusCode.BadRequest
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

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
    val announceLANAddresses: Boolean = false,
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
    val localAnnounceEnabled: Boolean = false,
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
    val relaysEnabled: Boolean = false,
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
