package dk.sdu.cloud

import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.controllers.ComputeController
import dk.sdu.cloud.controllers.Controller
import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.http.H2OServer
import dk.sdu.cloud.http.loadMiddleware
import dk.sdu.cloud.plugins.PluginLoader
import dk.sdu.cloud.plugins.SimplePluginContext
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.*
import kotlinx.coroutines.runBlocking
import sqlite3.sqlite3_open
import kotlin.system.exitProcess

@OptIn(ExperimentalStdlibApi::class, ExperimentalUnsignedTypes::class)
fun main() {
    runBlocking {
        /*
            TODO Binary
            ================================================================================================================

            All dependencies should be statically linked to make sure that this is easy to deploy.

         */

        if (true) {
            val conn = Sqlite3Driver("/tmp/db.sqlite3").openSession()
            conn.withTransaction { session ->
                session.prepareStatement("create table test(greeting text)").invokeAndDiscard()
                val testInsert = session.prepareStatement("insert into test values(:greeting)")
                testInsert.invokeAndDiscard {
                    bindString("greeting", "fie.dog")
                }

                val statement = session.prepareStatement("""
                    select :bool, :int, :long, :null, :double, :string, 42
                """)
                statement.invoke(
                    prepare = {
                        bindBoolean("bool", true)
                        bindInt("int", 42)
                        bindLong("long", 1337)
                        bindNull("null")
                        bindDouble("double", 13.37)
                        bindString("string", "fie.dog")
                    },
                    readRow = {
                        println(it.getBoolean(0))
                        println(it.getInt(1))
                        println(it.getLong(2))
                        println(it.getString(3))
                        println(it.getDouble(4))
                        println(it.getString(5))
                        println(it.getInt(6))
                    }
                )
            }
            exitProcess(0)
        }

        val log = Logger("Main")
        val server = H2OServer()
        val config = IMConfiguration.load()
        val validation = NativeJWTValidation(config.core.certificate!!)
        loadMiddleware(config, validation)

        val client = RpcClient().also { client ->
            OutgoingHttpRequestInterceptor()
                .install(
                    client,
                    FixedOutgoingHostResolver(HostInfo("localhost", "http", 8080))
                )
        }

        val authenticator = RefreshingJWTAuthenticator(
            client,
            JwtRefresher.Provider(config.core.refreshToken),
            becomesInvalidSoon = { accessToken ->
                val expiresAt = validation.validateOrNull(accessToken)?.expiresAt
                (expiresAt ?: return@RefreshingJWTAuthenticator true) +
                    (1000 * 120) >= Time.now()
            }
        )

        val providerClient = authenticator.authenticateClient(OutgoingHttpCall)
        val plugins = PluginLoader(config).load().freeze()
        val pluginContext = SimplePluginContext(providerClient).freeze()
        val controllerContext = ControllerContext(pluginContext, plugins)

        with(server) {
            configureControllers(
                ComputeController(controllerContext),
            )
        }

        server.start()
    }
}

private fun H2OServer.configureControllers(vararg controllers: Controller) {
    controllers.forEach { with(it) { configure() } }
}
