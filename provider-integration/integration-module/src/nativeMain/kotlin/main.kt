package dk.sdu.cloud

import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.controllers.ComputeController
import dk.sdu.cloud.controllers.ConnectionController
import dk.sdu.cloud.controllers.Controller
import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.http.H2OServer
import dk.sdu.cloud.http.loadMiddleware
import dk.sdu.cloud.plugins.PluginLoader
import dk.sdu.cloud.plugins.SimplePluginContext
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.*
import dk.sdu.cloud.sql.migrations.loadMigrations
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.runBlocking

enum class ServerMode {
    USER,
    SERVER
}

private val databaseConfig = atomic("")
@ThreadLocal val dbConnection: DBContext.Connection? by lazy {
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
        args.contains("user") -> ServerMode.USER
        args.contains("server") -> ServerMode.SERVER
        else -> throw IllegalArgumentException("Missing server mode")
    }

    runBlocking {
        val log = Logger("Main")
        val server = H2OServer()
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
                ServerMode.SERVER -> {
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

                ServerMode.USER -> {
                    AuthenticatedClient(client, OutgoingHttpCall, null, {})
                }
            }
        }

        if (config.server != null) {
            databaseConfig.getAndSet(config.server.dbFile)

            // NOTE(Dan): It is important that migrations run _before_ plugins are loaded
            val handler = MigrationHandler(dbConnection!!)
            loadMigrations(handler)
            handler.migrate()
        }

        val plugins = PluginLoader(config).load().freeze()
        val pluginContext = SimplePluginContext(providerClient, config).freeze()
        val controllerContext = ControllerContext(config, pluginContext, plugins)

        with(server) {
            configureControllers(
                ComputeController(controllerContext),
                ConnectionController(controllerContext)
            )
        }

        server.start()
    }
}

private fun H2OServer.configureControllers(vararg controllers: Controller) {
    controllers.forEach { with(it) { configure() } }
}
