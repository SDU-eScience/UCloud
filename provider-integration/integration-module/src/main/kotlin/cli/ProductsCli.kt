package dk.sdu.cloud.cli

import dk.sdu.cloud.ServerMode
import dk.sdu.cloud.accounting.api.ChargeType
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductPriceUnit
import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.config.ConfigSchema
import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.TypedIpcHandler
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.plugins.ipcServer
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.utils.sendTerminalMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
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

                                    run {
                                        // NOTE(Dan): Cost explanation is somewhat complicated

                                        if (p.chargeType == ChargeType.DIFFERENTIAL_QUOTA) {
                                            inline("This product will be allocated on a ")
                                            bold { inline("QUOTA") }
                                            line(" basis.")
                                        } else {
                                            val isUnitsPer = p.unitOfPrice.name.startsWith("UNITS_PER_")
                                            val isCreditsPer = p.unitOfPrice.name.startsWith("CREDITS_PER_")
                                            val isOneTimePayment = !isUnitsPer && !isCreditsPer
                                            val isPaidByCredits =
                                                isCreditsPer || p.unitOfPrice == ProductPriceUnit.CREDITS_PER_UNIT

                                            val priceMultiplier = when (p) {
                                                is Product.Compute -> p.cpu ?: 1
                                                else -> 1
                                            }

                                            val unit: String? = when (p) {
                                                is Product.Compute -> null // Use custom message
                                                is Product.Storage -> "GB"
                                                is Product.Ingress -> "link"
                                                is Product.License -> "license"
                                                is Product.NetworkIP -> "IP"
                                            }

                                            if (isPaidByCredits) {
                                                val priceString = run {
                                                    val price = p.pricePerUnit * priceMultiplier
                                                    val fullString = price.toString().padStart(6, '0')
                                                    val before = fullString.substring(0, fullString.length - 6)
                                                        .takeIf { b -> b.isNotEmpty() } ?: "0"
                                                    var after = fullString.substring(fullString.length - 6)
                                                    while (true) {
                                                        if (!after.endsWith("0")) break
                                                        after = after.removeSuffix("0")
                                                    }
                                                    if (after.isEmpty()) after = "0"

                                                    "$before.$after DKK"
                                                }

                                                inline("This product is paid for in ")
                                                bold { inline("DKK. ") }

                                                if (isOneTimePayment) {
                                                    inline("Each requires a ")
                                                    bold { inline("one-time payment ") }
                                                    inline("of ")
                                                    bold { inline(priceString) }
                                                    line(".")
                                                } else {
                                                    inline("The price for using this product for one ")
                                                    inline(
                                                        when (p.unitOfPrice) {
                                                            ProductPriceUnit.CREDITS_PER_DAY -> "day "
                                                            ProductPriceUnit.CREDITS_PER_HOUR -> "hour "
                                                            ProductPriceUnit.CREDITS_PER_MINUTE -> "minute "
                                                            else -> error("bad state")
                                                        }
                                                    )

                                                    inline("is ")
                                                    bold { inline(priceString) }
                                                    if (unit != null) inline(" per $unit")
                                                    line(".")
                                                }
                                            } else {
                                                if (isOneTimePayment) {
                                                    inline("Users will be granted a number of ")
                                                    bold { inline((unit ?: "CPU") + "s") }
                                                    line(" to use.")
                                                } else {
                                                    inline("Users will be charged for every ")
                                                    bold {
                                                        inline(unit ?: "core ")
                                                        inline(
                                                            when (p.unitOfPrice) {
                                                                ProductPriceUnit.UNITS_PER_DAY -> "day "
                                                                ProductPriceUnit.UNITS_PER_HOUR -> "hour "
                                                                ProductPriceUnit.UNITS_PER_MINUTE -> "minute "
                                                                else -> error("bad state")
                                                            }
                                                        )
                                                    }
                                                    line("of use.")
                                                }
                                            }
                                        }
                                    }

                                    line()

                                    when (p) {
                                        is Product.Compute -> {
                                            bold { inline("vCPU: ") }
                                            line((p.cpu ?: 1).toString())

                                            bold { inline("Memory: ") }
                                            inline((p.memoryInGigs ?: 1).toString())
                                            line("GB")

                                            bold { inline("GPU: ") }
                                            line((p.gpu ?: 1).toString())
                                        }
                                        is Product.Ingress -> {}
                                        is Product.License -> {}
                                        is Product.NetworkIP -> {}
                                        is Product.Storage -> {}
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

            Products.create.call(
                BulkRequest(config.products.productsUnknownToUCloud.toList()),
                rpcClient
            ).orThrow()

            config.products.productsUnknownToUCloud = emptySet()
        })
    }
}

@Serializable
private data class ProductPreview(val unknown: List<Product>)

private object ProductsIpc : IpcContainer("products") {
    val preview = updateHandler("preview", Unit.serializer(), ProductPreview.serializer())
    val register = updateHandler("register", Unit.serializer(), Unit.serializer())
}
