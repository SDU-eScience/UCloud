package dk.sdu.cloud.config

import dk.sdu.cloud.ProductReferenceWithoutProvider
import dk.sdu.cloud.ServerMode
import dk.sdu.cloud.file.orchestrator.api.FileType
import dk.sdu.cloud.plugins.*
import dk.sdu.cloud.utils.*
import kotlin.system.exitProcess

// NOTE(Dan): To understand how this class is loaded, see the note in `Config.kt` of this package.

data class VerifiedConfig(
    val coreOrNull: Core?,
    val serverOrNull: Server?,
    val pluginsOrNull: Plugins?,
    val productsOrNull: Products?,
    val frontendProxyOrNull: FrontendProxy?
) {
    val core: Core get() = coreOrNull!!
    val server: Server get() = serverOrNull!!
    val plugins: Plugins get() = pluginsOrNull!!
    val products: Products get() = productsOrNull!!
    val frontendProxy: FrontendProxy get() = frontendProxyOrNull!!

    data class Core(
        val providerId: String,
        val hosts: Hosts,
        val ipc: Ipc,
        val logs: Logs,
    ) {
        data class Hosts(
            val ucloud: Host, val self: Host?,
        )

        data class Ipc(
            val directory: String
        )

        data class Logs(
            val directory: String
        )
    }

    data class Server(
        val certificate: String,
        val refreshToken: String,
        val network: Network,
        val developmentMode: DevelopmentMode
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
    }

    data class Plugins(
        val connection: ConnectionPlugin?,
        val projects: ProjectPlugin?,
        val jobs: Map<String, ComputePlugin>,
        val files: Map<String, FilePlugin>,
        val fileCollections: Map<String, FileCollectionPlugin>
    ) {
        interface ProductBased {
            val matches: ProductMatcher
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
        val compute: Map<String, List<String>>? = null,
        val storage: Map<String, List<String>>? = null,
    )

    data class FrontendProxy(
        val sharedSecret: String,
        val remote: Host
    )
}

fun verifyConfiguration(mode: ServerMode, config: ConfigSchema): VerifiedConfig {
    run {
        // Verify that sections required by the mode are available.

        if (config.core == null) missingFile(ConfigSchema.FILE_CORE) // Required for all

        when (mode) {
            ServerMode.FrontendProxy -> {
                if (config.frontendProxy == null) missingFile(ConfigSchema.FILE_FRONTEND_PROXY)
                if (config.server != null) insecureFile(ConfigSchema.FILE_SERVER)
            }

            is ServerMode.Plugin -> {
                // No validation required
            }

            ServerMode.Server -> {
                if (config.plugins == null) missingFile(ConfigSchema.FILE_PLUGINS)
                if (config.server == null) missingFile(ConfigSchema.FILE_SERVER)
                if (config.products == null) missingFile(ConfigSchema.FILE_PRODUCTS)
            }

            ServerMode.User -> {
                if (config.server != null) insecureFile(ConfigSchema.FILE_SERVER)
                if (config.plugins == null) missingFile(ConfigSchema.FILE_PLUGINS)
                if (config.products == null) missingFile(ConfigSchema.FILE_PRODUCTS)
                if (config.frontendProxy != null) insecureFile(ConfigSchema.FILE_FRONTEND_PROXY)
            }
        }
    }

    val core: VerifiedConfig.Core = run {
        // Verify the core section
        val core = config.core!!
        val baseReference = ConfigurationReference(
            config.configurationDirectory + "/" + ConfigSchema.FILE_CORE, 
            core.yamlDocument, 
            YamlLocationReference(0, 0),
        )

        // NOTE(Dan): Provider ID is verified later together with products
        val providerId = core.providerId

        val hosts = run {
            val ucloud = handleVerificationResultStrict(verifyHost(core.hosts.ucloud))
            val self = if (core.hosts.self != null) {
                handleVerificationResultWeak(verifyHost(core.hosts.self)) ?: core.hosts.self
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
                    baseReference.useLocationAndProperty(core.ipc?.tag, "ipc/directory")
                )
            )

            VerifiedConfig.Core.Ipc(directory)
        }

        val logs = run {
            val directory = handleVerificationResultStrict(
                verifyFile(
                    core.logs?.directory ?: "/var/log/ucloud", 
                    FileType.DIRECTORY,
                    baseReference.useLocationAndProperty(core.logs?.tag, property = "logs/directory")
                )
            )

            VerifiedConfig.Core.Logs(directory)
        }

        VerifiedConfig.Core(providerId, hosts, ipc, logs)
    }

    val server: VerifiedConfig.Server? = if (config.server == null) {
        null
    } else {
        val baseReference = ConfigurationReference(
            config.configurationDirectory + "/" + ConfigSchema.FILE_CORE, 
            config.server.yamlDocument, 
            YamlLocationReference(0, 0),
        )

        val refreshToken = run {
            val tok = config.server.refreshToken.value.trim()

            if (tok.isBlank() || tok.length < 10 || tok.contains("\n")) {
                emitError(
                    VerifyResult.Error<Unit>(
                        "The refresh token supplied for the server does not look valid.",
                        baseReference.useLocationAndProperty(config.server.refreshToken.tag, "refreshToken")
                    )
                )
            }

            tok
        }

        val certificate = run {
            val certPath = "${config.configurationDirectory}/ucloud_crt.pem"
            try {
                val certText = normalizeCertificate(NativeFile.open(certPath, readOnly = true).readText())

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

                    line("The ceritificate is issed by UCloud during the registration process. " +
                        "You can try downloading a new certificate from UCloud at: ")
                    code { line("${core.hosts.ucloud}/app/providers") }
                }
                exitProcess(1)
            }
        }

        val network: VerifiedConfig.Server.Network = run {
            VerifiedConfig.Server.Network(
                config.server.network?.listenAddress ?: "127.0.0.1",
                config.server.network?.listenPort ?: 42000
            )
        }

        run {
            if (!network.listenAddress.matches(Regex("""\d\d?\d?\.\d\d?\d?\.\d\d?\d?\.\d\d?\d?"""))) {
                emitWarning("The listen address specified for the server '${network.listenAddress}' does not look " +
                    "like a valid IPv4 address. The integration module will attempt to use this address regardless.")
            }
            if (network.listenPort <= 0 || network.listenPort >= 65536) {
                emitError("The listen port specified for the server '${network.listenPort}' is not valid.")
            }
        }

        val developmentMode: VerifiedConfig.Server.DevelopmentMode = run {
            if (config.server.developmentMode == null) {
                VerifiedConfig.Server.DevelopmentMode(emptyList())
            } else {
                val portsInUse = HashSet<Int>()

                val instances = config.server.developmentMode.predefinedUserInstances.mapIndexed { idx, instance ->
                    val username = instance.username.trim()
                    val userId = instance.userId
                    val port = instance.port

                    val path = "server/developmentMode/predefinedUserInstances[$idx]"

                    if (username.isBlank()) emitError("$path has a blank username")
                    if (username.contains("\n")) emitError("$path has an invalid username with newlines")

                    if (userId < 0) emitError("$path has an invalid unix user id (UID)")
                    if (userId == 0) emitError("$path has an invalid unix user id (UID). It is not possible " +
                        "to run the integration module as root.")

                    if (port in portsInUse) {
                        emitError("$path is using a port $port which is already in use.")
                    }

                    if (port == network.listenPort) {
                        emitError("$path is using the same port as the server itself ($port).")
                    }

                    portsInUse.add(port)

                    VerifiedConfig.Server.DevelopmentMode.UserInstance(username, userId, port)
                }

                VerifiedConfig.Server.DevelopmentMode(instances)
            }
        }

        VerifiedConfig.Server(certificate, refreshToken, network, developmentMode)
    }

    val products: VerifiedConfig.Products? = run {
        if (config.products == null) {
            null
        } else {
            // NOTE(Dan): Products are verified later (against UCloud/Core)
            VerifiedConfig.Products(config.products.compute, config.products.storage)
        }
    }

    val frontendProxy: VerifiedConfig.FrontendProxy? = run {
        if (config.frontendProxy == null) {
            null
        } else {
            val remote = if (mode == ServerMode.FrontendProxy) {
                handleVerificationResultStrict(verifyHost(config.frontendProxy.remote))
            } else {
                handleVerificationResultWeak(verifyHost(config.frontendProxy.remote)) ?: config.frontendProxy.remote
            }

            val sharedSecret = config.frontendProxy.sharedSecret.trim()
            if (sharedSecret.isBlank() || sharedSecret.contains("\n")) {
                emitError("Shared secret for frontend proxy is not valid.")
            }

            VerifiedConfig.FrontendProxy(sharedSecret, remote)
        }
    }

    val plugins: VerifiedConfig.Plugins? = run {
        if (config.plugins == null) {
            null
        } else {
            val connection: ConnectionPlugin? = if (config.plugins.connection == null) {
                null
            } else {
                loadPlugin(config.plugins.connection) as ConnectionPlugin
            }

            val projects: ProjectPlugin? = if (config.plugins.projects == null) {
                null
            } else {
                loadPlugin(config.plugins.projects) as ProjectPlugin
            }

            val jobs: Map<String, ComputePlugin> = loadProductBasedPlugins(
                config.products?.compute ?: emptyMap(),
                config.plugins.jobs ?: emptyMap()
            ) as Map<String, ComputePlugin>

            val files: Map<String, FilePlugin> = loadProductBasedPlugins(
                config.products?.storage ?: emptyMap(),
                config.plugins.files ?: emptyMap()
            ) as Map<String, FilePlugin>

            val fileCollections: Map<String, FileCollectionPlugin> = loadProductBasedPlugins(
                config.products?.storage ?: emptyMap(),
                config.plugins.fileCollections ?: emptyMap()
            ) as Map<String, FileCollectionPlugin>

            VerifiedConfig.Plugins(connection, projects, jobs, files, fileCollections)
        }
    }

    return VerifiedConfig(core, server,  plugins, products, frontendProxy)
}

// Plugin loading
private fun <Cfg> loadPlugin(config: Cfg): Plugin<Cfg> {
    TODO()
}

private fun <Cfg : ConfigSchema.Plugins.ProductBased> loadProductBasedPlugins(
    products: Map<String, List<String>>,
    plugins: Map<String, Cfg>
): Map<String, Plugin<Cfg>> {
    val result = HashMap<String, Plugin<Cfg>>()
    val relevantProducts = products.entries.flatMap { (category, products) ->
        products.map { ProductReferenceWithoutProvider(it, category) }
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
                        "creating a more specific matcher."
                )
            }

            if (score > bestScore) {
                bestScore = score
                bestMatch = id
            }
        }

        if (bestMatch == null) {
            emitWarning(
                "Could not allocate product '$product' to a plugin. No plugins match it, " +
                    "the integration module will ignore all requests for this product!"
            )
        } else {
            partitionedProducts[bestMatch] = (partitionedProducts[bestMatch] ?: emptyList()) + product
        }
    }

    return result
}

// End-user feedback
private fun missingFile(file: String): Nothing {
    // TODO
    println("Missing file at $file")
    exitProcess(1)
}

private fun insecureFile(file: String): Nothing {
    // TODO
    println("Insecure file $file")
    exitProcess(1)
}

private fun emitWarning(warning: String) {
    emitWarning(VerifyResult.Warning<Unit>(warning))
}

private fun emitWarning(result: VerifyResult.Warning<*>) {
    if (result.ref == null) {
        println("GENERIC WARNING: ${result.message}")
    } else {
        sendTerminalMessage {
            yellow { bold { inline("Configuration warning! ") } } 
            code { line(result.ref.file) }

            line(result.message)
            line()
            
            if (result.ref.location != null) {
                inline("The warning occured approximately here")
            } else if (result.ref.property != null) {
                inline("The warning occured")
            }

            if (result.ref.property != null) {
                inline(" in property ")
                code { inline(result.ref.property) }
            }

            if (result.ref.location != null) {
                line(":")
                yamlDocumentContext(result.ref.document, result.ref.location.approximateStart,
                    result.ref.location.approximateEnd)
            } else {
                line()
            }
        }
    }
}

private fun emitError(error: String): Nothing {
    emitError(VerifyResult.Error<Unit>(error))
}

private fun emitError(result: VerifyResult.Error<*>): Nothing {
    if (result.ref == null) {
        println("GENERIC ERROR: ${result.message}")
    } else {
        sendTerminalMessage {
            red { bold { inline("Configuration error! ") } } 
            code { line(result.ref.file) }

            line(result.message)
            line()
            
            if (result.ref.location != null) {
                inline("The error occured approximately here")
            } else if (result.ref.property != null) {
                inline("The error occured")
            }

            if (result.ref.property != null) {
                inline(" in property ")
                code { inline(result.ref.property) }
            }

            if (result.ref.location != null) {
                line(":")
                yamlDocumentContext(result.ref.document, result.ref.location.approximateStart,
                    result.ref.location.approximateEnd)
            } else {
                line()
            }

            if (result.ref.location?.approximateStart == 0 && result.ref.location?.approximateEnd == 0) {
                line()
                line("The above value was computed value/default value. You can try specifying the value explicitly " +
                    "in the configuration.")
            }
        }
    }
    exitProcess(1)
}

// General verification procedures
data class ConfigurationReference(
    val file: String, 
    val document: String, 
    // NOTE(Dan): A value of null or (0, 0) indicates that this value was comptued
    val location: YamlLocationReference?,
    val property: String? = null,
) {
    fun useLocation(tag: YamlLocationTag?): ConfigurationReference {
        return if (tag == null) this
        else copy(location = tag.toReference())
    }

    fun useLocationAndProperty(tag: YamlLocationTag?, property: String): ConfigurationReference {
        return useLocation(tag).copy(property = property)
    }
}

sealed class VerifyResult<T> {
    data class Ok<T>(val result: T) : VerifyResult<T>()
    data class Warning<T>(val message: String, val ref: ConfigurationReference? = null) : VerifyResult<T>()
    data class Error<T>(val message: String, val ref: ConfigurationReference? = null) : VerifyResult<T>()
}

private fun <T> handleVerificationResultStrict(result: VerifyResult<T>): T {
    return handleVerificationResult(result, errorsAreWarnings = false)!!
}

private fun <T> handleVerificationResultWeak(result: VerifyResult<T>): T? {
    return handleVerificationResult(result, errorsAreWarnings = false)
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
            emitWarning(result.message)
            null
        }
    }
}

private fun verifyHost(host: Host): VerifyResult<Host> {
    // TODO
    return VerifyResult.Ok(host)
}

private fun verifyFile(
    path: String, 
    typeRequirement: FileType?,
    ref: ConfigurationReference? = null,
): VerifyResult<String> {
    val isOk = when (typeRequirement) {
        FileType.FILE -> fileExists(path) && !fileIsDirectory(path)
        FileType.DIRECTORY -> fileExists(path) && fileIsDirectory(path)
        else -> fileExists(path)
    }

    if (!isOk) {
        return when (typeRequirement) {
            FileType.DIRECTORY -> VerifyResult.Error<String>("No directory exists at '$path'", ref)
            null -> VerifyResult.Error<String>("No file exists at '$path'", ref)
            else -> {
                VerifyResult.Error<String>("No file exists at '$path'", ref)
            }
        }
    } else {
        return VerifyResult.Ok(path)
    }
}
