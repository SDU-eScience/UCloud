package dk.sdu.cloud

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.accounting.api.ProductsRetrieveRequest
import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.cli.CommandLineInterface
import dk.sdu.cloud.cli.registerAlwaysOnCommandLines
import dk.sdu.cloud.config.*
import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.controllers.EnvoyConfigurationService
import dk.sdu.cloud.controllers.IpcController
import dk.sdu.cloud.debug.*
import dk.sdu.cloud.io.CommonFile
import dk.sdu.cloud.ipc.*
import dk.sdu.cloud.plugins.ResourcePlugin
import dk.sdu.cloud.plugins.SimplePluginContext
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.InternalTokenValidationJWT
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.migrations.loadMigrations
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.cors.routing.*
import libc.clib
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.readSymbolicLink
import kotlin.system.exitProcess
import dk.sdu.cloud.controllers.*
import dk.sdu.cloud.plugins.storage.posix.posixFilePermissionsFromInt
import dk.sdu.cloud.sql.*
import dk.sdu.cloud.utils.*
import dk.sdu.cloud.config.ConfigSchema.Core.Logs.Tracer as TracerFeature
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.ListSerializer
import org.slf4j.LoggerFactory
import java.sql.DriverManager

fun main(args: Array<String>) {
    try {
        // NOTE(Dan): The integration module of UCloud can start in one of three modes. What the integration module
        // does and starts depends heavily on the mode we are started in. We present a short summary of the modes here,
        // but we refer to the documentation for more in-depth explanations of the different modes.
        //
        // 1. Server: This mode receives traffic which is not bound to a specific user. This mode is also responsible
        //            for providing core services to the other modes. This includes: access to the database, routing
        //            of traffic and launching user instances. Other instances communicate with the server instance
        //            through inter-process communication (IPC).
        //
        // 2. User:   This mode receives traffic which is bound to a specific user. Communication is load balances to
        //            this instances based on the L7 router. Most of the business logic is handled in this mode.
        //
        // 3. CLI:    Handles command-line invocations. This is typically used by administrators to query and update
        //            state of the integration module. Typically, this is implemented through IPC with the server
        //            instance.
        //
        // 4. Proxy:  The proxy instance is responsible for proxying inter-process communication across different
        //            nodes. This process is transparent, and is only relevant if the command-line interface is used
        //            from a different node, than the node which hosts the server instance.

        val serverMode = when {
            args.getOrNull(0) == "user" -> ServerMode.User
            args.getOrNull(0) == "server" || args.isEmpty() -> ServerMode.Server
            args.getOrNull(0) == "proxy" -> ServerMode.FrontendProxy
            else -> ServerMode.Plugin(args[0])
        }

        // NOTE(Dan): From this point, the main function is extremely linear in code execution and always goes through
        // the following phases:
        //
        // 1. Process configuration and signal handlers:
        // -----------------------------------------------------------------------------------------------------------
        // In the first phase we perform simple configuration of the process itself. Currently, this involves querying
        // some information about the process and its environment, along with installing signal handlers.
        //
        // 2. Configuration:
        // -----------------------------------------------------------------------------------------------------------
        // In the following step, we read and verify the configuration from the file-system. The configuration changes
        // how and which services are constructed.
        //
        // If the configuration is not valid, then the program will terminate before continuing. Assuming that
        // verification of the configuration passes, then everything else should be assumed to succeed. Only bugs in
        // the software should prevent us from starting from this point.
        //
        // 3. Service construction:
        // -----------------------------------------------------------------------------------------------------------
        // In this phase services are constructed, but they remain inactive. In this function we refer to a service as
        // anything which performs some kind of business logic. This could be access to a database or running one of
        // the servers required. The ultimate goal of the main function is to transfer control to these services.
        //
        // 4. Transferring control to services:
        // -----------------------------------------------------------------------------------------------------------
        // In this phase we transfer control fully to all the services which were constructed in the previous
        // phase. Almost all services are powered by some kind of loop running in a background coroutine/thread.


        // 1. Process configuration and signal handlers:
        // ===========================================================================================================

        val ownExecutable = readSelfExecutablePath()

        run {
            // Install common signal handlers. At the moment, it appears that we can install identical handlers
            // regardless of mode.

            // NOTE(Dan): The interrupt handler will attempt to shut down all relevant children and then proceed to
            // shut itself off.
            Runtime.getRuntime().addShutdownHook(object : Thread() {
                override fun run() {
                    ProcessWatcher.killAll()
                }
            })
        }

        runBlocking {
            // 2. Configuration:
            // =======================================================================================================
            // NOTE(Dan): Regardless of mode, we will read the configuration. The configuration we load is
            // going to be different based on the mode. This is a natural consequence of the file permissions which
            // are on the configuration files. For example, the user instances will run as the end-user, it is
            // critical that these are not able to load API tokens for UCloud/Core.
            //
            // The configuration process will detect if we have not completed the initial setup process. This includes
            // not having any valid API tokens for UCloud/Core. If that is the case, we will run the installer which
            // guides the operator through this initial setup phase.
            val configSchema = loadConfiguration()
            val config = run {
                val runInstaller = with(configSchema) {
                    core == null &&
                            server == null &&
                            plugins == null &&
                            products == null &&
                            frontendProxy == null
                }

                if (runInstaller) {
                    runInstaller()
                    exitProcess(0)
                }

                verifyConfiguration(serverMode, configSchema)
            }

            val logDir = config.core.logs.directory

            // NOTE(Dan): This is passed directly to the config file, which doesn't escape anything. Be careful with
            // potential XML injection here.
            val logModule = when (serverMode) {
                ServerMode.FrontendProxy -> "frontend-proxy"
                is ServerMode.Plugin -> "plugin-${Time.now()}"
                ServerMode.Server -> "server"
                ServerMode.User -> "user-${clib.getuid()}"
            }

            run {
                // Tell logback information about the log output. We need to do this as early as possible since we
                // cannot do any real logging before these calls have been made.
                //
                // We have named the configuration in such a way that it is not autoloaded by logback. If it were, then
                // it would complain heavily about the fact that a bunch of loggers were loaded doing companion object
                // initialization before these properties were set. Instead, we use the default config, which is to
                // output to stdout until these properties are ready to be set.
                val ctx = LoggerFactory.getILoggerFactory() as LoggerContext
                val configurator = JoranConfigurator()
                configurator.context = ctx
                ctx.reset()
                configurator.doConfigure(logbackConfiguration(logDir, logModule).encodeToByteArray().inputStream())
            }

            run {
                // Verify that we have been launched with a valid user, and that all file permissions appear to be
                // correct. This process is meant to alert the operator of potentially dangerous configurations.
                val uid = clib.getuid()
                when (serverMode) {
                    ServerMode.FrontendProxy -> {
                        if (uid == 0) throw IllegalStateException("Refusing the start the frontend proxy as root")
                    }

                    is ServerMode.Plugin -> {
                        // Do nothing (some plugins will run as root, which is fine)
                    }

                    ServerMode.Server -> {
                        if (uid == 0 && !config.core.allowRootMode) {
                            throw IllegalStateException("Refusing to start the server as root")
                        }
                    }

                    ServerMode.User -> {
                        if (uid == 0) throw IllegalStateException("Refusing to start a user instance as root")
                    }
                }
            }

            // 3. Service construction:
            // =======================================================================================================
            // NOTE(Dan): At this point, we are ready to begin constructing services. In this function, we define a
            // service to be anything that needs to run after initialization is complete.
            //
            // __The goal of the main function is to ultimately transfer control to all the relevant services.__
            //
            // For example, a service could be the RPC server (w/plugin controllers) or the L7 router. Not all
            // modes have access to all services.
            // =======================================================================================================

            // Feature traces
            // -------------------------------------------------------------------------------------------------------
            run {
                val trace = config.core.logs.trace
                shellTracer = createFeatureTracer(TracerFeature.SHELL in trace, "Shell")
            }

            // Database services
            // -------------------------------------------------------------------------------------------------------
            if (config.serverOrNull != null && serverMode == ServerMode.Server) {
                dbConfig.set(config.server.database)
                // NOTE(Dan): It is important that migrations run _before_ plugins are loaded
                val handler = MigrationHandler(dbConnection)
                loadMigrations(handler)
                handler.migrate()
            }

            // Command Line Interface (CLI)
            // -------------------------------------------------------------------------------------------------------
            val cli = if (serverMode !is ServerMode.Plugin) {
                null
            } else {
                val isParsable = args.contains("--parsable")
                CommandLineInterface(isParsable, args.drop(1).filter { it != "--parsable" })
            }

            // Inter Process Communication Client (IPC)
            // -------------------------------------------------------------------------------------------------------
            val ipcSocketDirectory = config.core.ipc.directory
            val ipcClient: IpcClient? = when (serverMode) {
                ServerMode.FrontendProxy -> null
                ServerMode.Server -> EmbeddedIpcClient()
                else -> RealIpcClient(ipcSocketDirectory)
            }

            // IpcServer comes later, since it requires knowledge of the RpcServer. TODO(Dan): Change this?

            // JWT Validation
            // -------------------------------------------------------------------------------------------------------
            // NOTE(Dan): The JWT validation is responsible for validating all communication we receive from
            // UCloud/Core. This service is constructed fairly early, since both the RPC client and RPC server
            // requires this.
            val validation = if (serverMode == ServerMode.Server || serverMode == ServerMode.User) {
                try {
                    InternalTokenValidationJWT.withPublicCertificate(config.core.certificate)
                } catch (ex: Throwable) {
                    sendTerminalMessage {
                        bold { red { line("Error while parsing certificate of UCloud/Core") } }

                        line()
                        line("We read the following certificate:")
                        code { line(config.core.certificate) }

                        line()
                        line("Formatted version:")
                        code { line(InternalTokenValidationJWT.formatCert(config.core.certificate, true)) }

                        line()
                        inline("Certificate hash (SHA1):")
                        code { line(hex(sha1(config.core.certificate.encodeToByteArray()))) }
                        line("Note: The hash might be different from the file since we trim new-lines.")

                        line()
                        line("Please double check that this matches the certificate you received during the connection procedure with UCloud/Core.")

                        line()
                        line("The error was:")
                        val stack = ex.toReadableStacktrace()
                        line("${stack.type}: ${stack.message}")
                        for (frame in stack.frames) {
                            line(frame.prependIndent("    "))
                        }
                    }

                    exitProcess(0)
                }
            } else {
                null
            }

            // Remote Procedure Calls (RPC)
            // -------------------------------------------------------------------------------------------------------
            val rpcServerPort = when (serverMode) {
                is ServerMode.Plugin, ServerMode.FrontendProxy -> null
                ServerMode.Server -> UCLOUD_IM_PORT
                ServerMode.User -> args.getOrNull(1)?.toInt() ?: error("Missing port argument for user server")
            }

            val rpcServer = when (serverMode) {
                ServerMode.Server, ServerMode.User -> RpcServer()
                else -> null
            }
            var ktorEngine: ApplicationEngine? = null

            if (rpcServer != null) {
                ClientInfoInterceptor().register(rpcServer)
                AuthInterceptor(validation ?: error("No validation")).register(rpcServer)
                IdleGarbageCollector().register(rpcServer)

                val engine = embeddedServer(
                    CIO,
                    host = "127.0.0.1",
                    port = rpcServerPort ?: error("Missing rpcServerPort"),
                    module = {}
                )
                ktorEngine = engine
                engine.application.install(CORS) {
                    // We run with permissive CORS settings in dev mode. This allows us to test frontend directly
                    // with local backend.
                    allowHost("frontend:9000")
                    allowHost("localhost:9000")
                    val ucloudHost = config.core.hosts.ucloud.toStringOmitDefaultPort()
                    allowHost(ucloudHost.substringAfter("://"), listOf(ucloudHost.substringBefore("://")))

                    val selfHost = config.core.hosts.self?.toStringOmitDefaultPort()
                    if (selfHost != null) {
                        allowHost(selfHost.substringAfter("://"), listOf(selfHost.substringBefore("://")))
                    }

                    println(config.core.cors.allowHosts.toString())
                    config.core.cors.allowHosts.forEach {
                        println("Setting $it")
                        allowHost(it.removePrefix("https://").removePrefix("http://"), listOf("https", "http"))
                    }

                    allowMethod(HttpMethod.Get)
                    allowMethod(HttpMethod.Post)
                    allowMethod(HttpMethod.Put)
                    allowMethod(HttpMethod.Delete)
                    allowMethod(HttpMethod.Head)
                    allowMethod(HttpMethod.Options)
                    allowMethod(HttpMethod.Patch)
                    allowNonSimpleContentTypes = true
                    allowCredentials = true
                    allowHeader(HttpHeaders.Authorization)
                    allowHeader("X-CSRFToken")
                    allowHeader("refreshToken")
                    allowHeader("chunked-upload-offset")
                    allowHeader("chunked-upload-token")
                    allowHeader("ucloud-username")
                    allowHeader("upload-name")
                }

                rpcServer.attachRequestInterceptor(IngoingWebSocketInterceptor(engine, rpcServer))
                rpcServer.attachRequestInterceptor(IngoingHttpInterceptor(engine, rpcServer))
            }

            val rpcClient: AuthenticatedClient? = when (serverMode) {
                ServerMode.Server -> {
                    val client = RpcClient().also { client ->
                        OutgoingHttpRequestInterceptor()
                            .install(
                                client,
                                FixedOutgoingHostResolver(
                                    HostInfo(
                                        config.core.hosts.ucloud.host,
                                        config.core.hosts.ucloud.scheme,
                                        config.core.hosts.ucloud.port
                                    )
                                )
                            )
                    }

                    client.attachFilter(OutgoingProject())

                    val authenticator = RefreshingJWTAuthenticator(
                        client,
                        JwtRefresher.Provider(config.server.refreshToken, OutgoingHttpCall),
                    )

                    authenticator.authenticateClient(OutgoingHttpCall)
                }

                ServerMode.User -> {
                    val client = RpcClient()
                    client.attachRequestInterceptor(IpcProxyRequestInterceptor(ipcClient!!))
                    AuthenticatedClient(client, IpcProxyCall, afterHook = null, authenticator = {})
                }

                ServerMode.FrontendProxy -> {
                    val cfg = config.frontendProxy
                    val client = RpcClient().also { client ->
                        OutgoingHttpRequestInterceptor()
                            .install(
                                client,
                                FixedOutgoingHostResolver(
                                    HostInfo(
                                        cfg.remote.host,
                                        cfg.remote.scheme,
                                        cfg.remote.port
                                    )
                                )
                            )
                    }

                    AuthenticatedClient(client, OutgoingHttpCall) {
                        it.attributes.outgoingAuthToken = cfg.sharedSecret
                    }
                }

                is ServerMode.Plugin -> null
            }

            // IPC Server
            // -------------------------------------------------------------------------------------------------------
            val ipcServer = when (serverMode) {
                ServerMode.Server, ServerMode.FrontendProxy -> IpcServer(
                    ipcSocketDirectory,
                    config.frontendProxyOrNull,
                    rpcClient!!,
                    rpcServer
                )

                else -> null
            }

            if (ipcClient is EmbeddedIpcClient && ipcServer != null) ipcClient.server = ipcServer

            // L7 router (Envoy)
            // -------------------------------------------------------------------------------------------------------
            val envoyConfig = if (serverMode == ServerMode.Server) {
                EnvoyConfigurationService(config)
            } else {
                null
            }

            // Process Watcher
            // -------------------------------------------------------------------------------------------------------
            val processWatcher = when (serverMode) {
                ServerMode.Server, ServerMode.User -> ProcessWatcher
                else -> null
            }

            // IPC Ping+Pong
            // -------------------------------------------------------------------------------------------------------
            // NOTE(Dan): The IPC Ping+Pong protocol ensures that user instances are only kept alive as long as their
            // parent process is still alive.
            val ipcPingPong = IpcPingPong(ipcServer, ipcClient)

            ipcClient?.connect()
            ipcPingPong.start() // Depends on ipcClient being initialized first

            // Collecting resource plugins
            // -------------------------------------------------------------------------------------------------------
            val allResourcePlugins = ArrayList<ResourcePlugin<*, *, *, *>>()
            if (config.pluginsOrNull != null) {
                allResourcePlugins.addAll(config.plugins.fileCollections.values)
                allResourcePlugins.addAll(config.plugins.files.values)
                allResourcePlugins.addAll(config.plugins.jobs.values)
                allResourcePlugins.addAll(config.plugins.ingresses.values)
                allResourcePlugins.addAll(config.plugins.publicIps.values)
                allResourcePlugins.addAll(config.plugins.licenses.values)
            }

            // Resolving products for plugins
            // -------------------------------------------------------------------------------------------------------
            // NOTE(Dan): This will only work for server and user mode. User mode will use a proxy to the server mode
            // to resolve the products.
            if (serverMode == ServerMode.Server || serverMode == ServerMode.User) {
                val unknownProducts = HashSet<Product>()

                for (plugin in allResourcePlugins) {
                    val allConfiguredProducts: List<Product> = config.products.allProducts

                    val resolvedProducts = ArrayList<Product>()
                    for (product in plugin.productAllocation) {
                        val configuredProduct = allConfiguredProducts
                            .find { it.name == product.id && it.category.name == product.category }
                            ?: error("Internal error $product was not in $allConfiguredProducts")

                        val resolvedProduct = Products.retrieve.call(
                            ProductsRetrieveRequest(
                                filterName = product.id,
                                filterCategory = product.category,
                                filterProvider = config.core.providerId
                            ),
                            rpcClient!!
                        ).orNull()

                        if (resolvedProduct == null) {
                            unknownProducts.add(configuredProduct)
                        } else {
                            val areEqual: Boolean = run {
                                val a = configuredProduct
                                val b = resolvedProduct

                                val areInternalEqual = when (a) {
                                    is Product.Compute -> {
                                        b is Product.Compute && a.cpu == b.cpu && a.gpu == b.gpu
                                                && a.memoryInGigs == b.memoryInGigs
                                                && a.cpuModel == b.cpuModel
                                                && a.gpuModel == b.gpuModel
                                                && a.memoryModel == b.memoryModel
                                    }

                                    is Product.Ingress -> b is Product.Ingress
                                    is Product.License -> b is Product.License
                                    is Product.NetworkIP -> b is Product.NetworkIP
                                    is Product.Storage -> b is Product.Storage
                                }

                                a.pricePerUnit == b.pricePerUnit && areInternalEqual && a.description == b.description
                            }

                            if (areEqual) {
                                resolvedProducts.add(resolvedProduct)
                            } else {
                                // NOTE(Dan): Add to both lists since it is still part of the product allocation even
                                // if it isn't the latest version we have in the configuration.
                                resolvedProducts.add(resolvedProduct)
                                unknownProducts.add(configuredProduct)
                            }
                        }
                    }

                    plugin.productAllocation = resolvedProducts.map {
                        ProductReferenceWithoutProvider(it.name, it.category.name)
                    }

                    plugin.productAllocationResolved = resolvedProducts
                }

                config.products.productsUnknownToUCloud = unknownProducts

                if (unknownProducts.isNotEmpty()) {
                    sendTerminalMessage {
                        bold { yellow { line("Not all products have been registered with UCloud (with all the latest changes)!") } }

                        inline("Register the products with: ")
                        code { line("ucloud products register") }
                        line()

                        bold { line("The following products will NOT work until they are registered.") }
                        unknownProducts
                            .sortedBy { "${it.productType} / ${it.category.name} / ${it.name}" }
                            .forEach {
                                line(" - ${it.name} / ${it.category.name} (${it.productType})")
                            }
                    }
                }
            }

            // Debug services
            // -------------------------------------------------------------------------------------------------------
            val debugTransformer = if (config.core.developmentMode) {
                DebugMessageTransformer.Development
            } else {
                DebugMessageTransformer.Production
            }
            val structuredLogs = File(config.core.logs.directory, "structured").also {
                it.mkdirs()
                runCatching {
                    java.nio.file.Files.setPosixFilePermissions(
                        it.toPath(),
                        posixFilePermissionsFromInt("777".toInt(8))
                    )
                }
            }.absolutePath
            val debugSystem = when (serverMode) {
                ServerMode.Server -> CommonDebugSystem(
                    "IM/Server",
                    CommonFile(structuredLogs),
                    debugTransformer
                )

                ServerMode.User -> CommonDebugSystem(
                    "IM/User/${clib.getuid()}",
                    CommonFile(structuredLogs),
                    debugTransformer
                )

                else -> null
            }
            debugSystemAtomic.getAndSet(debugSystem)

            if (debugSystem != null && rpcClient != null) {
                debugSystem.registerMiddleware(rpcClient.client, rpcServer)
            }

            // Configuration debug (before initializing any plugins, which might crash because of config)
            // -------------------------------------------------------------------------------------------------------
            if (config.core.developmentMode) {
                // NOTE(Dan): Do NOT remove the check as this will cause refresh tokens to be logged which we
                // shouldn't do.
                debugSystem.normalD("Configuration has been loaded!", ConfigSchema.serializer(), configSchema)
                debugSystem.detailD(
                    "Compute products loaded",
                    ListSerializer(Product.serializer()),
                    config.products.compute?.values?.flatten() ?: emptyList()
                )
                debugSystem.detailD(
                    "Storage products loaded",
                    ListSerializer(Product.serializer()),
                    config.products.storage?.values?.flatten() ?: emptyList()
                )
            }

            // Initialization of plugins (Final initialization step)
            // -------------------------------------------------------------------------------------------------------
            val pluginContext = SimplePluginContext(rpcClient, config, ipcClient, ipcServer, cli, debugSystem)
            val controllerContext = ControllerContext(ownExecutable, config, pluginContext)

            if (config.pluginsOrNull != null) {
                val plugins = config.plugins
                with(pluginContext) {
                    // NOTE(Dan): Initialization order is important. Do not change it without a good reason.

                    plugins.connection?.apply { initialize() }
                    plugins.projects?.apply { initialize() }
                    for ((_, plugin) in plugins.allocations) plugin.apply { initialize() }
                    for ((_, plugin) in plugins.fileCollections) plugin.apply { initialize() }
                    for ((_, plugin) in plugins.files) plugin.apply { initialize() }
                    for ((_, plugin) in plugins.shares) plugin.apply { initialize() }
                    for ((_, plugin) in plugins.jobs) plugin.apply { initialize() }
                    for ((_, plugin) in plugins.ingresses) plugin.apply { initialize() }
                    for ((_, plugin) in plugins.publicIps) plugin.apply { initialize() }
                    for ((_, plugin) in plugins.licenses) plugin.apply { initialize() }
                }
            }

            // 4. Transferring control to services:
            // =======================================================================================================
            // NOTE(Dan): None of the services we launch here are supposed to block the current thread of execution.
            // They should instead launch in a new coroutine or thread. As a result, the code ends up being fairly
            // linear when not counting the null checks.

            if (ipcServer != null && rpcClient != null) {
                IpcToUCloudProxyServer(rpcClient).init(ipcServer, rpcClient)
                ProcessingScope.launch { ipcServer.runServer() }
            }
            envoyConfig?.start(config.serverOrNull?.network?.listenPort)
            processWatcher?.initialize()
            if (rpcServer != null) loadE2EValidation(rpcServer, pluginContext)

            if (config.pluginsOrNull != null) {
                for (plugin in allResourcePlugins) {
                    if (config.shouldRunServerCode()) {
                        ProcessingScope.launch {
                            with(pluginContext) {
                                with(plugin) {
                                    // Delay execution of monitoring loop until rpcServer is reporting ready
                                    while (isActive) {
                                        val isReady = rpcServer?.isRunning ?: true
                                        if (isReady) break
                                        delay(50)
                                    }

                                    runMonitoringLoopInServerMode()
                                }
                            }
                        }
                    }

                    if (config.shouldRunUserCode()) {
                        ProcessingScope.launch {
                            with(pluginContext) {
                                with(plugin) {
                                    // Delay execution of monitoring loop until rpcServer is reporting ready
                                    while (isActive) {
                                        val isReady = rpcServer?.isRunning ?: true
                                        if (isReady) break
                                        delay(50)
                                    }

                                    runMonitoringLoopInUserMode()
                                }
                            }
                        }
                    }
                }
            }

            if (serverMode is ServerMode.Plugin || serverMode == ServerMode.Server) {
                registerAlwaysOnCommandLines(controllerContext)
            }

            if (serverMode is ServerMode.Plugin) {
                cli?.execute(serverMode.name) // NOTE(Dan): Will always exit here
            }

            if (rpcServer != null) {
                rpcServer.configureControllers(
                    controllerContext,
                    FileController(controllerContext, envoyConfig),
                    FileCollectionController(controllerContext),
                    ComputeController(controllerContext, envoyConfig, ktorEngine!!.application),
                    IngressController(controllerContext),
                    PublicIPController(controllerContext),
                    LicenseController(controllerContext),
                    ShareController(controllerContext),
                    ConnectionController(controllerContext, envoyConfig),
                    EventController(controllerContext),
                )
            }

            sendTerminalMessage {
                bold {
                    green {
                        val separator = CharArray(80) { '~' }.concatToString()
                        line(separator)
                        line("UCloud/IM is now ready serve requests!")
                        line(separator)
                    }
                }
                line()

                val empty = "" to ""
                val stats = ArrayList<Pair<String, String>>()
                stats.add("Provider ID" to config.core.providerId)
                stats.add("Mode" to serverMode.toString())
                stats.add(empty)
                stats.add("All logs" to config.core.logs.directory)
                stats.add("My logs" to "${config.core.logs.directory}/$logModule-ucloud.log")
                if (serverMode == ServerMode.Server) {
                    val embeddedConfig = config.server.database.embeddedDataDirectory
                    if (embeddedConfig != null) {
                        stats.add("Database" to "Embedded: ${embeddedConfig}")
                    } else {
                        stats.add("Database" to config.server.database.jdbcUrl)
                    }
                }
                if (config.core.hosts.ucloud.host == "backend") {
                    stats.add("Debugger" to "http://localhost:42999")
                }
                stats.add(empty)
                if (config.pluginsOrNull != null) {
                    val jobs = config.plugins.jobs.values.map { it.pluginTitle }.toSet().joinToString(", ")
                        .takeIf { it.isNotEmpty() }
                    val projects = config.plugins.projects?.pluginTitle
                    val connection = config.plugins.connection?.pluginTitle
                    val files = config.plugins.files.values.map { it.pluginTitle }.toSet().joinToString(", ")
                        .takeIf { it.isNotEmpty() }
                    val fileCollections =
                        config.plugins.fileCollections.values.map { it.pluginTitle }.toSet().joinToString(", ")
                            .takeIf { it.isNotEmpty() }
                    val ingresses = config.plugins.ingresses.values.map { it.pluginTitle }.toSet().joinToString(", ")
                        .takeIf { it.isNotEmpty() }
                    val publicIps = config.plugins.publicIps.values.map { it.pluginTitle }.toSet().joinToString(", ")
                        .takeIf { it.isNotEmpty() }
                    val licenses = config.plugins.licenses.values.map { it.pluginTitle }.toSet().joinToString(", ")
                        .takeIf { it.isNotEmpty() }

                    stats.add("Files" to (files ?: "No plugins"))
                    stats.add("Drives" to (fileCollections ?: "No plugins"))

                    stats.add("Jobs" to (jobs ?: "No plugins"))
                    stats.add("Public links" to (ingresses ?: "No plugins"))
                    stats.add("Public IPs" to (publicIps ?: "No plugins"))
                    stats.add("Licenses" to (licenses ?: "No plugins"))

                    stats.add("Projects" to (projects ?: "No plugins"))
                    stats.add("Connection" to (connection ?: "No plugins"))
                }

                val colLength = stats.maxOf { it.first.length }
                for ((name, stat) in stats) {
                    if (name.isBlank() && stat.isBlank()) {
                        line()
                    } else {
                        bold { inline("${name.padStart(colLength, ' ')}: ") }
                        code { line(stat) }
                    }
                }
            }

            // NOTE(Dan): This thread is done! Spin the main thread indefinitely.
            while (isActive) {
                delay(50)
            }
        }
    } catch (ex: Throwable) {
        ex.printStackTrace()
        println("Good bye")
    }
}

// TODO(Dan): We have a number of utilities which should probably be moved out of this file.

private fun RpcServer.configureControllers(ctx: ControllerContext, vararg controllers: Controller) {
    controllers.forEach { it.configure(this) }

    val ipcServer = ctx.pluginContext.ipcServerOptional
    if (ipcServer != null) {
        controllers.forEach {
            if (it is IpcController) it.configureIpc(ipcServer)
        }
    }

    start()

    controllers.forEach { it.onServerReady(this) }
}

fun <R : Any, S : Any, E : Any> CallDescription<R, S, E>.callBlocking(
    request: R,
    client: AuthenticatedClient,
): IngoingCallResponse<S, E> {
    return runBlocking {
        call(request, client)
    }
}

private val debugSystemAtomic = AtomicReference<DebugSystem?>(null)
val debugSystem: DebugSystem?
    get() = debugSystemAtomic.get()

private val dbConfig = AtomicReference<VerifiedConfig.Server.Database>()

val dbConnection: DBContext by lazy {
    val config = dbConfig.get().takeIf { it != null }
    if (config == null) {
        error("Config not found for DB")
    } else {
        val connection = createDBConnection(config)
        runBlocking { testDB(connection) }
        connection
    }
}

private suspend fun testDB(db: DBContext) {
    db.withSession { session ->
        session.prepareStatement("select 1").invokeAndDiscard()
    }
}

private fun createDBConnection(database: VerifiedConfig.Server.Database): DBContext {
    return object : JdbcDriver() {
        override val pool: SimpleConnectionPool = SimpleConnectionPool(DB_CONNECTION_POOL_SIZE) { pool ->
            JdbcConnection(
                DriverManager.getConnection(database.jdbcUrl, database.username, database.password),
                pool
            )
        }
    }
}

const val DB_CONNECTION_POOL_SIZE = 8

private fun readSelfExecutablePath(): String {
    return File("/proc/self/exe").toPath().readSymbolicLink().toFile().absolutePath
}

const val UCLOUD_IM_PORT = 42000
