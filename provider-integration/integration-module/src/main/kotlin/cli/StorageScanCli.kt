package dk.sdu.cloud.cli

import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.plugins.storage.ucloud.StorageScanIpc
import dk.sdu.cloud.serviceContext
import dk.sdu.cloud.utils.sendTerminalMessage

fun StorageScanCli(controllerContext: ControllerContext) {
    controllerContext.pluginContext.commandLineInterface?.addHandler(CliHandler("storage-scan") { args ->
        val ipcClient = serviceContext.ipcClient
        fun sendHelp(): Nothing = sendCommandLineUsage("storage-scan", "Used to interact with storage scan functionality") {
            subcommand("scan-now", "Triggers a scan of a specific drive") {
                arg("<driveId>", optional = false) {
                    description = "The UCloud ID of the drive"
                }
            }
        }

        genericCommandLineHandler {
            when (args.getOrNull(0)) {
                "scan-now" -> {
                    val driveId = args.getOrNull(1) ?: sendHelp()
                    val driveIdLong = driveId.toLongOrNull() ?: sendHelp()
                    ipcClient.sendRequest(StorageScanIpc.requestScan, FindByLongId(driveIdLong))
                    sendTerminalMessage { line("OK!") }
                }

                else -> sendHelp()
            }
        }
    })
}
