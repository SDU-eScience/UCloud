package dk.sdu.cloud.plugins.licenses.generic

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.AppParameterValue
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.cli.CliHandler
import dk.sdu.cloud.config.ConfigSchema
import dk.sdu.cloud.config.ProductReferenceWithoutProvider
import dk.sdu.cloud.controllers.RequestContext
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.LicensePlugin
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.plugins.ipcServer
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.bindIntNullable
import dk.sdu.cloud.sql.bindStringNullable
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
import dk.sdu.cloud.utils.sendTerminalMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.system.exitProcess

class GenericLicensePlugin : LicensePlugin {
    override val pluginTitle: String = "Generic"
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<Product> = emptyList()

    override fun supportsRealUserMode(): Boolean = true
    override fun supportsServiceUserMode(): Boolean = true

    private lateinit var pluginConfig: ConfigSchema.Plugins.Licenses.Generic
    override fun configure(config: ConfigSchema.Plugins.Licenses) {
        this.pluginConfig = config as ConfigSchema.Plugins.Licenses.Generic
    }

    override suspend fun PluginContext.initialize() {
        if (config.shouldRunAnyPluginCode()) {
            commandLineInterface?.addHandler(CliHandler("license") { args ->
                fun printHelp(): Nothing {
                    sendTerminalMessage {
                        bold { inline("Usage: ") }
                        code { line("ucloud license <command> [args]") }
                        line()
                        bold { line("Commands:") }

                        run {
                            bold { code { inline("  register ") } }
                            code { inline("<name> <category> [--license=license] [--address=address:port]") }
                            line(" - Registers or modifies a license server")

                            code { inline("    name: ") }
                            line("The product name of the license server, must match an entry in products.yaml")

                            code { inline("    category: ") }
                            line("The product category of the license server, must match an entry in products.yaml")

                            code { inline("    --license: ") }
                            line("A license key associated with the server, can be left blank.")

                            code { inline("    --address: ") }
                            line("A hostname and port combination associated with the license server")

                            line()
                        }

                        run {
                            bold { code { inline("  delete ") } }
                            code { inline("<name> <category>") }
                            line(" - Deletes a license server")
                            line()
                        }

                        run {
                            bold { code { inline("  list ") } }
                            line(" - Lists all license servers registered with this module")
                            line()
                        }
                    }
                    exitProcess(0)
                }

                when (args.getOrNull(0)) {
                    "register" -> {
                        val name = args.getOrNull(1) ?: printHelp()
                        val category = args.getOrNull(2) ?: printHelp()
                        val license = args.find { it.startsWith("--license=") }?.removePrefix("--license=")
                        val addressAndPort = args.find { it.startsWith("--address=") }?.removePrefix("--address=")
                        val address = addressAndPort?.substringBefore(':')
                        val port = addressAndPort?.substringAfter(':', "")?.toIntOrNull()

                        if ((port == null && address != null && address.contains(":")) ||
                            (port != null && port !in 1..(65536))
                        ) {
                            sendTerminalMessage {
                                bold {
                                    red {
                                        line(
                                            "Invalid port specified! The port must be numeric value between 1 and 65536."
                                        )
                                    }
                                }
                                line()
                            }

                            printHelp()
                        }

                        try {
                            ipcClient.sendRequest(
                                GenericLicenseIpc.upsert,
                                GenericLicenseServer(
                                    ProductReferenceWithoutProvider(name, category),
                                    address,
                                    port,
                                    license
                                )
                            )

                            sendTerminalMessage {
                                bold { green { line("Success") } }
                            }
                        } catch (ex: Throwable) {
                            sendTerminalMessage {
                                bold { red { line("Error!") } }
                                line()
                                bold { line(ex.message ?: "Unknown error") }
                            }
                        }
                    }

                    "delete" -> {
                        val name = args.getOrNull(1) ?: printHelp()
                        val category = args.getOrNull(2) ?: printHelp()

                        try {
                            ipcClient.sendRequest(
                                GenericLicenseIpc.delete,
                                ProductReferenceWithoutProvider(name, category)
                            )

                            sendTerminalMessage {
                                bold { green { line("Success") } }
                            }
                        } catch (ex: Throwable) {
                            sendTerminalMessage {
                                bold { red { line("Error!") } }
                                line()
                                bold { line(ex.message ?: "Unknown error") }
                            }
                        }
                    }

                    "list" -> {
                        val results = try {
                            ipcClient.sendRequest(
                                GenericLicenseIpc.browse,
                                Unit
                            )
                        } catch (ex: Throwable) {
                            sendTerminalMessage {
                                bold { red { line("Error!") } }
                                line()
                                bold { line(ex.message ?: "Unknown error") }
                            }

                            exitProcess(1)
                        }

                        sendTerminalMessage {
                            val nameColumn = 20
                            val categoryColumn = 20
                            val licenseColumn = 40
                            val addressAndPortColumn = 40

                            bold {
                                inline("Name".padEnd(nameColumn))
                                inline("Category".padEnd(categoryColumn))
                                inline("License".padEnd(licenseColumn))
                                inline("Address".padEnd(addressAndPortColumn))
                                line()
                                line(CharArray(120) { '-' }.concatToString())
                            }

                            for (result in results.items) {
                                inline(result.product.id.padEnd(nameColumn))
                                inline(result.product.category.padEnd(categoryColumn))
                                inline((result.license ?: "None specified").padEnd(licenseColumn))
                                inline(
                                    if (result.address != null && result.port != null) {
                                        "${result.address}:${result.port}"
                                    } else {
                                        "None specified"
                                    }.padEnd(addressAndPortColumn)
                                )

                                line()
                            }
                        }
                    }

                    else -> printHelp()
                }

                exitProcess(0)
            })
        }

        if (!config.shouldRunServerCode()) return

        ipcServer.addHandler(GenericLicenseIpc.upsert.handler { user, request ->
            if (user.uid != 0) throw RPCException("You must be root to run this command", HttpStatusCode.Forbidden)

            productAllocation.find {
                it.id == request.product.id &&
                        it.category == request.product.category
            } ?: throw RPCException(
                "Unknown product, make sure that this product is registered with UCloud and configured " +
                        "as a generic license in plugins.yaml!",
                HttpStatusCode.BadRequest
            )

            dbConnection.withSession { session ->
                session.prepareStatement(
                    //language=postgresql
                    """
                        insert into generic_license_servers (name, category, address, port, license)
                        values (:name, :category, :address::text, :port, :license::text)
                        on conflict (name, category) do update set
                            address = excluded.address,
                            port = excluded.port,
                            license = excluded.license
                    """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindString("name", request.product.id)
                        bindString("category", request.product.category)
                        bindStringNullable("address", request.address)
                        bindIntNullable("port", request.port)
                        bindStringNullable("license", request.license)
                    }
                )
            }
        })

        ipcServer.addHandler(GenericLicenseIpc.delete.handler { user, request ->
            if (user.uid != 0) throw RPCException("You must be root to run this command", HttpStatusCode.Forbidden)

            dbConnection.withSession { session ->
                session.prepareStatement(
                    //language=postgresql
                    """
                        delete from generic_license_servers
                        where
                            name = :name and
                            category = :category
                    """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindString("name", request.id)
                        bindString("category", request.category)
                    }
                )
            }
        })

        ipcServer.addHandler(GenericLicenseIpc.browse.handler { user, _ ->
            if (user.uid != 0) throw RPCException("You must be root to run this command", HttpStatusCode.Forbidden)

            val result = ArrayList<GenericLicenseServer>()

            dbConnection.withSession { session ->
                session.prepareStatement(
                    """
                        select name, category, address, port, license 
                        from generic_license_servers
                        order by category, name
                    """
                ).useAndInvoke(
                    prepare = {
                        // Nothing to prepare
                    },

                    readRow = { row ->
                        result.add(
                            GenericLicenseServer(
                                ProductReferenceWithoutProvider(
                                    row.getString(0)!!,
                                    row.getString(1)!!,
                                ),
                                row.getString(2),
                                row.getInt(3),
                                row.getString(4)
                            )
                        )
                    }
                )
            }

            PageV2(result.size, result, null)
        })

        ipcServer.addHandler(GenericLicenseIpc.retrieve.handler { user, request ->
            // TODO Verify this request
            // TODO Verify this request
            // TODO Verify this request
            // TODO Verify this request
            // TODO Verify this request
            // TODO Verify this request
            // TODO Verify this request
            // TODO Verify this request
            // TODO Verify this request
            // TODO Verify this request

            val result = ArrayList<GenericLicenseServer>()

            val retrievedLicense = LicenseControl.retrieve.call(
                ResourceRetrieveRequest(LicenseIncludeFlags(), request.id),
                rpcClient
            ).orThrow()

            dbConnection.withSession { session ->
                session.prepareStatement(
                    """
                        select name, category, address, port, license 
                        from generic_license_servers
                        where
                            name = :name and
                            category = :category
                        order by category, name
                    """
                ).useAndInvoke(
                    prepare = {
                        bindString("name", retrievedLicense.specification.product.id)
                        bindString("category", retrievedLicense.specification.product.category)
                    },
                    readRow = { row ->
                        result.add(
                            GenericLicenseServer(
                                ProductReferenceWithoutProvider(
                                    row.getString(0)!!,
                                    row.getString(1)!!,
                                ),
                                row.getString(2),
                                row.getInt(3),
                                row.getString(4)
                            )
                        )
                    }
                )
            }

            result.singleOrNull() ?: throw RPCException("Unknown license", HttpStatusCode.NotFound)
        })
    }

    override suspend fun RequestContext.retrieveProducts(knownProducts: List<ProductReference>): BulkResponse<LicenseSupport> {
        return BulkResponse(knownProducts.map { LicenseSupport(it) })
    }

    override suspend fun RequestContext.create(resource: License): FindByStringId? {
        LicenseControl.update.call(
            bulkRequestOf(
                ResourceUpdateAndId(
                    resource.id,
                    LicenseUpdate(
                        Time.now(),
                        LicenseState.READY,
                        "License is ready for use"
                    )
                )
            ),
            rpcClient
        ).orThrow()
        return FindByStringId(resource.id)
    }

    override suspend fun RequestContext.delete(resource: License) {
        // Nothing to delete
    }

    override suspend fun PluginContext.buildParameter(param: AppParameterValue.License): String {
        val licenseInfo = ipcClient.sendRequest(
            GenericLicenseIpc.retrieve,
            FindByStringId(param.id)
        )

        return buildString {
            append(licenseInfo.address)
            append(":")
            append(licenseInfo.port)
            if (licenseInfo.license != null) {
                append("/")
                append(licenseInfo.license)
            }
        }
    }
}

@Serializable
data class GenericLicenseServer(
    val product: ProductReferenceWithoutProvider,
    val address: String? = null,
    val port: Int? = null,
    val license: String? = null
) {
    init {
        if (address != null) require(port != null) { "address and port must be supplied together" }
        if (port != null) require(address != null) { "address and port must be supplied together" }
    }
}

object GenericLicenseIpc : IpcContainer("generic_licenses") {
    val upsert = updateHandler("upsert", GenericLicenseServer.serializer(), Unit.serializer())
    val delete = deleteHandler(ProductReferenceWithoutProvider.serializer(), Unit.serializer())
    val browse = browseHandler(Unit.serializer(), PageV2.serializer(GenericLicenseServer.serializer()))
    val retrieve = retrieveHandler(FindByStringId.serializer(), GenericLicenseServer.serializer())
}
