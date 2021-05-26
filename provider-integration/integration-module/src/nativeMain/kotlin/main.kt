package dk.sdu.cloud

import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.cli.CommandLineInterface
import dk.sdu.cloud.controllers.*
import dk.sdu.cloud.http.H2OServer
import dk.sdu.cloud.http.loadMiddleware
import dk.sdu.cloud.ipc.*
import dk.sdu.cloud.plugins.PluginLoader
import dk.sdu.cloud.plugins.SimplePluginContext
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.*
import dk.sdu.cloud.sql.migrations.loadMigrations
import dk.sdu.cloud.utils.homeDirectory
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import platform.posix.*
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.system.exitProcess

sealed class ServerMode {
    object User : ServerMode()
    object Server : ServerMode()
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

@SharedImmutable
private val databaseConfig = atomic("").freeze()

@ThreadLocal
val dbConnection: DBContext.Connection? by lazy {
    val dbConfig = databaseConfig.value.takeIf { it.isNotBlank() }
    if (dbConfig == null) {
        null
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

@OptIn(ExperimentalStdlibApi::class, ExperimentalUnsignedTypes::class)
fun main(args: Array<String>) {
    val serverMode = when {
        args.getOrNull(0) == "user" -> ServerMode.User
        args.getOrNull(0) == "server" || args.isEmpty() -> ServerMode.Server
        else -> ServerMode.Plugin(args[0])
    }

    val ownExecutable = readSelfExecutablePath()
    signal(SIGCHLD, SIG_IGN) // Automatically reap children
    signal(SIGPIPE, SIG_IGN) // Our code already correctly handles EPIPE. There is no need for using the signal.

    runBlocking {
        val config = try {
            IMConfiguration.load(serverMode)
        } catch (ex: ConfigurationException.IsBeingInstalled) {
            runInstaller(ex.core, ex.server, ownExecutable)
            exitProcess(0)
        } catch (ex: ConfigurationException.BadConfiguration) {
            println(ex.message)
            exitProcess(1)
        }

        val validation = NativeJWTValidation(config.core.certificate!!)
        loadMiddleware(config, validation)

        if (config.server != null && serverMode == ServerMode.Server) {
            databaseConfig.getAndSet(config.server.dbFile)

            // NOTE(Dan): It is important that migrations run _before_ plugins are loaded
            val handler = MigrationHandler(dbConnection!!)
            loadMigrations(handler)
            handler.migrate()
        }

        val ipcSocketDirectory = config.core.ipcDirectory ?: config.configLocation
        val ipcServer = if (serverMode != ServerMode.Server) null else IpcServer(ipcSocketDirectory)
        val ipcClient = if (serverMode == ServerMode.Server) null else IpcClient(ipcSocketDirectory)
        val cli = if (serverMode !is ServerMode.Plugin) null else CommandLineInterface(args.drop(1))

        val rpcServerPort = when (serverMode) {
            is ServerMode.Plugin -> null
            ServerMode.Server -> UCLOUD_IM_PORT
            ServerMode.User -> args.getOrNull(1)?.toInt() ?: error("Missing port argument for user server")
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
                        JwtRefresher.Provider(serverConfig.refreshToken),
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

                is ServerMode.Plugin -> null
            }
        }

        if (ipcServer != null && providerClient != null) {
            IpcProxyServer().init(ipcServer, providerClient)
        }

        val pluginContext = SimplePluginContext(
            providerClient,
            config,
            ipcClient,
            ipcServer,
            cli
        )
        val plugins = PluginLoader(pluginContext).load()

        plugins.freeze()
        pluginContext.freeze()

        val controllerContext = ControllerContext(ownExecutable, config, pluginContext, plugins)

        // Start services
        if (ipcServer != null && providerClient != null) {
            Worker.start(name = "IPC Accept Worker").execute(TransferMode.SAFE, { ipcServer }) { it.runServer() }
        }

        ipcClient?.connect()

        val envoyConfig = if (serverMode == ServerMode.Server) {
            EnvoyConfigurationService(ENVOY_CONFIG_PATH)
        } else {
            null
        }

        envoyConfig?.start(config.server?.port)

        when (serverMode) {
            ServerMode.Server, ServerMode.User -> {
                val server = H2OServer(rpcServerPort!!)
                with(server) {
                    configureControllers(
                        ComputeController(controllerContext),
                        ConnectionController(controllerContext, envoyConfig)
                    )
                }

                server.start()
            }

            is ServerMode.Plugin -> {
                cli!!.execute(serverMode.name)
            }
        }
    }
}

private fun H2OServer.configureControllers(vararg controllers: Controller) {
    controllers.forEach { with(it) { configure() } }
}

const val ENVOY_CONFIG_PATH = "/var/run/ucloud/envoy"