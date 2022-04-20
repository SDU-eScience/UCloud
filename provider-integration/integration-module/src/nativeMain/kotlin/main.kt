package dk.sdu.cloud

import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.cli.CommandLineInterface
import dk.sdu.cloud.controllers.*
import dk.sdu.cloud.http.OutgoingHttpCall
import dk.sdu.cloud.http.OutgoingHttpRequestInterceptor
import dk.sdu.cloud.http.RpcServer
import dk.sdu.cloud.http.loadMiddleware
import dk.sdu.cloud.ipc.*
import dk.sdu.cloud.plugins.PluginLoader
import dk.sdu.cloud.plugins.SimplePluginContext
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.DBContext
import dk.sdu.cloud.sql.MigrationHandler
import dk.sdu.cloud.sql.Sqlite3Driver
import dk.sdu.cloud.utils.ProcessWatcher
import dk.sdu.cloud.sql.migrations.loadMigrations
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.posix.*
import kotlin.coroutines.CoroutineContext
import kotlin.system.exitProcess

sealed class ServerMode {
    object User : ServerMode()
    object Server : ServerMode()
    object FrontendProxy : ServerMode()
    data class Plugin(val name: String) : ServerMode()
}

@OptIn(ExperimentalStdlibApi::class, ExperimentalUnsignedTypes::class)
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
        //            state of the integration module. Typically this is implemented through IPC with the server
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

            // NOTE(Dan): Our code already correctly handles EPIPE. There is no need for using the signal.
            signal(SIGPIPE, SIG_IGN)

            // NOTE(Dan): The interrupt handler will attempt to shutdown all relevant children and then proceed to
            // shut itself off.
            signal(SIGINT, staticCFunction { p ->
                ProcessWatcher.killAll()
                exitProcess(0)
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
            val config = try {
                IMConfiguration.load(serverMode)
            } catch (ex: ConfigurationException.IsBeingInstalled) {
                if (getuid() == 0U) {
                    throw IllegalStateException(
                        "Refusing to start as root. The integration module needs to be " +
                            "started as a dedicated 'ucloud' (service) user."
                    )
                }

                runInstaller(ex.core, ex.server, ownExecutable)
                exitProcess(0)
            } catch (ex: ConfigurationException.BadConfiguration) {
                println(ex.message)
                exitProcess(1)
            }

            run {
                // Verify that we have been launched with a valid user, and that all file permissions appear to be
                // correct. This process is meant to alert the operator of potentially dangerous configurations.
                val uid = getuid()
                when (serverMode) {
                    ServerMode.FrontendProxy -> {
                        if (uid == 0U) throw IllegalStateException("Refusing the start the frontend proxy as root")

                        if (config.server != null) {
                            throw IllegalStateException(
                                "Misconfiguration of file permissions! The frontend proxy should " +
                                    "not be able to read server.json"
                            )
                        }

                        if (config.frontendProxy == null) {
                            throw IllegalStateException(
                                "Misconfiguration of file permissions! The frontend proxy should " +
                                    "be able to read frontend_proxy.json"
                            )
                        }
                    }

                    is ServerMode.Plugin -> {
                        // Do nothing (some plugins will run as root, which is fine)
                    }

                    ServerMode.Server -> {
                        if (uid == 0U) throw IllegalStateException("Refusing the start the server as root")
                    }

                    ServerMode.User -> {
                        if (uid == 0U) throw IllegalStateException("Refusing the start a user instance as root")

                        if (config.server != null) {
                            throw IllegalStateException(
                                "Misconfiguration of file permissions. A user should not be able " +
                                    "to read server.json"
                            )
                        }
                        if (config.frontendProxy != null) {
                            throw IllegalStateException(
                                "Misconfiguration of file permissions. A user should not be able " +
                                    "to read frontend_proxy.json"
                            )
                        }
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
            if (config.server != null && serverMode == ServerMode.Server) {
                databaseConfig.getAndSet(config.server.dbFile)

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
            val ipcSocketDirectory = config.core.ipcDirectory ?: config.configLocation
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
            val validation = NativeJWTValidation(config.core.certificate ?: error("Missing certificate"))
            loadMiddleware(config, validation)

            // Remote Procedure Calls (RPC)
            // -------------------------------------------------------------------------------------------------------
            val rpcServerPort = when (serverMode) {
                is ServerMode.Plugin, ServerMode.FrontendProxy -> null
                ServerMode.Server -> UCLOUD_IM_PORT
                ServerMode.User -> args.getOrNull(1)?.toInt() ?: error("Missing port argument for user server")
            }

            val rpcServer = when (serverMode) {
                ServerMode.Server, ServerMode.User -> RpcServer(rpcServerPort ?: error("Missing rpcServerPort"))
                else -> null
            }

            val rpcClient: AuthenticatedClient? = run {
                when (serverMode) {
                    ServerMode.Server -> {
                        val serverConfig = config.server ?: error("Could not find server configuration")
                        val client = RpcClient().also { client ->
                            OutgoingHttpRequestInterceptor()
                                .install(
                                    client,
                                    FixedOutgoingHostResolver(
                                        HostInfo(
                                            serverConfig.ucloud.host,
                                            serverConfig.ucloud.scheme,
                                            serverConfig.ucloud.port
                                        )
                                    )
                                )
                        }

                        val authenticator = RefreshingJWTAuthenticator(
                            client,
                            JwtRefresher.Provider(serverConfig.refreshToken, OutgoingHttpCall),
                            becomesInvalidSoon = { accessToken ->
                                val expiresAt = validation.validateOrNull(accessToken)?.expiresAt
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
                        val cfg = config.frontendProxy ?: error("Could not find frontend proxy configuration")
                        val client = RpcClient().also { client ->
                            OutgoingHttpRequestInterceptor()
                                .install(
                                    client,
                                    FixedOutgoingHostResolver(
                                        HostInfo(
                                            cfg.remoteHost,
                                            cfg.remoteScheme,
                                            cfg.remotePort
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
                    config.frontendProxy ?: error("Could not find frontend proxy configuration"),
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

            // Initialization of plugins (Final initialization step)
            // -------------------------------------------------------------------------------------------------------
            val pluginContext = SimplePluginContext(rpcClient, config, ipcClient, ipcServer, cli, null)
            val plugins = PluginLoader(pluginContext).load()
            pluginContext.loadedPlugins = plugins
            val controllerContext = ControllerContext(ownExecutable, config, pluginContext, plugins)

            // 4. Transferring control to services:
            // =======================================================================================================
            // NOTE(Dan): None of the services we launch here are supposed to block the current thread of execution.
            // They should instead launch in a new coroutine or thread. As a result, the code ends up being fairly
            // linear when not counting the null checks.

            if (ipcServer != null && rpcClient != null) IpcToUCloudProxyServer().init(ipcServer, rpcClient)
            if (ipcServer != null && rpcClient != null) ProcessingScope.launch { ipcServer.runServer() }
            ipcClient?.connect()
            ipcPingPong.start() // Depends on ipcClient being initialized first
            envoyConfig?.start(config.server?.port)
            processWatcher?.initialize()

            if (config.serverMode == ServerMode.Server || config.serverMode == ServerMode.User) {
                plugins.compute?.plugins?.values?.forEach { plugin ->
                    ProcessingScope.launch {
                        with(pluginContext) {
                            with(plugin) {
                                runMonitoringLoop()
                            }
                        }
                    }

                }
            }

            if (serverMode is ServerMode.Plugin) cli?.execute(serverMode.name)

            if (rpcServer != null) {
                rpcServer.configureControllers(
                    controllerContext,
                    FileController(controllerContext, envoyConfig),
                    FileCollectionController(controllerContext),
                    ComputeController(controllerContext),
                    ConnectionController(controllerContext, envoyConfig),
                    AccountingController(controllerContext),
                    ProjectController(controllerContext)
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


fun <R : Any, S : Any, E : Any> CallDescription<R, S, E>.callBlocking(
    request: R,
    client: AuthenticatedClient,
): IngoingCallResponse<S, E> {
    return runBlocking {
        call(request, client)
    }
}

private val databaseConfig = atomic("")

@ThreadLocal
val dbConnection: DBContext.Connection by lazy {
    val dbConfig = databaseConfig.value.takeIf { it.isNotBlank() }
    if (dbConfig == null) {
        error("This plugin does not have access to a database")
    } else {
        Sqlite3Driver(dbConfig).openSession()
    }
}

private fun readSelfExecutablePath(): String {
    val resultBuffer = ByteArray(2048)
    resultBuffer.usePinned { pinned ->
        val read = readlink("/proc/self/exe", pinned.addressOf(0), resultBuffer.size.toULong())
        return when {
            read == resultBuffer.size.toLong() -> {
                throw IllegalStateException("Path to own executable is too long")
            }
            read != -1L -> {
                resultBuffer.decodeToString(0, read.toInt())
            }
            else -> {
                throw IllegalStateException("Could not read self executable path")
            }
        }
    }
}

object ProcessingScope : CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = job + newFixedThreadPoolContext(30, "Processing")
}

private fun RpcServer.configureControllers(ctx: ControllerContext, vararg controllers: Controller) {
    controllers.forEach { with(it) { configure() } }

    val ipcServer = ctx.pluginContext.ipcServerOptional
    if (ipcServer != null) {
        controllers.forEach { it.configureIpc(ipcServer) }
    }
}

const val ENVOY_CONFIG_PATH = "/var/run/ucloud/envoy"
