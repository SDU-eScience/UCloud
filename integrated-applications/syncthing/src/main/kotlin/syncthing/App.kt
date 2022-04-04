package dk.sdu.cloud.syncthing

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.lang.Compiler.command

fun main(args: Array<String>) {
    println("START")
    runBlocking {
        // Launch Syncthing
        launch {
            val syncthingProcess = Runtime.getRuntime().exec("/opt/syncthing/syncthing --home /var/syncthing")
            val out = syncthingProcess.inputStream.bufferedReader()
            var line = out.readLine()
            while (line != null) {
                println("SYNCTHING: $line")
                line = out.readLine()
            }
        }

        // TODO(Brian) Read api key and device id

        // TODO(Brian) Listen for changes to mounted config
    }
    println("END")
}

fun String.fileName(): String = substringAfterLast('/')

val defaultMapper = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    classDiscriminator = "type"
    isLenient = true
    coerceInputValues = true
}

data class SyncConfiguration(
    val devices: List<LocalSyncthingDevice> = emptyList(),
)

data class LocalSyncthingDevice(
    val name: String = "UCloud",
    val hostname: String = "",
    val apiKey: String = "",
    val id: String = "",
    val addresses: List<String> = emptyList(),
    val port: Int = 80,
    val username: String = "",
    val password: String = "",
    val rescanIntervalSeconds: Int = 3600,
    val doNotChangeHostNameForMounter: Boolean = false,
)

class SyncthingClient(
    val config: SyncConfiguration,
) {
    private val httpClient = HttpClient(CIO) {
        expectSuccess = false
    }

    private fun deviceEndpoint(device: LocalSyncthingDevice, path: String): String {
        return "http://${device.hostname}:${device.port}/${path.removePrefix("/")}"
    }

    private fun canMap() {
        defaultMapper.encodeToString("Hello, World")
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
    ): List<SyncedFolder> {
        return emptyList()
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

    suspend fun addDevices(localDevices: List<LocalSyncthingDevice> = config.devices) {
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
        addFolders(localDevices)
    }

    suspend fun removeDevices(devices: List<String>) {
        log.debug("Removing devices from Syncthing")

        config.devices.forEach { localDevice ->
            devices.forEach { device ->
                // NOTE(Dan): Not throwing since the device might not be available on this UCloud device.
                httpClient.delete<HttpResponse>(
                    deviceEndpoint(localDevice, "/rest/config/devices/$device"),
                    apiRequest(localDevice)
                )
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
                status.value
            )
        }
    }

    companion object {
        val log = LoggerFactory.getLogger("Syncthing")
    }
}

class RPCException(message: String, val code: Int) : RuntimeException(message)

@Serializable
enum class SynchronizationType(val syncthingValue: String) {
    SEND_RECEIVE("sendreceive"),
    SEND_ONLY("sendonly")
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
