package dk.sdu.cloud.config

import dk.sdu.cloud.ServerMode
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.file.orchestrator.api.FileType
import dk.sdu.cloud.plugins.*
import dk.sdu.cloud.sql.postgres.EmbeddedPostgres
import dk.sdu.cloud.utils.*
import libc.clib
import java.io.File
import java.nio.file.Files
import kotlin.system.exitProcess

// NOTE(Dan): To understand how this class is loaded, see the note in `Config.kt` of this package.

data class VerifiedConfig(
    val configurationDirectory: String,
    private val serverMode: ServerMode,
    val coreOrNull: Core?,
    val serverOrNull: Server?,
    val pluginsOrNull: Plugins?,
    val rawPluginConfigOrNull: ConfigSchema.Plugins?,
    val productsOrNull: Products?,
    val frontendProxyOrNull: FrontendProxy?
) {
    val core: Core get() = coreOrNull!!
    val server: Server get() = serverOrNull!!
    val plugins: Plugins get() = pluginsOrNull!!
    val rawPluginConfig: ConfigSchema.Plugins get() = rawPluginConfigOrNull!!
    val products: Products get() = productsOrNull!!
    val frontendProxy: FrontendProxy get() = frontendProxyOrNull!!

    data class Core(
        val certificate: String,
        val providerId: String,
        val hosts: Hosts,
        val ipc: Ipc,
        val logs: Logs,
        val launchRealUserInstances: Boolean,
        val allowRootMode: Boolean,
        val developmentMode: Boolean,
        val cors: Cors,
        val disableInsecureFileCheck: Boolean,
        val maintenance: Maintenance,
        val internalBindAddress: String,
    ) {
        data class Hosts(
            val ucloud: Host,
            val self: Host?,
        )

        data class Ipc(
            val directory: String
        )

        data class Logs(
            val directory: String,
            val trace: List<ConfigSchema.Core.Logs.Tracer>,
            val preferStdout: Boolean,
        )

        data class Cors(
            val allowHosts: List<String>,
        )

        data class Maintenance(
            val alwaysAllowAccessFrom: List<String>,
        )
    }

    data class Server(
        val refreshToken: String,
        val network: Network,
        val developmentMode: DevelopmentMode,
        val database: Database,
        val envoy: Envoy,
    ) {
        data class Network(
            val listenAddress: String,
            val listenPort: Int
        )

        data class DevelopmentMode(
            val predefinedUserInstances: List<UserInstance>
        ) {
            data class UserInstance(
                val username: String,
                val userId: Int,
                val port: Int
            )
        }

        data class Database(
            val embeddedDataDirectory: File?,
            val jdbcUrl: String,
            val username: String?,
            val password: String?,
        )

        data class Envoy(
            val executable: String?,
            val directory: String,
            val downstreamTls: Boolean,
            val funceWrapper: Boolean,
            val internalAddressToProvider: String,
            val envoyIsManagedExternally: Boolean,
        )
    }


    data class Plugins(
        val connection: ConnectionPlugin?,
        val projects: ProjectPlugin?,
        val jobs: Map<String, ComputePlugin>,
        val files: Map<String, FilePlugin>,
        val fileCollections: Map<String, FileCollectionPlugin>,
        val ingresses: Map<String, IngressPlugin>,
        val publicIps: Map<String, PublicIPPlugin>,
        val licenses: Map<String, LicensePlugin>,
        val shares: Map<String, SharePlugin>,
        val allocations: Map<ConfigSchema.Plugins.AllocationsProductType, AllocationPlugin>,

        // TODO(Dan): This is a hack to make the NotificationController correctly receive events from the
        //   ConnectionController. I don't have a good solution right now, so we will have to live with this weird
        //   thing.
        val temporary: Temporary = Temporary(),
    ) {
        data class Temporary(
            val onConnectionCompleteHandlers: ArrayList<suspend (ucloudId: String, localId: Int) -> Unit> = ArrayList(),
        )

        fun resourcePlugins(): Iterator<ResourcePlugin<*, *, *, *>> {
            val iterators = arrayOf(
                jobs.values.iterator(),
                files.values.iterator(),
                fileCollections.values.iterator(),
                ingresses.values.iterator(),
                publicIps.values.iterator(),
                licenses.values.iterator(),
                shares.values.iterator(),
            )
            var idx = 0

            return object : Iterator<ResourcePlugin<*, *, *, *>> {
                override fun hasNext(): Boolean {
                    if (idx !in iterators.indices) return false
                    val currHasNext = iterators[idx].hasNext()
                    if (!currHasNext) {
                        idx++
                        if (idx !in iterators.indices) return false
                        return iterators[idx].hasNext()
                    }

                    return true
                }

                override fun next(): ResourcePlugin<*, *, *, *> {
                    return iterators.getOrNull(idx)?.next() ?: throw NoSuchElementException()
                }
            }
        }

        sealed class ProductMatcher {
            abstract fun match(product: ProductReferenceWithoutProvider): Int

            data class Product(val category: String, val id: String) : ProductMatcher() {
                override fun match(product: ProductReferenceWithoutProvider): Int {
                    return if (product.category == category && product.id == id) {
                        3
                    } else {
                        -1
                    }
                }
            }

            data class Category(val category: String) : ProductMatcher() {
                override fun match(product: ProductReferenceWithoutProvider): Int {
                    return if (product.category == category) 2
                    else -1
                }
            }

            data class PrefixAny(val prefix: String) : ProductMatcher() {
                override fun match(product: ProductReferenceWithoutProvider): Int {
                    if (product.id.startsWith(prefix)) return 3
                    if (product.category.startsWith(prefix)) return 2
                    return -1
                }
            }

            object Any : ProductMatcher() {
                override fun match(product: ProductReferenceWithoutProvider): Int = 1
            }

            companion object {
                fun parse(pattern: String): VerifyResult<ProductMatcher> {
                    val trimmed = pattern.trim()

                    if (trimmed.contains("\n")) {
                        return VerifyResult.Error("Product matcher cannot contain new lines.")
                    }

                    if (trimmed == "*") return VerifyResult.Ok(Any)
                    if (trimmed.endsWith("*")) return VerifyResult.Ok(PrefixAny(trimmed.removeSuffix("*")))

                    return if (trimmed.contains("/")) {
                        val category = trimmed.substringBefore('/').trim()
                        val id = trimmed.substringAfter('/').trim()

                        if (id.contains("/")) {
                            return VerifyResult.Error("Product matcher contains too many slashes.")
                        }

                        VerifyResult.Ok(Product(category, id))
                    } else {
                        VerifyResult.Ok(Category(trimmed))
                    }
                }
            }
        }
    }

    data class Products(
        val compute: Map<String, List<Product.Compute>>? = null,
        val storage: Map<String, List<Product.Storage>>? = null,
        val ingress: Map<String, List<Product.Ingress>>? = null,
        val publicIp: Map<String, List<Product.NetworkIP>>? = null,
        val license: Map<String, List<Product.License>>? = null,
    ) {
        var productsUnknownToUCloud: Set<Product> = emptySet()

        val allProducts: List<Product>
            get() =
                (compute?.values?.toList()?.flatten() ?: emptyList()) +
                        (storage?.values?.toList()?.flatten() ?: emptyList()) +
                        (ingress?.values?.toList()?.flatten() ?: emptyList()) +
                        (publicIp?.values?.toList()?.flatten() ?: emptyList()) +
                        (license?.values?.toList()?.flatten() ?: emptyList())
    }

    data class FrontendProxy(
        val sharedSecret: String,
        val remote: Host
    )

    fun shouldRunUserCode(): Boolean {
        return serverMode == ServerMode.User || (serverMode == ServerMode.Server && !core.launchRealUserInstances)
    }

    fun shouldRunServerCode(): Boolean {
        return serverMode == ServerMode.Server
    }

    fun shouldRunProxyCode(): Boolean {
        return serverMode == ServerMode.FrontendProxy
    }

    fun shouldRunAnyPluginCode(): Boolean {
        return serverMode is ServerMode.Plugin
    }

    fun rawServerMode(): ServerMode = serverMode
}

// NOTE(Dan): Make sure you understand `loadConfiguration()` of `Config.kt` before you read this function. This
// function takes, as input the output of `loadConfiguration()`. The job is then to read the raw configuration from
// the user, and determine if it is valid and fetch additional information if required. This usually involves checking
// if additional files exists, making sure hosts are valid and so on. Once this function is done, then the
// configuration should be valid and no plugins/other code should crash as a result of bad configuration.
fun verifyConfiguration(mode: ServerMode, config: ConfigSchema): VerifiedConfig {
    fun <T : Product> mapProducts(p: Map<String, List<ConfigProduct<T>>>?): Map<String, List<T>> {
        return p?.mapValues { (categoryName, products) ->
            products.map { it.toProduct(categoryName, config.core!!.providerId) }
        } ?: emptyMap()
    }

    run {
        // Verify that sections required by the mode are available.
        val disableInsecureCheck =
            config.core?.disableInsecureFileCheckIUnderstandThatThisIsABadIdeaButSomeDevEnvironmentsAreBuggy == true &&
                config.core.developmentMode == true

        if (config.core == null) missingFile(config, ConfigSchema.FILE_CORE) // Required for all

        when (mode) {
            ServerMode.FrontendProxy -> {
                if (config.frontendProxy == null) missingFile(config, ConfigSchema.FILE_FRONTEND_PROXY)
                if (config.server != null) insecureFile(config, ConfigSchema.FILE_SERVER, disableInsecureCheck)
            }

            is ServerMode.Plugin -> {
                // No validation required
            }

            ServerMode.Server -> {
                if (config.plugins == null) missingFile(config, ConfigSchema.FILE_PLUGINS)
                if (config.server == null) missingFile(config, ConfigSchema.FILE_SERVER)
                if (config.products == null) missingFile(config, ConfigSchema.FILE_PRODUCTS)
            }

            ServerMode.User -> {
                if (config.server != null) insecureFile(config, ConfigSchema.FILE_SERVER, disableInsecureCheck)
                if (config.plugins == null) missingFile(config, ConfigSchema.FILE_PLUGINS)
                if (config.products == null) missingFile(config, ConfigSchema.FILE_PRODUCTS)
                if (config.frontendProxy != null) insecureFile(config, ConfigSchema.FILE_FRONTEND_PROXY, disableInsecureCheck)
            }
        }
    }

    val core: VerifiedConfig.Core = run {
        // Verify the core section
        val core = config.core!!
        val baseReference = ConfigurationReference(
            config.configurationDirectory + "/" + ConfigSchema.FILE_CORE,
        )

        val certificate = run {
            val certPath = "${config.configurationDirectory}/ucloud_crt.pem"
            try {
                val certText = File(certPath).readText().trim()

                val lineRegex = Regex("[a-zA-Z0-9+/=,-_]+")
                certText.lines().drop(1).dropLast(1).forEach { line ->
                    if (!line.matches(lineRegex)) {
                        error("Invalid certificate")
                    }
                }

                certText
            } catch (ex: Throwable) {
                sendTerminalMessage {
                    red { bold { line("Configuration error!") } }
                    line("Could not load certificate used for authentication with UCloud.")
                    line()

                    inline("We expected to be able to find the certificate here: ")
                    code { line(certPath) }
                    line()

                    line(
                        "The ceritificate is issued by UCloud during the registration process. " +
                                "You can try downloading a new certificate from UCloud at: "
                    )
                    code { line("${core.hosts.ucloud}/app/providers") }
                }
                exitProcess(1)
            }
        }


        // NOTE(Dan): Provider ID is verified later together with products
        val providerId = core.providerId

        val hosts = run {
            val ucloud = handleVerificationResultStrict(
                verifyHost(
                    core.hosts.ucloud,
                    baseReference
                )
            )
            val self = if (core.hosts.self != null) {
                handleVerificationResultWeak(
                    verifyHost(
                        core.hosts.self,
                        baseReference
                    )
                ) ?: core.hosts.self
            } else {
                null
            }

            VerifiedConfig.Core.Hosts(ucloud, self)
        }

        val ipc = run {
            val directory = handleVerificationResultStrict(
                verifyFile(
                    core.ipc?.directory ?: "/var/run/ucloud",
                    FileType.DIRECTORY,
                    baseReference,
                    requireWriteAccess = mode == ServerMode.Server
                )
            )

            VerifiedConfig.Core.Ipc(directory)
        }

        val logs = run {
            val directory = handleVerificationResultStrict(
                verifyFile(
                    core.logs?.directory ?: "/var/log/ucloud",
                    FileType.DIRECTORY,
                    baseReference,
                    requireWriteAccess = true
                )
            )

            val trace = config.core.logs?.trace ?: emptyList()

            val preferStdout = core.logs?.preferStdout ?: false
            if (preferStdout && core.launchRealUserInstances) {
                emitError("core.logs.preferStdout cannot be true when core.launchRealUserInstances is also true " +
                        "(logs would be discarded)")
            }

            VerifiedConfig.Core.Logs(directory, trace, core.logs?.preferStdout ?: false)
        }

        val cors = run {
            val allowHosts = core.cors?.allowHosts ?: emptyList()
            VerifiedConfig.Core.Cors(allowHosts)
        }

        if (core.launchRealUserInstances && core.allowRootMode) {
            emitError("core.allowRootMode is only allowed if core.launchRealUserInstances = false")
        }

        val maintenance = VerifiedConfig.Core.Maintenance(
            core.maintenance?.alwaysAllowAccessFrom ?: emptyList()
        )

        val internalBindAddress = core.internalBindAddress ?: "127.0.0.1"

        VerifiedConfig.Core(
            certificate,
            providerId,
            hosts,
            ipc,
            logs,
            core.launchRealUserInstances,
            core.allowRootMode,
            core.developmentMode ?: (core.hosts.ucloud.host == "backend"),
            cors,
            core.disableInsecureFileCheckIUnderstandThatThisIsABadIdeaButSomeDevEnvironmentsAreBuggy && core.developmentMode == true,
            maintenance,
            internalBindAddress
        )
    }

    val server: VerifiedConfig.Server? = if (config.server == null) {
        null
    } else {
        val baseReference = ConfigurationReference(
            config.configurationDirectory + "/" + ConfigSchema.FILE_SERVER,
        )

        val refreshToken = run {
            val tok = config.server.refreshToken.trim()

            if (tok.isBlank() || tok.length < 10 || tok.contains("\n")) {
                emitError(
                    VerifyResult.Error<Unit>(
                        "The refresh token supplied for the server does not look valid.",
                        baseReference
                    )
                )
            }

            tok
        }

        val network: VerifiedConfig.Server.Network = run {
            VerifiedConfig.Server.Network(
                config.server.network?.listenAddress ?: "0.0.0.0",
                config.server.network?.listenPort ?: 8889
            )
        }

        run {
            if (!network.listenAddress.matches(Regex("""\d\d?\d?\.\d\d?\d?\.\d\d?\d?\.\d\d?\d?"""))) {
                emitWarning(
                    VerifyResult.Warning<Unit>(
                        "The listen address specified for the server '${network.listenAddress}' does not look " +
                                "like a valid IPv4 address. The integration module will attempt to use this address " +
                                "regardless.",
                        baseReference
                    )
                )
            }
            if (network.listenPort <= 0 || network.listenPort >= 65536) {
                emitError(
                    VerifyResult.Error<Unit>(
                        "The listen port specified for the server '${network.listenPort}' is not valid.",
                        baseReference
                    )
                )
            }
        }

        val developmentMode: VerifiedConfig.Server.DevelopmentMode = run {
            if (config.server.developmentMode == null) {
                VerifiedConfig.Server.DevelopmentMode(emptyList())
            } else {
                val portsInUse = HashSet<Int>()
                val usernamesInUse = HashSet<String>()

                val instances = config.server.developmentMode.predefinedUserInstances.mapIndexed { idx, instance ->
                    val username = instance.username.trim()
                    val userId = instance.userId
                    val port = instance.port

                    val ref = baseReference

                    if (username.isBlank()) emitError("Username cannot be blank", ref)
                    if (username.contains("\n")) emitError("Username cannot contain newlines", ref)
                    if (username in usernamesInUse) {
                        emitError("Username '$username' is already in use by a different instance.", ref)
                    }

                    if (userId < 0) emitError("Invalid unix user id (UID)", ref)
                    if (userId == 0) emitError(
                        "Invalid unix user id (UID). It is not possible " +
                                "to run the integration module as root.", ref
                    )

                    if (port in portsInUse) {
                        emitError("Port $port is already in use by a different instance.", ref)
                    }

                    if (port == network.listenPort) {
                        emitError("Development instance is using the same port as the server itself ($port).", ref)
                    }

                    if (port <= 0 || port >= 65536) {
                        emitError("Invalid port specified ($port).", ref)
                    }

                    portsInUse.add(port)
                    usernamesInUse.add(username)

                    VerifiedConfig.Server.DevelopmentMode.UserInstance(username, userId, port)
                }

                VerifiedConfig.Server.DevelopmentMode(instances)
            }
        }

        val database: VerifiedConfig.Server.Database = if (mode == ServerMode.Server) {
            val database = config.server.database ?: ConfigSchema.Server.Database.Embedded(
                File(config.configurationDirectory, "pgsql").absolutePath
            )

            when (database) {
                is ConfigSchema.Server.Database.Embedded -> {
                    val workDir = File(database.directory)
                    val staticPassword = database.password
                    if (staticPassword != null) {
                        workDir.mkdir()
                        File(workDir, EmbeddedPostgres.POSTGRES_PASSWORD_FILE).writeText(staticPassword)
                    }

                    workDir.mkdirs()
                    verifyFile(workDir.absolutePath, FileType.DIRECTORY)
                    val dataDir = File(workDir, "data")

                    val embeddedPostgres = EmbeddedPostgres.builder().apply {
                        setCleanDataDirectory(false)
                        setDataDirectory(dataDir.also { it.mkdirs() })
                        setOverrideWorkingDirectory(workDir)
                        setUseUnshare(clib.getuid() == 0)
                        setHost(database.host)
                        setPort(database.port)
                    }.start()

                    VerifiedConfig.Server.Database(
                        dataDir,
                        embeddedPostgres.getJdbcUrl("postgres", "postgres"),
                        EmbeddedPostgres.PG_SUPERUSER,
                        embeddedPostgres.password,
                    )
                }

                is ConfigSchema.Server.Database.External -> {
                    VerifiedConfig.Server.Database(
                        null,
                        buildString {
                            append("jdbc:postgresql://")
                            append(database.hostname)
                            if (database.port != null) {
                                append(':')
                                append(database.port)
                            }
                            append('/')
                            append(database.database)
                        },
                        database.username,
                        database.password,
                    )
                }
            }
        } else {
            VerifiedConfig.Server.Database(null, "", null, null)
        }

        val envoy: VerifiedConfig.Server.Envoy = run {
            val executable = config.server.envoy?.executable
            val directory = config.server.envoy?.directory ?: "/var/run/ucloud/envoy"
            val downstreamTls = config.server.envoy?.downstreamTls ?: false
            val funceWrapper = config.server.envoy?.funceWrapper ?: true
            val internalAddressToProvider = config.server.envoy?.internalAddressToProvider ?: "127.0.0.1"
            val envoyIsManagedExternally = config.server.envoy?.envoyIsManagedExternally ?: false
            VerifiedConfig.Server.Envoy(
                executable,
                directory,
                downstreamTls,
                funceWrapper,
                internalAddressToProvider,
                envoyIsManagedExternally
            )
        }

        VerifiedConfig.Server(refreshToken, network, developmentMode, database, envoy)
    }

    val products: VerifiedConfig.Products? = run {
        if (config.products == null) {
            null
        } else {
            // NOTE(Dan): Products are verified later (against UCloud/Core)
            VerifiedConfig.Products(
                mapProducts(config.products.compute),
                mapProducts(config.products.storage),
                mapProducts(config.products.ingress),
                mapProducts(config.products.publicIps),
                mapProducts(config.products.licenses),
            )
        }
    }

    val frontendProxy: VerifiedConfig.FrontendProxy? = run {
        if (config.frontendProxy == null) {
            null
        } else {
            val baseReference = ConfigurationReference(
                config.configurationDirectory + "/" + ConfigSchema.FILE_FRONTEND_PROXY,
            )

            val remote = if (mode == ServerMode.FrontendProxy) {
                handleVerificationResultStrict(
                    verifyHost(
                        config.frontendProxy.remote,
                        baseReference
                    )
                )
            } else {
                handleVerificationResultWeak(
                    verifyHost(
                        config.frontendProxy.remote,
                        baseReference
                    )
                ) ?: config.frontendProxy.remote
            }

            val sharedSecret = config.frontendProxy.sharedSecret.trim()
            if (sharedSecret.isBlank() || sharedSecret.contains("\n")) {
                emitError(
                    "Shared secret for frontend proxy is not valid.",
                    baseReference
                )
            }

            VerifiedConfig.FrontendProxy(sharedSecret, remote)
        }
    }

    @Suppress("UNCHECKED_CAST") val plugins: VerifiedConfig.Plugins? = run {
        if (config.plugins == null) {
            null
        } else {
            val productReference = ConfigurationReference(
                config.configurationDirectory + "/" + ConfigSchema.FILE_PRODUCTS,
            )

            val pluginReference = ConfigurationReference(
                config.configurationDirectory + "/" + ConfigSchema.FILE_PLUGINS,
            )

            val connection: ConnectionPlugin? = if (config.plugins.connection == null) {
                null
            } else {
                loadPlugin(config.plugins.connection, core.launchRealUserInstances) as ConnectionPlugin
            }

            val projects: ProjectPlugin? = if (config.plugins.projects == null) {
                null
            } else {
                loadPlugin(config.plugins.projects, core.launchRealUserInstances) as ProjectPlugin
            }

            val allocations: Map<ConfigSchema.Plugins.AllocationsProductType, AllocationPlugin> =
                if (config.plugins.allocations == null) {
                    emptyMap()
                } else {
                    val result = HashMap<ConfigSchema.Plugins.AllocationsProductType, AllocationPlugin>()
                    for ((productType, cfg) in config.plugins.allocations) {
                        result[productType] = loadPlugin(cfg, core.launchRealUserInstances) as AllocationPlugin
                    }
                    result
                }

            @Suppress("unchecked_cast")
            val jobs: Map<String, ComputePlugin> = loadProductBasedPlugins(
                "jobs",
                mapProducts(config.products?.compute),
                config.plugins.jobs ?: emptyMap(),
                productReference,
                pluginReference,
                core.launchRealUserInstances
            ) as Map<String, ComputePlugin>

            @Suppress("unchecked_cast")
            val files: Map<String, FilePlugin> = loadProductBasedPlugins(
                "files",
                mapProducts(config.products?.storage),
                config.plugins.files ?: emptyMap(),
                productReference,
                pluginReference,
                core.launchRealUserInstances
            ) as Map<String, FilePlugin>

            @Suppress("unchecked_cast")
            val fileCollections: Map<String, FileCollectionPlugin> = loadProductBasedPlugins(
                "fileCollections",
                mapProducts(config.products?.storage),
                config.plugins.fileCollections ?: emptyMap(),
                productReference,
                pluginReference,
                core.launchRealUserInstances
            ) as Map<String, FileCollectionPlugin>

            @Suppress("unchecked_cast")
            val ingresses: Map<String, IngressPlugin> = loadProductBasedPlugins(
                "ingresses",
                mapProducts(config.products?.ingress),
                config.plugins.ingresses ?: emptyMap(),
                productReference,
                pluginReference,
                core.launchRealUserInstances
            ) as Map<String, IngressPlugin>

            @Suppress("unchecked_cast")
            val publicIps: Map<String, PublicIPPlugin> = loadProductBasedPlugins(
                "publicIps",
                mapProducts(config.products?.publicIps),
                config.plugins.publicIps ?: emptyMap(),
                productReference,
                pluginReference,
                core.launchRealUserInstances
            ) as Map<String, PublicIPPlugin>

            @Suppress("unchecked_cast")
            val licenses: Map<String, LicensePlugin> = loadProductBasedPlugins(
                "licenses",
                mapProducts(config.products?.licenses),
                config.plugins.licenses ?: emptyMap(),
                productReference,
                pluginReference,
                core.launchRealUserInstances
            ) as Map<String, LicensePlugin>

            @Suppress("unchecked_cast")
            val shares: Map<String, SharePlugin> = loadProductBasedPlugins(
                "shares",
                mapProducts(config.products?.storage),
                config.plugins.shares ?: emptyMap(),
                productReference,
                pluginReference,
                core.launchRealUserInstances,
                requireProductAllocation = false
            ) as Map<String, SharePlugin>

            VerifiedConfig.Plugins(
                connection, projects, jobs, files, fileCollections, ingresses, publicIps,
                licenses, shares, allocations
            )
        }
    }

    return VerifiedConfig(
        config.configurationDirectory,
        mode,
        core,
        server,
        plugins,
        config.plugins,
        products,
        frontendProxy
    )
}

// Plugin loading
class PluginLoadingException(val pluginTitle: String, message: String) : RuntimeException(message)

private fun <Cfg : Any> loadPlugin(config: Cfg, realUserMode: Boolean): Plugin<Cfg> {
    val result = instantiatePlugin(config)
    if (!result.supportsRealUserMode() && realUserMode) {
        throw PluginLoadingException(
            result.pluginTitle,
            "launchRealUserInstances is true but not supported for this plugin: ${result.pluginTitle}"
        )
    }
    if (!result.supportsServiceUserMode() && !realUserMode) {
        throw PluginLoadingException(
            result.pluginTitle,
            "launchRealUserInstances is false but not supported for this plugin: ${result.pluginTitle}"
        )
    }
    result.configure(config)
    return result
}

private fun <Cfg : ConfigSchema.Plugins.ProductBased> loadProductBasedPlugins(
    type: String,
    products: Map<String, List<Product>>,
    plugins: Map<String, Cfg>,
    productRef: ConfigurationReference,
    pluginRef: ConfigurationReference,
    realUserMode: Boolean,
    requireProductAllocation: Boolean = true,
): Map<String, Plugin<Cfg>> {
    val result = HashMap<String, Plugin<Cfg>>()
    val relevantProducts = products.entries.flatMap { (category, products) ->
        products.map { ProductReferenceWithoutProvider(it.name, category) }
    }

    val partitionedProducts = HashMap<String, List<ProductReferenceWithoutProvider>>()
    for (product in relevantProducts) {
        var bestScore = -1
        var bestMatch: String? = null
        for ((id, pluginConfig) in plugins) {
            val matcher = handleVerificationResultStrict(
                VerifiedConfig.Plugins.ProductMatcher.parse(pluginConfig.matches)
            )

            val score = matcher.match(product)
            if (score == bestScore && score >= 0 && bestMatch != null) {
                emitError(
                    "Could not allocate product '$product' to a plugin. Both '$id' and '$bestMatch' " +
                            "target the product with identical specificity. Resolve this conflict by " +
                            "creating a more specific matcher.",
                    pluginRef
                )
            }

            if (score > bestScore) {
                bestScore = score
                bestMatch = id
            }
        }

        if (bestMatch == null) {
            if (requireProductAllocation) {
                emitWarning(
                    "Could not allocate product '$product' to a plugin ($type). No plugins match it, " +
                            "the integration module will ignore all requests for this product!",
                    productRef
                )
            }
        } else {
            partitionedProducts[bestMatch] = (partitionedProducts[bestMatch] ?: emptyList()) + product
        }
    }

    for ((id, pluginConfig) in plugins) {
        val pluginProducts = partitionedProducts[id] ?: emptyList()
        val plugin = instantiatePlugin(pluginConfig)
        if (plugin is ResourcePlugin<*, *, *, *>) {
            plugin.pluginName = id
            plugin.productAllocation = pluginProducts
            if (pluginProducts.isEmpty()) {
                emitWarning(
                    "Could not allocate any products to the plugin '$id' ($type). This plugin will never run!",
                    pluginRef
                )
            }
        }
        if (!plugin.supportsRealUserMode() && realUserMode) {
            throw PluginLoadingException(
                plugin.pluginTitle,
                "launchRealUserInstances is true but not supported for this plugin: ${plugin.pluginTitle} ($type)"
            )
        }
        if (!plugin.supportsServiceUserMode() && !realUserMode) {
            throw PluginLoadingException(
                plugin.pluginTitle,
                "launchRealUserInstances is false but not supported for this plugin: ${plugin.pluginTitle} ($type)"
            )
        }

        plugin.configure(pluginConfig)
        result[id] = plugin
    }

    return result
}

// End-user feedback
private fun missingFile(config: ConfigSchema, file: String): Nothing {
    sendTerminalMessage {
        bold { red { inline("Missing file! ") } }
        code {
            inline(config.configurationDirectory)
            inline("/")
            line(file)
        }
        line()
        line("This file is required when running the ingration module in this mode. Please make sure that the file ")
        line("exists and is readable by the appropiate users. We refer to the documentation for more information.")

        line()
        bold { inline("NOTE: ") }
        line(
            "The integration module requires precise file names and extensions. Make sure the file exists exactly " +
                    "as specified above"
        )

    }
    exitProcess(1)
}

private fun insecureFile(config: ConfigSchema, file: String, disabled: Boolean) {
    if (disabled) {
        sendTerminalMessage {
            bold { yellow { inline("Insecure file! ") } }
            code {
                inline(config.configurationDirectory)
                inline("/")
                line(file)
            }
            line()
            line("This file is not supposed to be readable in the configuration, yet it was. ")
            line("It appears you might be running a some weird dev environment. We are allowing you to continue")
            line("with this insecure file.")
        }
    } else {
        sendTerminalMessage {
            bold { red { inline("Insecure file! ") } }
            code {
                inline(config.configurationDirectory)
                inline("/")
                line(file)
            }
            line()
            line("This file is not supposed to be readable in the configuration, yet it was. ")
            line("We refer to the documentation for more information about this error.")
        }

        exitProcess(1)
    }
}

private fun emitWarning(warning: String, ref: ConfigurationReference? = null) {
    emitWarning(VerifyResult.Warning<Unit>(warning, ref))
}

private fun emitWarning(result: VerifyResult.Warning<*>) {
    sendTerminalMessage {
        yellow { bold { inline("Configuration warning! ") } }
        if (result.ref != null) code { line(result.ref.file) }

        line(result.message)
        line()
    }
}

private fun emitError(error: String, ref: ConfigurationReference? = null): Nothing {
    emitError(VerifyResult.Error<Unit>(error, ref))
}

private fun emitError(result: VerifyResult.Error<*>): Nothing {
    sendTerminalMessage {
        red { bold { inline("Configuration error! ") } }
        if (result.ref != null) code { line(result.ref.file) }

        line(result.message)
        line()
    }
    exitProcess(1)
}

// General verification procedures
data class ConfigurationReference(
    val file: String,
)

sealed class VerifyResult<T> {
    data class Ok<T>(val result: T) : VerifyResult<T>()
    data class Warning<T>(val message: String, val ref: ConfigurationReference? = null) : VerifyResult<T>()
    data class Error<T>(val message: String, val ref: ConfigurationReference? = null) : VerifyResult<T>()
}

fun <T> handleVerificationResultStrict(result: VerifyResult<T>): T {
    return handleVerificationResult(result, errorsAreWarnings = false)!!
}

fun <T> handleVerificationResultWeak(result: VerifyResult<T>): T? {
    return handleVerificationResult(result, errorsAreWarnings = true)
}

private fun <T> handleVerificationResult(
    result: VerifyResult<T>,
    errorsAreWarnings: Boolean = false
): T? {
    return when (result) {
        is VerifyResult.Ok -> result.result

        is VerifyResult.Error -> {
            if (!errorsAreWarnings) {
                emitError(result)
            } else {
                emitWarning(VerifyResult.Warning<T>(result.message, result.ref))
                null
            }
        }

        is VerifyResult.Warning -> {
            emitWarning(result)
            null
        }
    }
}

fun verifyHost(host: Host, ref: ConfigurationReference? = null): VerifyResult<Host> {
    // TODO()
    /*
    memScoped {
        val hints = alloc<addrinfo>()
        memset(hints.ptr, 0, sizeOf<addrinfo>().toULong())
        hints.ai_family = AF_UNSPEC
        hints.ai_socktype = SOCK_STREAM // TCP, please.
        hints.ai_flags = 0
        hints.ai_protocol = 0

        // Next, we use these hints to retrieve information about our requested host at the specified port
        val result = allocPointerTo<addrinfo>()
        if (getaddrinfo(host.host, host.port.toString(), hints.ptr, result.ptr) != 0) {
            return VerifyResult.Error(
                "The following host appears to be invalid: $host. Validate that the host name is correct and that " +
                    "you are able to connect to it.",
                ref
            )
        }

        freeaddrinfo(result.value)
    }
     */
    return VerifyResult.Ok(host)
}

private fun verifyFile(
    path: String,
    typeRequirement: FileType?,
    ref: ConfigurationReference? = null,
    requireWriteAccess: Boolean = false,
): VerifyResult<String> {
    val fileExists = fileExists(path)
    val fileIsDirectory = fileExists && fileIsDirectory(path)
    val isOk = when (typeRequirement) {
        FileType.FILE -> fileExists && !fileIsDirectory
        FileType.DIRECTORY -> fileExists && fileIsDirectory
        else -> fileExists
    }

    if (!isOk) {
        return when (typeRequirement) {
            FileType.DIRECTORY -> VerifyResult.Error("No directory exists at '$path'", ref)
            null -> VerifyResult.Error("No file exists at '$path'", ref)
            else -> {
                VerifyResult.Error("No file exists at '$path'", ref)
            }
        }
    } else {
        if (requireWriteAccess) {
            val hasWrite = Files.isWritable(File(path).toPath())
            if (!hasWrite) {
                return VerifyResult.Error("Unable to write to '$path'", ref)
            }
        }

        return VerifyResult.Ok(path)
    }
}
