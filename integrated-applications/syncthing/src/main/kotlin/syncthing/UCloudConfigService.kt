package dk.sdu.cloud.syncthing

import kotlin.system.exitProcess
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@Serializable
data class UCloudSyncthingConfig(
    val folders: List<Folder> = emptyList(),
    val devices: List<Device> = emptyList()
) {

    @Serializable
    data class Folder(
        val id: String,
        val path: String,
    )

    @Serializable
    data class Device(
        val deviceId: String,
        val label: String,
    )
}

class UCloudConfigService(
    private val configFolder: String
) {
    private val apiKey: String
    private val deviceId: String
    private var config: UCloudSyncthingConfig
    private var previouslyObserved: HashSet<String>
    private var requiresRestart = false
    private val syncthingClient: SyncthingClient
    private val previouslyObservedFile: File

    /*
     * Wait for Syncthing to initialize, then read the apiKey and device ID,
     * If this is the first time the instance is started, update the name of the Syncthing instance and write the
     * device ID to the config folder
     */
    init {
        val syncthingConfig = File(configFolder, "config.xml")

        val timeout = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < timeout) {
            if (syncthingConfig.exists()) {
                break
            }
        }

        val configDoc: Document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(syncthingConfig)
        val guiElement = configDoc.getElementsByTagName("gui").item(0) as Element
        val apiKeyElement = guiElement.getElementsByTagName("apikey")
        apiKey = apiKeyElement.item(0).textContent
        val deviceElement = configDoc.getElementsByTagName("device").item(0) as Element
        deviceId = deviceElement.getAttribute("id")

        log.info("apiKey is: $apiKey")
        log.info("deviceId is: $deviceId")

        config = UCloudSyncthingConfig()

        // Read previously observed folders
        previouslyObservedFile = File(configFolder, "previously_observed.txt")
        previouslyObserved = if (previouslyObservedFile.exists()) {
            previouslyObservedFile.readLines().toHashSet()
        } else {
            emptySet<String>().toHashSet()
        }

        syncthingClient = SyncthingClient(apiKey)

        // If the deviceIdFile does not exist, this is (most likely) the first time the instance is running.
        val deviceIdFile = File(configFolder, "ucloud_device_id.txt")
        if (!deviceIdFile.exists()) {
            GlobalScope.launch {
                log.info("Updating name of Syncthing instance and setting default configuration")
                val timeout = System.currentTimeMillis() + 10_000
                while (System.currentTimeMillis() < timeout) {
                    try {
                        syncthingClient.addDevices(
                            listOf(
                                UCloudSyncthingConfig.Device(deviceId, "UCloud")
                            )
                        )
                        syncthingClient.configureOptions()
                        syncthingClient.configureGui()

                        // Remove default folder
                        syncthingClient.removeFolders(
                            listOf(UCloudSyncthingConfig.Folder("default", ""))
                        )
                        break
                    } catch (e: Throwable) {
                        // Do nothing
                    }
                    delay(1000)
                }
            }

            deviceIdFile.writeText(deviceId)
        }

        val restartFile = File(configFolder, "restart.txt")
        if (restartFile.exists()) {
            restartFile.delete()
        }
    }

    /*
     * Start the service/listen for changes to mounted config (main-loop)
     * Checks for changes every few seconds, and if found the changes are validated and applied
     */
    fun start() {
        val ucloudConfigFile = File(configFolder, "ucloud_config.json")

        while (!requiresRestart) {
            try {
                val restartFile = File(configFolder, "restart.txt")
                if (restartFile.exists()) {
                    requiresRestart = true
                }
            } catch (e: Throwable) {
                log.debug("Caught exception while monitoring for restart signal: ${e.stackTraceToString()}")
            }

            try {
                val newConfig = defaultMapper.decodeFromString<UCloudSyncthingConfig>(ucloudConfigFile.readText())

                val validatedNewConfig = validate(newConfig)

                if (validatedNewConfig != config) {
                    log.info("Using new config")
                    apply(validatedNewConfig)
                }
            } catch (e: Throwable) {
                log.debug("Unable to use new config: ${e.message}")
            }

            Thread.sleep(5000)
        }

        flushPreviouslyObservedToDisk()

        exitProcess(0)
    }

    /*
     * Validate the configuration,
     */
    private fun validate(newConfig: UCloudSyncthingConfig): UCloudSyncthingConfig {
        val validatedFolders: HashSet<UCloudSyncthingConfig.Folder> = HashSet()

        for (folder in newConfig.folders) {
            if (!File(folder.path).exists()) {
                if (previouslyObserved.contains(folder.path)) {
                    // ignore and do not apply
                } else {
                    previouslyObserved.add(folder.path)
                    requiresRestart = true
                }
            } else {
                validatedFolders.add(folder)
            }
        }

        return UCloudSyncthingConfig(validatedFolders.toList(), newConfig.devices)
    }

    /*
     * Apply changes to configuration to Syncthing
     */
    private fun apply(newConfig: UCloudSyncthingConfig) {
        val oldConfig = config
        val timeout = System.currentTimeMillis() + 10_000
        config = newConfig

        GlobalScope.launch {
            while (System.currentTimeMillis() < timeout) {
                try {
                    // Write new devices
                    syncthingClient.addDevices(config.devices.filter { !oldConfig.devices.contains(it) })

                    // Remove old devices
                    syncthingClient.removeDevices(oldConfig.devices
                        .filter { !config.devices.contains(it) }
                        .map { it.deviceId })

                    // All folders are rewritten to Syncthing, to make sure new devices are added to each folder
                    syncthingClient.addFolders(config.folders, config.devices)

                    // Remove old folders
                    syncthingClient.removeFolders(oldConfig.folders.filter { !config.folders.contains(it) })

                    break
                } catch (e: Throwable) {
                    log.debug("Unable to apply configuration: ${e.message}")
                }
                delay(1000)
            }
        }
    }

    private fun flushPreviouslyObservedToDisk() {
        previouslyObservedFile.writeText(
            previouslyObserved.joinToString("\n")
        )
    }


    companion object {
        val log = LoggerFactory.getLogger("UCloudConfigService")
    }
}

