package dk.sdu.cloud.cli

import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.plugins.storage.ucloud.StorageScanIpc
import dk.sdu.cloud.plugins.storage.ucloud.StorageScanReportUsage
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

            subcommand("report-usage", "Send a new reportUsage() call to correct prior mistakes") {
                arg("<workspace>", optional = false) {
                    description = "The workspace reference"
                }
                arg("<productName>", optional = false) {
                    description = "The name of the product"
                }
                arg("<productCategory>", optional = false) {
                    description = "The name of the product category"
                }
                arg("<driveId>", optional = false) {
                    description = "The drive ID (it does not have to exist anymore)"
                }
                arg("<usageInBytes>", optional = false) {
                    description = "Usage in bytes"
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

                "report-usage" -> {
                    val workspace = args.getOrNull(1) ?: sendHelp()
                    val productName = args.getOrNull(2) ?: sendHelp()
                    val productCategory = args.getOrNull(3) ?: sendHelp()
                    val driveId = args.getOrNull(4)?.toLongOrNull() ?: sendHelp()
                    val usageInBytes = args.getOrNull(5)?.toLongOrNull() ?: sendHelp()

                    ipcClient.sendRequest(StorageScanIpc.reportUsage, StorageScanReportUsage(
                        workspace,
                        productName,
                        productCategory,
                        driveId,
                        usageInBytes
                    ))

                    sendTerminalMessage { line("OK!") }
                }

                else -> sendHelp()
            }
        }
    })
}
