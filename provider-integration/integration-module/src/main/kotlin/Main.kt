package dk.sdu.cloud

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
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
import dk.sdu.cloud.config.ConfigSchema
import dk.sdu.cloud.config.loadConfiguration
import dk.sdu.cloud.config.verifyConfiguration
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
import dk.sdu.cloud.sql.DBContext
import dk.sdu.cloud.sql.JdbcDriver
import dk.sdu.cloud.sql.MigrationHandler
import dk.sdu.cloud.sql.migrations.loadMigrations
import dk.sdu.cloud.utils.ProcessWatcher
import dk.sdu.cloud.utils.sendTerminalMessage
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.cors.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import libc.clib
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.readSymbolicLink
import kotlin.system.exitProcess
import dk.sdu.cloud.controllers.*

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
        // In the first phase we perform simple configuration of the process itself. Currently this involves querying
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
        // In this phase we transfer control fully to all of the services which were constructed in the previous
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
                    runInstaller(ownExecutable)
                    exitProcess(0)
                }

                verifyConfiguration(serverMode, configSchema)
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
                        if (uid == 0) throw IllegalStateException("Refusing the start the server as root")
                    }

                    ServerMode.User -> {
                        if (uid == 0) throw IllegalStateException("Refusing the start a user instance as root")
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

            // Database services
            // -------------------------------------------------------------------------------------------------------
            if (config.serverOrNull != null && serverMode == ServerMode.Server) {
                databaseConfig.getAndSet(config.server.database.file)

                // NOTE(Dan): It is important that migrations run _before_ plugins are loaded
                val handler = MigrationHandler(dbConnection)
                loadMigrations(handler)
                handler.migrate()
            }

            // Command Line Interface (CLI)
            // -------------------------------------------------------------------------------------------------------
            val cli = if (serverMode !is ServerMode.Plugin) null else CommandLineInterface(args.drop(1))

            // Inter Process Communication Client (IPC)
            // -------------------------------------------------------------------------------------------------------
            val ipcSocketDirectory = config.core.ipc.directory
            val ipcClient = when (serverMode) {
                ServerMode.Server, ServerMode.FrontendProxy -> null
                else -> IpcClient(ipcSocketDirectory)
            }

            // IpcServer comes later, since it requires knowledge of the RpcServer. TODO(Dan): Change this?

            // JWT Validation
            // -------------------------------------------------------------------------------------------------------
            // NOTE(Dan): The JWT validation is responsible for validating all communication we receive from
            // UCloud/Core. This service is constructed fairly early, since both the RPC client and RPC server
            // requires this.
            val validation = if (serverMode == ServerMode.Server) {
                InternalTokenValidationJWT.withPublicCertificate(config.server.certificate)
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

            if (rpcServer != null) {
                ClientInfoInterceptor().register(rpcServer)
                AuthInterceptor(validation ?: error("No validation")).register(rpcServer)

                val engine = embeddedServer(CIO, port = rpcServerPort ?: error("Missing rpcServerPort")) {}
                engine.application.install(CORS) {
                    // We run with permissive CORS settings in dev mode. This allows us to test frontend directly
                    // with local backend.
                    allowHost("frontend:9000")
                    allowHost("localhost:9000")
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
                    allowHeader("upload-name")
                }

                rpcServer.attachRequestInterceptor(IngoingHttpInterceptor(engine, rpcServer))
                rpcServer.attachRequestInterceptor(IngoingWebSocketInterceptor(engine, rpcServer))
            }

            val rpcClient: AuthenticatedClient? = run {
                when (serverMode) {
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

                        val authenticator = RefreshingJWTAuthenticator(
                            client,
                            JwtRefresher.Provider(config.server.refreshToken, OutgoingHttpCall),
                            becomesInvalidSoon = { accessToken ->
                                val expiresAt = validation!!.validateOrNull(accessToken)?.expiresAt?.time
                                (expiresAt ?: return@RefreshingJWTAuthenticator true) +
                                    (1000 * 120) >= Time.now()
                            }
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

            // L7 router (Envoy)
            // -------------------------------------------------------------------------------------------------------
            val envoyConfig = if (serverMode == ServerMode.Server) {
                EnvoyConfigurationService(ENVOY_CONFIG_PATH)
            } else {
                null
            }

            // Process Watcher
            // -------------------------------------------------------------------------------------------------------
            val processWatcher = when(serverMode) {
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
            }

            // Resolving products for plugins
            // -------------------------------------------------------------------------------------------------------
            // NOTE(Dan): This will only work for server and user mode. User mode will use a proxy to the server mode
            // to resolve the products.
            if (serverMode == ServerMode.Server || serverMode == ServerMode.User) {
                for (plugin in allResourcePlugins) {
                    val resolvedProducts = ArrayList<Product>()
                    for (product in plugin.productAllocation) {
                        val resolvedProduct = Products.retrieve.call(
                            ProductsRetrieveRequest(
                                filterName = product.id,
                                filterCategory = product.category,
                                filterProvider = config.core.providerId
                            ),
                            rpcClient!!
                        ).orNull()

                        if (resolvedProduct == null) {
                            sendTerminalMessage {
                                red { bold { line("Configuration error!") } }
                                inline("The product ")
                                code { inline("${product.id} / ${product.category} ") }
                                inline("requested by ")
                                code {
                                    inline(plugin.pluginName)
                                    inline(" ")
                                }
                                line("is not recognized by UCloud")
                                line()
                                line("Please ensure that this product is correctly registered with UCloud")
                            }
                            exitProcess(1)
                        }

                        resolvedProducts.add(resolvedProduct)
                    }

                    plugin.productAllocationResolved = resolvedProducts
                }
            }

            // Debug services
            // -------------------------------------------------------------------------------------------------------
            val debugSystem = when (serverMode) {
                ServerMode.Server -> CommonDebugSystem("IM/Server", CommonFile(config.core.logs.directory))
                ServerMode.User -> CommonDebugSystem("IM/User/${clib.getuid()}", CommonFile(config.core.logs.directory))
                else -> null
            }
            debugSystemAtomic.getAndSet(debugSystem)

            if (debugSystem != null && rpcClient != null) {
                debugSystem.registerMiddleware(rpcClient.client, rpcServer)
            }

            // Configuration debug (before initializing any plugins, which might crash because of config)
            // -------------------------------------------------------------------------------------------------------
            debugSystem.normalD("Configuration has been loaded!", configSchema)

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
                    for ((_, plugin) in plugins.fileCollections) plugin.apply { initialize() }
                    for ((_, plugin) in plugins.files) plugin.apply { initialize() }
                    for ((_, plugin) in plugins.jobs) plugin.apply { initialize() }
                }
            }

            // 4. Transferring control to services:
            // =======================================================================================================
            // NOTE(Dan): None of the services we launch here are supposed to block the current thread of execution.
            // They should instead launch in a new coroutine or thread. As a result, the code ends up being fairly
            // linear when not counting the null checks.

            if (ipcServer != null && rpcClient != null) {
                IpcToUCloudProxyServer().init(ipcServer, rpcClient)
                ProcessingScope.launch { ipcServer.runServer() }
            }
            envoyConfig?.start(config.serverOrNull?.network?.listenPort)
            processWatcher?.initialize()
            if (rpcServer != null) loadE2EValidation(rpcServer, pluginContext)

            if (config.pluginsOrNull != null) {
                for (plugin in allResourcePlugins) {
                    ProcessingScope.launch {
                        with(pluginContext) {
                            with(plugin) {
                                // Delay execution of monitoring loop until rpcServer is reporting ready
                                while (isActive) {
                                    val isReady = rpcServer?.isRunning ?: true
                                    if (isReady) break
                                    delay(50)
                                }

                                runMonitoringLoop()
                            }
                        }
                    }
                }
            }

            if (serverMode is ServerMode.Plugin) {
                registerAlwaysOnCommandLines(controllerContext)
                cli?.execute(serverMode.name)
            }

            if (rpcServer != null) {
                rpcServer.configureControllers(
                    controllerContext,
                    FileController(controllerContext, envoyConfig),
                    FileCollectionController(controllerContext),
                    ComputeController(controllerContext),
                    ConnectionController(controllerContext, envoyConfig),
                    NotificationController(controllerContext),
                )

                rpcServer.start()
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
private val databaseConfig = AtomicReference<String>("")

val dbConnection: DBContext by lazy {
    val dbConfig = databaseConfig.get().takeIf { it.isNotBlank() }
    if (dbConfig == null) {
        error("This plugin does not have access to a database")
    } else {
        JdbcDriver("jdbc:sqlite:$dbConfig")
    }
}

private fun readSelfExecutablePath(): String {
    return File("/proc/self/exe").toPath().readSymbolicLink().toFile().absolutePath
}

const val ENVOY_CONFIG_PATH = "/var/run/ucloud/envoy"
const val UCLOUD_IM_PORT = 42000
