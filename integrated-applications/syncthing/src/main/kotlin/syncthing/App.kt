package dk.sdu.cloud.syncthing

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import syncthing.UCloudConfigService
import java.io.File
import java.io.FileNotFoundException

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Missing argument: config path")
        return
    }

    val configFolder = File(args[0])
    if (!configFolder.exists()) {
        throw FileNotFoundException(configFolder.path)
    }

    // Launch Syncthing process (currently printing output for debugging)
    GlobalScope.launch {
        val syncthingProcess = Runtime.getRuntime().exec("/opt/syncthing/syncthing --home ${configFolder.path}")
        val out = syncthingProcess.inputStream.bufferedReader()
        var line = out.readLine()
        while (line != null) {
            println("SYNCTHING: $line")
            line = out.readLine()
        }
    }

    val ucloudConfigService = UCloudConfigService(configFolder.path)
    ucloudConfigService.listen()
}

fun String.fileName(): String = substringAfterLast('/')

val defaultMapper = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    classDiscriminator = "type"
    isLenient = true
    coerceInputValues = true
}

/*data class SyncConfiguration(
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
)*/
