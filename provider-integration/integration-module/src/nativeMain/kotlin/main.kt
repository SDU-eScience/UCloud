package dk.sdu.cloud

import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.cli.CommandLineInterface
import dk.sdu.cloud.controllers.*
import dk.sdu.cloud.file.orchestrator.api.Files
import dk.sdu.cloud.file.orchestrator.api.UFileIncludeFlags
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
import dk.sdu.cloud.sql.migrations.loadMigrations
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import platform.posix.*
import kotlin.coroutines.CoroutineContext
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.system.exitProcess

sealed class ServerMode {
    object User : ServerMode()
    object Server : ServerMode()
    object FrontendProxy : ServerMode()
    data class Plugin(val name: String) : ServerMode()
}

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

@OptIn(ExperimentalStdlibApi::class, ExperimentalUnsignedTypes::class)
fun main(args: Array<String>) {


        //https://tldp.org/LDP/lpg/node143.html

        // //allocates a virtual device and a file descriptor 
        // val fd = posix_openpt(O_RDWR)
        // println(fd)

        // println(ptsname(fd)?.toKString())

        // grantpt(fd)
        // unlockpt(fd)

        // val session = setsid()

        // println(session)
        
        // val ioctl: Int = ioctl(fd, TIOCSCTTY)

        // val process = startProcess(
        //     args = listOf(
        //         "/usr/bin/sudo",
        //         "-u",
        //         "testuser",
        //         "/usr/bin/ssh",
        //         "c1",
        //         "([ -x /bin/bash ] && exec /bin/bash) || " +
        //         "([ -x /usr/bin/bash ] && exec /usr/bin/bash) || " +
        //         "([ -x /bin/zsh ] && exec /bin/zsh) || " +
        //         "([ -x /usr/bin/zsh ] && exec /usr/bin/zsh) || " +
        //         "([ -x /bin/fish ] && exec /bin/fish) || " +
        //         "([ -x /usr/bin/fish ] && exec /usr/bin/fish) || " +
        //         "exec /bin/sh "
        //     ),
        //     envs = listOf("TERM=xterm-256color"),
        //     attachStdin = true,
        //     attachStdout = true,
        //     attachStderr = true,
        //     nonBlockingStdout = true,
        //     nonBlockingStderr = true
        // )

        // val cmd = "hostname \n env \n ".encodeToByteArray()
        // val cmdend = "stty cols 1200 rows 1200 \r stty size \r ".encodeToByteArray()
        // process!!.stdin!!.write(cmd)
        // process!!.stdin!!.write(cmdend)

        // sleep(5)
        // var buffer = ByteArray(2048 * 2048)
        // //while(true) {
        //     val stdOut = process!!.stdout!!.read(buffer)
        //     if ( !buffer.decodeToString().isNullOrEmpty() ) println("THIS IS: ${ buffer.decodeToString() }")
        // //}

        // sleep(100000000)
        // exitProcess(0)




    try {
        val serverMode = when {
            args.getOrNull(0) == "user" -> ServerMode.User
            args.getOrNull(0) == "server" || args.isEmpty() -> ServerMode.Server
            args.getOrNull(0) == "proxy" -> ServerMode.FrontendProxy
            else -> ServerMode.Plugin(args[0])
        }

        val ownExecutable = readSelfExecutablePath()
        //signal(SIGCHLD, SIG_IGN) // Automatically reap children - commenting out as currently interferes with execve.kt
        signal(SIGPIPE, SIG_IGN) // Our code already correctly handles EPIPE. There is no need for using the signal.

        runBlocking {
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

            // Verify that we didn't manage to read parts of the configuration we shouldn't be able to
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

            val validation = NativeJWTValidation(config.core.certificate!!)
            loadMiddleware(config, validation)

            if (config.server != null && serverMode == ServerMode.Server) {
                databaseConfig.getAndSet(config.server.dbFile)

                // NOTE(Dan): It is important that migrations run _before_ plugins are loaded
                val handler = MigrationHandler(dbConnection)
                loadMigrations(handler)
                handler.migrate()
            }

            val rpcServerPort = when (serverMode) {
                is ServerMode.Plugin, ServerMode.FrontendProxy -> null
                ServerMode.Server -> UCLOUD_IM_PORT
                ServerMode.User -> args.getOrNull(1)?.toInt() ?: error("Missing port argument for user server")
            }

            val rpcServer = when (serverMode) {
                ServerMode.Server, ServerMode.User -> RpcServer(rpcServerPort!!)
                else -> null
            }

            val ipcSocketDirectory = config.core.ipcDirectory ?: config.configLocation
            val ipcClient = when (serverMode) {
                ServerMode.Server, ServerMode.FrontendProxy -> null
                else -> IpcClient(ipcSocketDirectory)
            }

            val providerClient = run {
                when (serverMode) {
                    ServerMode.Server -> {
                        val serverConfig = config.server!!
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
                        val cfg = config.frontendProxy!!
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

            val ipcServer = when (serverMode) {
                ServerMode.Server, ServerMode.FrontendProxy -> IpcServer(
                    ipcSocketDirectory,
                    config.frontendProxy!!,
                    providerClient!!,
                    rpcServer
                )
                else -> null
            }
            val cli = if (serverMode !is ServerMode.Plugin) null else CommandLineInterface(args.drop(1))

            if (ipcServer != null && providerClient != null) {
                IpcToUCloudProxyServer().init(ipcServer, providerClient)
            }

            val pluginContext = SimplePluginContext(
                providerClient,
                config,
                ipcClient,
                ipcServer,
                cli
            )
            val plugins = PluginLoader(pluginContext).load()

            val controllerContext = ControllerContext(ownExecutable, config, pluginContext, plugins)

            // Start services
            if (ipcServer != null && providerClient != null) {
                ProcessingScope.launch { ipcServer.runServer() }
            }

            ipcClient?.connect()

            val envoyConfig = if (serverMode == ServerMode.Server) {
                EnvoyConfigurationService(ENVOY_CONFIG_PATH)
            } else {
                null
            }

            envoyConfig?.start(config.server?.port)

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

            when (serverMode) {
                ServerMode.Server, ServerMode.User -> {
                    if (rpcServer != null) {
                        rpcServer.configureControllers(
                            controllerContext,
                            FileController(controllerContext, envoyConfig),
                            FileCollectionController(controllerContext),
                            ComputeController(controllerContext),
                            ConnectionController(controllerContext, envoyConfig),
                            AccountingController(controllerContext)
                        )

                        rpcServer.start()
                    }
                }

                is ServerMode.Plugin -> {
                    cli!!.execute(serverMode.name)
                }

                ServerMode.FrontendProxy -> {
                    // Just spin, the IPC server is running in a separate thread
                    while (isActive) {
                        delay(50)
                    }
                }
            }
        }
    } catch (ex: Throwable) {
        ex.printStackTrace()
        println("Good bye")
    }
}

private fun RpcServer.configureControllers(ctx: ControllerContext, vararg controllers: Controller) {
    controllers.forEach { with(it) { configure() } }

    val ipcServer = ctx.pluginContext.ipcServerOptional
    if (ipcServer != null) {
        controllers.forEach { it.configureIpc(ipcServer) }
    }
}

const val ENVOY_CONFIG_PATH = "/var/run/ucloud/envoy"