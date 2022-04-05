package syncthing

import dk.sdu.cloud.syncthing.defaultMapper
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
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

    init {
        /*
         * Wait for Syncthing to create the config file, if it isn't created yet, then read the apiKey and device ID
         */
        val syncthingConfig = File("/var/syncthing/config.xml")

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
    }

    /*
     * Listen for changes to mounted config
     */
    fun listen() {
        val ucloudConfigFile = File(configFolder, "ucloud_config.json")

        var nextScan = System.currentTimeMillis()
        while (true) {
            if (System.currentTimeMillis() > nextScan) {
                try {
                    val newConfig = defaultMapper.decodeFromString<UCloudSyncthingConfig>(ucloudConfigFile.readText())

                    if (newConfig != config) {
                        println("Using new config")
                        config = newConfig
                    }
                } catch (e: Throwable) {
                    println("Unable to use new config")
                }
                nextScan = System.currentTimeMillis() + 5000
            }
        }
    }
}