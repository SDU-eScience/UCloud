package dk.sdu.cloud.syncthing

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        log.error("Missing argument: config path")
        throw FileNotFoundException()
    }

    val configFolder = File(args[0])
    if (!configFolder.exists()) {
        log.error("Config folder not found")
        throw FileNotFoundException(configFolder.path)
    }

    // Launch Syncthing process
    GlobalScope.launch {
        val syncthingProcess = Runtime.getRuntime().exec("/opt/syncthing/syncthing --home ${configFolder.path}")
        val out = syncthingProcess.inputStream.bufferedReader()
        var line = out.readLine()
        while (line != null) {
            println(line)
            line = out.readLine()
        }
    }

    val ucloudConfigService = UCloudConfigService(configFolder.path)
    ucloudConfigService.start()
}

private val log = LoggerFactory.getLogger("Syncthing")


fun String.fileName(): String = substringAfterLast('/')

val defaultMapper = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    classDiscriminator = "type"
    isLenient = true
    coerceInputValues = true
}
