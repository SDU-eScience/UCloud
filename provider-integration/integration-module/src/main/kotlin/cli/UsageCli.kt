package dk.sdu.cloud.cli

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.ProductType
import dk.sdu.cloud.accounting.api.providers.ProviderRegisteredResource
import dk.sdu.cloud.accounting.api.providers.ResourceChargeCredits
import dk.sdu.cloud.accounting.api.providers.ResourceControlApi
import dk.sdu.cloud.app.orchestrator.api.IngressControl
import dk.sdu.cloud.app.orchestrator.api.IngressSpecification
import dk.sdu.cloud.app.orchestrator.api.JobSpecification
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.JobUpdate
import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.app.orchestrator.api.LicenseControl
import dk.sdu.cloud.app.orchestrator.api.LicenseSpecification
import dk.sdu.cloud.app.orchestrator.api.NetworkIPControl
import dk.sdu.cloud.app.orchestrator.api.NetworkIPSpecification
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.config.ProductReferenceWithoutProvider
import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.file.orchestrator.api.FileCollection
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsControl
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.plugins.ipcServer
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.utils.CommaSeparatedValues
import dk.sdu.cloud.utils.forEachGraal
import dk.sdu.cloud.utils.sendTerminalMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

fun UsageCli(controllerContext: ControllerContext) {
    val pluginContext = controllerContext.pluginContext
    val config = pluginContext.config
    pluginContext.commandLineInterface?.addHandler(CliHandler("usage") { args ->
        fun sendHelp(): Nothing = sendCommandLineUsage("usage", "Tools to manage and self-report usage of a provider") {
            subcommand("register", "Registers usage not generated by the integration module with UCloud/Core") {
                arg("-f=<file.csv>", optional = false) {
                    description = """
                        
                    """.trimIndent()
                }
            }
        }

        val ipcClient = pluginContext.ipcClient
        genericCommandLineHandler {
            when (args.getOrNull(0)) {
                "register" -> {
                    val file = args.find { it.startsWith("-f=") }?.removePrefix("-f=") ?: sendHelp()

                    val linesOfFile = try {
                        File(file).readLines()
                    } catch (ex: Throwable) {
                        sendTerminalMessage {
                            red { bold { line("Could not open file '${File(file).absolutePath}' for reading") } }
                            line()
                        }
                        sendHelp()
                    }

                    val points = ArrayList<RegisteredUsage.DataPoint?>()
                    for ((index, line) in linesOfFile.withIndex()) {
                        if (line.isBlank()) {
                            points.add(null)
                            continue
                        }

                        val lineNumber = index + 1
                        val cells = CommaSeparatedValues.parse(line)
                        if (cells.isEmpty()) continue
                        if (cells.size != 6) {
                            sendTerminalMessage {
                                red { bold { line("Invalid number of columns in line $lineNumber") } }
                                line()
                            }

                            sendHelp()
                        }

                        val productId = cells[0]
                        val productCategory = cells[1]
                        val username = cells[2].takeIf { it.isNotBlank() } ?: "_ucloud"
                        val project = cells[3].takeIf { it.isNotBlank() }
                        val units = cells[4].toLongOrNull() ?: run {
                            sendTerminalMessage {
                                red { bold { line("Units specified in line $lineNumber is not a valid number") } }
                                line()
                            }

                            sendHelp()
                        }

                        val duration = cells[5].toLongOrNull() ?: run {
                            sendTerminalMessage {
                                red { bold { line("Duration specified in line $lineNumber is not a valid number") } }
                                line()
                            }

                            sendHelp()
                        }

                        val product = ProductReferenceWithoutProvider(productId, productCategory)
                        points.add(RegisteredUsage.DataPoint(product, username, project, units, duration))
                    }

                    ipcClient.sendRequest(
                        UsageIpc.register,
                        RegisteredUsage(points)
                    )

                    sendTerminalMessage {
                        bold { green { line("Success") } }
                    }
                }

                else -> sendHelp()
            }
        }
    })

    if (config.shouldRunServerCode()) {
        val rpcClient = pluginContext.rpcClient
        val ipcServer = pluginContext.ipcServer

        ipcServer.addHandler(UsageIpc.register.handler { user, request ->
            if (user.uid != 0) throw RPCException("Root is required for this script", HttpStatusCode.Forbidden)

            val rowsByType = HashMap<ProductType, ArrayList<RegisteredUsage.DataPoint>>()
            for ((index, point) in request.points.withIndex()) {
                if (point == null) continue
                val lineNumber = index + 1
                val resolvedProduct = controllerContext.pluginContext.config.products.allProducts
                    .find { it.name == point.product.id && it.category.name == point.product.category }
                    ?: run {
                        throw RPCException(
                            "Unknown product specified in line $lineNumber. " +
                                    "${point.product} is not registered in products.yaml!",
                            HttpStatusCode.BadRequest
                        )
                    }

                rowsByType.getOrPut(resolvedProduct.productType) { ArrayList() }.add(point)
            }

            var controlApi: ResourceControlApi<*, *, *, *, *, *, *>? = null
            var ids: List<FindByStringId> = emptyList()

            val uniquePrefix = UUID.randomUUID().toString()
            var idSuffix = 0
            fun hiddenId(): String = RESOURCE_HIDDEN_BY_PROVIDER + uniquePrefix + idSuffix++

            rowsByType.forEachGraal { type, allPoints ->
                allPoints.asSequence().chunked(250).forEach { chunk ->
                    when (type) {
                        ProductType.STORAGE -> {
                            ids = FileCollectionsControl.register.call(
                                BulkRequest(
                                    chunk.map { point ->
                                        ProviderRegisteredResource(
                                            FileCollection.Spec(
                                                "Imported by provider",
                                                ProductReference(
                                                    point.product.id,
                                                    point.product.category,
                                                    pluginContext.config.core.providerId,
                                                ),
                                            ),
                                            createdBy = point.username,
                                            project = point.project,
                                            providerGeneratedId = hiddenId()
                                        )
                                    }
                                ),
                                rpcClient
                            ).orThrow().responses

                            controlApi = FileCollectionsControl
                        }

                        ProductType.COMPUTE -> {
                            ids = JobsControl.register.call(
                                BulkRequest(
                                    chunk.map { point ->
                                        ProviderRegisteredResource(
                                            JobSpecification(
                                                unknownApplication,
                                                ProductReference(
                                                    point.product.id,
                                                    point.product.category,
                                                    pluginContext.config.core.providerId,
                                                ),
                                                name = "Imported from provider",
                                                parameters = emptyMap(),
                                                resources = emptyList(),
                                            ),
                                            createdBy = point.username,
                                            project = point.project
                                        )
                                    }
                                ),
                                rpcClient
                            ).orThrow().responses

                            JobsControl.update.call(
                                BulkRequest(
                                    ids.map { (id) ->
                                        ResourceUpdateAndId(id, JobUpdate(state = JobState.SUCCESS))
                                    }
                                ),
                                rpcClient
                            ).orThrow()

                            controlApi = JobsControl
                        }

                        ProductType.INGRESS -> {
                            ids = IngressControl.register.call(
                                BulkRequest(
                                    chunk.map { point ->
                                        ProviderRegisteredResource(
                                            IngressSpecification(
                                                "Imported by provider",
                                                ProductReference(
                                                    point.product.id,
                                                    point.product.category,
                                                    pluginContext.config.core.providerId,
                                                ),
                                            ),
                                            createdBy = point.username,
                                            project = point.project,
                                            providerGeneratedId = hiddenId()
                                        )
                                    }
                                ),
                                rpcClient
                            ).orThrow().responses

                            controlApi = IngressControl
                        }

                        ProductType.LICENSE -> {
                            ids = LicenseControl.register.call(
                                BulkRequest(
                                    chunk.map { point ->
                                        ProviderRegisteredResource(
                                            LicenseSpecification(
                                                ProductReference(
                                                    point.product.id,
                                                    point.product.category,
                                                    pluginContext.config.core.providerId,
                                                ),
                                            ),
                                            createdBy = point.username,
                                            project = point.project,
                                            providerGeneratedId = hiddenId()
                                        )
                                    }
                                ),
                                rpcClient
                            ).orThrow().responses

                            controlApi = LicenseControl
                        }

                        ProductType.NETWORK_IP -> {
                            ids = NetworkIPControl.register.call(
                                BulkRequest(
                                    chunk.map { point ->
                                        ProviderRegisteredResource(
                                            NetworkIPSpecification(
                                                ProductReference(
                                                    point.product.id,
                                                    point.product.category,
                                                    pluginContext.config.core.providerId,
                                                ),
                                            ),
                                            createdBy = point.username,
                                            project = point.project,
                                            providerGeneratedId = hiddenId()
                                        )
                                    }
                                ),
                                rpcClient
                            ).orThrow().responses

                            controlApi = NetworkIPControl
                        }
                    }

                    val api = controlApi ?: error("controlApi should not be null")
                    api.chargeCredits.call(
                        BulkRequest(
                            chunk.zip(ids).map { (point, findById) ->
                                ResourceChargeCredits(findById.id, findById.id, point.units, point.duration)
                            }
                        ),
                        rpcClient
                    ).orThrow()
                }
            }
        })
    }
}

private val unknownApplication = NameAndVersion("unknown", "unknown")
const val RESOURCE_HIDDEN_BY_PROVIDER = "__PROVIDER_HIDDEN_C3C13974-9E9F-4F24-A252-49D9892E1526__"

@Serializable
private data class RegisteredUsage(
    val points: List<DataPoint?>,
) {
    @Serializable
    data class DataPoint(
        val product: ProductReferenceWithoutProvider,
        val username: String,
        val project: String?,
        val units: Long,
        val duration: Long,
    )
}

private object UsageIpc : IpcContainer("usage") {
    val register = updateHandler("register", RegisteredUsage.serializer(), Unit.serializer())
}
