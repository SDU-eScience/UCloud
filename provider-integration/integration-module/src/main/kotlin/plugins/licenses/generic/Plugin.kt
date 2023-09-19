package dk.sdu.cloud.plugins.licenses.generic

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.ErrorCode
import dk.sdu.cloud.accounting.api.ProductCategoryIdV2
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.ProductV2
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
import dk.sdu.cloud.cli.sendCommandLineUsage
import dk.sdu.cloud.config.ConfigSchema
import dk.sdu.cloud.config.ProductReferenceWithoutProvider
import dk.sdu.cloud.controllers.RequestContext
import dk.sdu.cloud.controllers.UserMapping
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.LicensePlugin
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.plugins.compute.ucloud.FeatureIngress
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.plugins.ipcServer
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.*
import dk.sdu.cloud.utils.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.coroutines.coroutineContext
import kotlin.system.exitProcess

class GenericLicensePlugin : LicensePlugin {
    override val pluginTitle: String = "Generic"
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<ProductV2> = emptyList()

    private val accountingFailureListeners = ArrayList<suspend (owner: String, category: String) -> Unit>()
    fun onAccountingFailure(listener: suspend (owner: String, category: String) -> Unit) {
        accountingFailureListeners.add(listener)
    }

    override fun supportsRealUserMode(): Boolean = true
    override fun supportsServiceUserMode(): Boolean = true

    private lateinit var pluginConfig: ConfigSchema.Plugins.Licenses.Generic
    override fun configure(config: ConfigSchema.Plugins.Licenses) {
        this.pluginConfig = config as ConfigSchema.Plugins.Licenses.Generic
    }

    override suspend fun PluginContext.initialize() {
        if (config.shouldRunAnyPluginCode()) {
            commandLineInterface?.addHandler(CliHandler("license") { args ->
                fun printHelp(): Nothing = sendCommandLineUsage("license", "Manage license information") {
                    subcommand("add", "Adds information about a license (this requires an already registered product)") {
                        arg("name", description = "The name of the product (must be in products.yaml)")
                        arg("category", description = "The category of the product (must be in products.yaml)")
                        arg(
                            "--license=<license>",
                            optional = true,
                            description = "The license key associated with the server, can be left omitted."
                        )
                        arg(
                            "--address=<address>",
                            optional = true,
                            description = "A hostname and port combination associated with the license server, can be omitted."
                        )
                    }

                    subcommand("rm", "Removes information about a license. This does not delete the license from UCloud but will make it unusable.") {
                        arg("name", description = "The name of the product")
                        arg("category", description = "The category of the product")
                    }

                    subcommand("ls", "Lists all license servers registered with this module")
                }

                when (args.getOrNull(0)) {
                    "add", "register" -> {
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

                    "rm", "delete" -> {
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

                    "ls", "list" -> {
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

                        sendTerminalTable {
                            header("Name", 20)
                            header("Category", 20)
                            header("License", 40)
                            header("Address", 40)

                            for (result in results.items) {
                                nextRow()
                                cell(result.product.id)
                                cell(result.product.category)
                                cell(result.license ?: "None specified")
                                cell(if (result.address != null && result.port != null) {
                                    "${result.address}:${result.port}"
                                } else {
                                    "None specified"
                                })
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
            log.info("Retrieving information about license: ${user.uid} ${request.id}")
            val retrievedLicense = LicenseControl.retrieve.call(
                ResourceRetrieveRequest(LicenseIncludeFlags(), request.id),
                rpcClient
            ).orThrow()
            log.info("We have received the following license: ${retrievedLicense}")

            if (user.uid != 0) {
                val ucloudUser = UserMapping.localIdToUCloudId(user.uid)
                log.info("The UCloud user is: $ucloudUser")
                ResourceVerification.verifyAccessToResource(ucloudUser, retrievedLicense)
                log.info("This is OK")
            }

            val result = ArrayList<GenericLicenseServer>()
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

            log.info(("We found the following licenses: $result"))

            result.singleOrNull() ?: throw RPCException("Unknown license", HttpStatusCode.NotFound)
        })
    }

    override suspend fun RequestContext.retrieveProducts(knownProducts: List<ProductReference>): BulkResponse<LicenseSupport> {
        return BulkResponse(knownProducts.map { LicenseSupport(it) })
    }

    override suspend fun RequestContext.create(resource: License): FindByStringId? {
        val owner = resource.owner.project ?: resource.owner.createdBy
        val category = resource.specification.product.category

        dbConnection.withSession { session ->
            session.prepareStatement(
                //language=postgresql
                """
                    insert into generic_license_instances (id, category, owner)
                    values (:id, :category, :owner)
                """
            ).useAndInvokeAndDiscard(
                prepare = {
                    bindString("id", resource.id)
                    bindString("category", category)
                    bindString("owner", owner)
                }
            )

            if (!accountNow(owner, category, session)) {
                throw RPCException(
                    "Unable to allocate a license. Please make sure you have sufficient funds!",
                    HttpStatusCode.PaymentRequired,
                    ErrorCode.MISSING_COMPUTE_CREDITS.name,
                )
            }
        }

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
        dbConnection.withSession { session ->
            session.prepareStatement(
                """
                    delete from generic_license_instances
                    where id = :id
                """
            ).useAndInvokeAndDiscard(prepare = { bindString("id", resource.id) })

            accountNow(resource.owner.project ?: resource.owner.createdBy, resource.specification.product.category,
                session)
        }
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

    private suspend fun accountNow(owner: String, category: String, ctx: DBContext = dbConnection): Boolean {
        val amountUsed = ctx.withSession { session ->
            var count = 0L
            session.prepareStatement(
                """
                    select count(*)::int8
                    from generic_license_instances
                    where
                        owner = :owner
                        and category = :category
                """
            ).useAndInvoke(
                prepare = {
                    bindString("owner", owner)
                    bindString("category", category)
                },
                readRow = { row -> count = row.getLong(0)!! },
            )
            count
        }

        return reportConcurrentUse(
            walletOwnerFromOwnerString(owner),
            ProductCategoryIdV2(category, providerId),
            amountUsed
        )
    }

    override suspend fun PluginContext.runMonitoringLoopInServerMode() {
        var nextScan = 0L
        while (coroutineContext.isActive) {
            try {
                val now = Time.now()
                if (now > nextScan) {
                    val ownerAndCategory = ArrayList<Pair<String, String>>()
                    val failures = ArrayList<Pair<String, String>>()
                    dbConnection.withSession { session ->
                        try {
                            session.prepareStatement(
                                """
                                    select distinct owner, category
                                    from generic_license_instances
                                """
                            ).useAndInvoke(readRow = { row ->
                                ownerAndCategory.add(Pair(row.getString(0)!!, row.getString(1)!!))
                            })

                            ownerAndCategory.forEachGraal { (owner, category) ->
                                if (!accountNow(owner, category, session)) {
                                    failures.add(Pair(owner, category))
                                }
                            }
                        } catch (ex: Throwable) {
                            log.warn("Caught exception while accounting public links: ${ex.toReadableStacktrace()}")
                        }
                    }

                    failures.forEachGraal { failure ->
                        accountingFailureListeners.forEachGraal { it(failure.first, failure.second) }
                    }

                    nextScan = now + (1000L * 60 * 60)
                }

                delay(30_000)
            } catch (ex: Throwable) {
                log.warn("Caught exception while accounting licenses: ${ex.toReadableStacktrace()}")
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
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
