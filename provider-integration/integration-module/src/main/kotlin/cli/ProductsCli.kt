package dk.sdu.cloud.cli

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.config.ConfigSchema
import dk.sdu.cloud.config.explainPricing
import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.plugins.ipcServer
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.utils.sendTerminalMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import java.util.Scanner

fun ProductsCli(controllerContext: ControllerContext) {
    val pluginContext = controllerContext.pluginContext
    val config = pluginContext.config
    pluginContext.commandLineInterface?.addHandler(CliHandler("products") { args ->
        val ipcClient = controllerContext.pluginContext.ipcClient

        try {
            when (args.firstOrNull()) {
                null, "register" -> {
                    sendTerminalMessage {
                        line("Looking up unknown products from UCloud...")
                    }

                    val preview = ipcClient.sendRequest(ProductsIpc.preview, Unit)

                    sendTerminalMessage {
                        if (preview.unknown.isEmpty()) {
                            line(
                                "No products are unknown to UCloud. You can update the list of products from " +
                                    ConfigSchema.FILE_PRODUCTS
                            )
                        } else {
                            preview
                                .unknown
                                .sortedBy { "${it.productType} / ${it.category.name} / ${it.name}" }
                                .forEachIndexed { idx, p ->
                                    line()
                                    line(CharArray(120) { '-' }.concatToString())
                                    bold { line("${p.name} / ${p.category.name} (${p.productType})") }

                                    line(p.description)
                                    line()

                                    line(p.explainPricing())
                                    line()

                                    when (p) {
                                        is ProductV2.Compute -> {
                                            bold { inline("vCPU: ") }
                                            line((p.cpu ?: 1).toString())

                                            bold { inline("Memory: ") }
                                            inline((p.memoryInGigs ?: 1).toString())
                                            line("GB")

                                            bold { inline("GPU: ") }
                                            line((p.gpu ?: 0).toString())
                                        }
                                        is ProductV2.Ingress -> {}
                                        is ProductV2.License -> {}
                                        is ProductV2.NetworkIP -> {}
                                        is ProductV2.Storage -> {}
                                    }
                                }

                            line()
                            line(CharArray(120) { '-' }.concatToString())
                        }
                    }


                    val scanner = Scanner(System.`in`)
                    if (preview.unknown.isNotEmpty()) {
                        loop@ while (true) {
                            sendTerminalMessage {
                                bold { red { inline("Do you wish to register these with UCloud? [y/N]: ") } }
                            }
                            val line = scanner.nextLine()
                            when (line) {
                                "y", "Y", "yes" -> {
                                    ipcClient.sendRequest(ProductsIpc.register, Unit)
                                    sendTerminalMessage {
                                        bold {
                                            green {
                                                line("Success! You will need to restart the IM for changes to take effect.")
                                            }
                                        }
                                    }
                                    break@loop
                                }

                                "", "n", "N", "no" -> {
                                    break@loop
                                }

                                else -> {
                                    sendTerminalMessage {
                                        line("Please type 'y' or 'n'")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (ex: RPCException) {
            if (ex.httpStatusCode == HttpStatusCode.Forbidden) {
                sendTerminalMessage {
                    red { bold { line("You must run this script as root!") } }
                }
            } else {
                sendTerminalMessage {
                    red { bold { line("An error has occured. We received the following message:") } }
                    line(ex.why)
                }
            }
        }
    })

    if (config.shouldRunServerCode()) {
        val rpcClient = pluginContext.rpcClient

        pluginContext.ipcServer.addHandler(ProductsIpc.preview.handler { user, request ->
            if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            ProductPreview(config.products.productsUnknownToUCloud.toList())
        })

        pluginContext.ipcServer.addHandler(ProductsIpc.register.handler { user, request ->
            if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

            try {
                ProductsV2.create.call(
                    BulkRequest(config.products.productsUnknownToUCloud.toList()),
                    rpcClient
                ).orThrow()
            } catch (ignored: Throwable) {
                var failures = ""
                for (p in config.products.productsUnknownToUCloud) {
                    try {
                        ProductsV2.create.call(
                            BulkRequest(listOf(p)),
                            rpcClient
                        ).orThrow()
                    } catch (ex: Throwable) {
                        failures += "Error while trying to create product: $p\n${ex.stackTraceToString()}\n\n"
                    }
                }
                throw RPCException(failures, HttpStatusCode.BadGateway)
            }

            config.products.productsUnknownToUCloud = emptySet()
        })
    }
}

@Serializable
private data class ProductPreview(val unknown: List<ProductV2>)

private object ProductsIpc : IpcContainer("products") {
    val preview = updateHandler("preview", Unit.serializer(), ProductPreview.serializer())
    val register = updateHandler("register", Unit.serializer(), Unit.serializer())
}
