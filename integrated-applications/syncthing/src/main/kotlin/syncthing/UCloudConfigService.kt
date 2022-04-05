package syncthing

import dk.sdu.cloud.syncthing.SyncthingClient
import dk.sdu.cloud.syncthing.defaultMapper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
     * Wait for Syncthing to create the config file, if it isn't created yet, then read the apiKey and device ID.
     * Then write the device ID to the config folder
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

        println("apiKey is: $apiKey")
        println("deviceId is: $deviceId")

        // Write device ID
        val deviceIdFile = File(configFolder, "ucloud_device_id.txt")
        deviceIdFile.writeText(deviceId)

        config = UCloudSyncthingConfig()

        // Read previously observed folders
        previouslyObservedFile = File(configFolder, "previously_observed.txt")
        previouslyObserved = if (previouslyObservedFile.exists()) {
            previouslyObservedFile.readLines().toHashSet()
        } else {
            emptySet<String>().toHashSet()
        }

        syncthingClient = SyncthingClient(apiKey)
    }

    /*
     * Listen for changes to mounted config (main-loop)
     */
    fun listen() {
        val ucloudConfigFile = File(configFolder, "ucloud_config.json")

        var nextScan = System.currentTimeMillis()
        while (!requiresRestart) {
            if (System.currentTimeMillis() > nextScan) {
                try {
                    val newConfig = defaultMapper.decodeFromString<UCloudSyncthingConfig>(ucloudConfigFile.readText())

                    val validatedNewConfig = validate(newConfig)

                    if (validatedNewConfig != config) {
                        println("Using new config")
                        apply(validatedNewConfig)
                    }
                } catch (e: Throwable) {
                    println("Unable to use new config: ${e.message}")
                }
                nextScan = System.currentTimeMillis() + 5000
            }
        }

        flushPreviouslyObservedToDisk()
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

    private fun apply(newConfig: UCloudSyncthingConfig) {
        val oldConfig = config
        config = newConfig

        GlobalScope.launch {
            // TODO(Brian Write new devices
            syncthingClient.addDevices(config.devices.filter { !oldConfig.devices.contains(it) })

            // TODO(Brian) Remove old devices
            syncthingClient.removeDevices(oldConfig.devices
                .filter { !config.devices.contains(it) }
                .map { it.deviceId.toString() })

            // TODO(Brian) Write new folders
            syncthingClient.addFolders(config.folders.filter { !oldConfig.folders.contains(it) }, config.devices)

            // TODO(Brian) Remove old folders
            syncthingClient.removeFolders(oldConfig.folders.filter { !config.folders.contains(it) })
        }
    }

    private fun flushPreviouslyObservedToDisk() {
        previouslyObservedFile.writeText(
            previouslyObserved.joinToString("\n")
        )
    }


    /*companion object {
        val log = LoggerFactory.getLogger("UCloudConfigService")
    }*/
}
