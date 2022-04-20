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
    val syncthingProcess = ProcessBuilder().apply {
        command(
            "/opt/syncthing/syncthing", 
            "--home", configFolder.path,
            "--gui-address", "0.0.0.0:8384",
        )
        redirectError(ProcessBuilder.Redirect.appendTo(File("/dev/stderr")))
        redirectOutput(ProcessBuilder.Redirect.appendTo(File("/dev/stdout")))
    }.start()

    Runtime.getRuntime().addShutdownHook(Thread {
        syncthingProcess.destroy()
    })

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

