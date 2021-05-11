package dk.sdu.cloud

import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.cli.CommandLineInterface
import dk.sdu.cloud.controllers.ComputeController
import dk.sdu.cloud.controllers.ConnectionController
import dk.sdu.cloud.controllers.Controller
import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.http.H2OServer
import dk.sdu.cloud.http.loadMiddleware
import dk.sdu.cloud.ipc.IpcClient
import dk.sdu.cloud.ipc.IpcServer
import dk.sdu.cloud.plugins.PluginLoader
import dk.sdu.cloud.plugins.SimplePluginContext
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.*
import dk.sdu.cloud.sql.migrations.loadMigrations
import dk.sdu.cloud.utils.homeDirectory
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.runBlocking
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker

sealed class ServerMode {
    object User : ServerMode()
    object Server : ServerMode()
    data class Plugin(val name: String) : ServerMode()
}

fun <R : Any, S : Any, E : Any> CallDescription<R, S, E>.callBlocking(
    request: R,
    client: AuthenticatedClient
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

@OptIn(ExperimentalStdlibApi::class, ExperimentalUnsignedTypes::class)
fun main(args: Array<String>) {
    val serverMode = when {
        args.contains("user") -> ServerMode.User
        args.contains("server") -> ServerMode.Server
        args.size >= 1 -> ServerMode.Plugin(args[0])
        else -> throw IllegalArgumentException("Missing server mode")
    }

    runBlocking {
        val log = Logger("Main")
        val config = IMConfiguration.load(serverMode)
        val validation = NativeJWTValidation(config.core.certificate!!)
        loadMiddleware(config, validation)

        val client = RpcClient().also { client ->
            OutgoingHttpRequestInterceptor()
                .install(
                    client,
                    FixedOutgoingHostResolver(HostInfo("localhost", "http", 8080))
                )
        }

        val providerClient = run {
            when (serverMode) {
                ServerMode.Server -> {
                    val authenticator = RefreshingJWTAuthenticator(
                        client,
                        JwtRefresher.Provider(config.server!!.refreshToken),
                        becomesInvalidSoon = { accessToken ->
                            val expiresAt = validation.validateOrNull(accessToken)?.expiresAt
                            (expiresAt ?: return@RefreshingJWTAuthenticator true) +
                                (1000 * 120) >= Time.now()
                        }
                    )

                    authenticator.authenticateClient(OutgoingHttpCall)
                }

                ServerMode.User -> {
                    AuthenticatedClient(client, OutgoingHttpCall, null, {})
                }

                is ServerMode.Plugin -> null
            }
        }

        if (config.server != null) {
            databaseConfig.getAndSet(config.server.dbFile)

            // NOTE(Dan): It is important that migrations run _before_ plugins are loaded
            val handler = MigrationHandler(dbConnection!!)
            loadMigrations(handler)
            handler.migrate()
        }

        val ipcSocketDirectory = "${homeDirectory()}/ucloud-im"
        val ipcServer = if (serverMode != ServerMode.Server) null else IpcServer(ipcSocketDirectory)
        val ipcClient = if (serverMode == ServerMode.Server) null else IpcClient(ipcSocketDirectory)
        val cli = if (serverMode !is ServerMode.Plugin) null else CommandLineInterface(args.drop(1))

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

        val controllerContext = ControllerContext(config, pluginContext, plugins)

        // Start services
        if (ipcServer != null) {
            Worker.start(name = "IPC Accept Worker").execute(TransferMode.SAFE, { ipcServer }) { it.runServer() }
        }

        ipcClient?.connect()

        when (serverMode) {
            ServerMode.Server, ServerMode.User -> {
                val server = H2OServer()
                with(server) {
                    configureControllers(
                        ComputeController(controllerContext),
                        ConnectionController(controllerContext)
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
