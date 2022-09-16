package dk.sdu.cloud.cli

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.plugins.ipcServer
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.sql.JdbcConnection
import dk.sdu.cloud.sql.JdbcDriver
import dk.sdu.cloud.sql.SimpleConnectionPool
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.sql.DriverManager

fun PsqlTestCli(controllerContext: ControllerContext) {
    val pluginContext = controllerContext.pluginContext
    val config = pluginContext.config
    pluginContext.commandLineInterface?.addHandler(CliHandler("psql") { args ->
        val ipcClient = pluginContext.ipcClient
        genericCommandLineHandler {
            when (args.getOrNull(0)) {
                "initTest" -> ipcClient.sendRequest(PsqlTestIpc.initTest, Unit)
                "insertTest" -> ipcClient.sendRequest(PsqlTestIpc.insertTest, ValueToInsert(args[1], args[2].toInt()))
                "selectTest" -> ipcClient.sendRequest(PsqlTestIpc.selectTest, Unit)
            }
        }
    })

    if (config.shouldRunServerCode()) {
        val rpcClient = pluginContext.rpcClient
        val ipcServer = pluginContext.ipcServer

        val embeddedPostgres = EmbeddedPostgres.builder().apply {
            setCleanDataDirectory(false)

            val workDir = File("/tmp/postgres")
            setDataDirectory(File(workDir, "data").also { it.mkdirs() })
            setOverrideWorkingDirectory(workDir)
        }.start()

        val db = object : JdbcDriver() {
            override val pool: SimpleConnectionPool = SimpleConnectionPool(8) { pool ->
                JdbcConnection(
                    DriverManager.getConnection(embeddedPostgres.getJdbcUrl("postgres", "postgres")),
                    pool
                )
            }
        }

        ipcServer.addHandler(PsqlTestIpc.initTest.handler { user, _ ->
            if (user.uid != 0) throw RPCException("Root is required for this script", HttpStatusCode.Forbidden)

            db.withSession { session ->
                session.prepareStatement(
                    """
                        create table if not exists my_data_table(
                            foo text primary key,
                            value int not null
                        )
                    """
                ).useAndInvokeAndDiscard()
            }
        })

        ipcServer.addHandler(PsqlTestIpc.insertTest.handler { user, request ->
            if (user.uid != 0) throw RPCException("Root is required for this script", HttpStatusCode.Forbidden)

            db.withSession { session ->
                session.prepareStatement(
                    """
                        insert into my_data_table(foo, value) values (:foo, :value)
                    """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindString("foo", request.foo)
                        bindInt("value", request.value)
                    }
                )
            }
        })

        ipcServer.addHandler(PsqlTestIpc.selectTest.handler { user, _ ->
            if (user.uid != 0) throw RPCException("Root is required for this script", HttpStatusCode.Forbidden)

            db.withSession { session ->
                session.prepareStatement(
                    """
                        select foo, value
                        from my_data_table
                    """
                ).useAndInvoke(
                    prepare = {

                    },
                    readRow = { row ->
                        val foo = row.getString(0)!!
                        val value = row.getInt(1)!!
                        println("$foo $value")
                    }
                )
            }
        })
    }
}

@Serializable
data class ValueToInsert(val foo: String, val value: Int)

private object PsqlTestIpc : IpcContainer("psqltest") {
    val initTest = updateHandler("initTest", Unit.serializer(), Unit.serializer())
    val insertTest = updateHandler("insertTest", ValueToInsert.serializer(), Unit.serializer())
    val selectTest = updateHandler("selectTest", Unit.serializer(), Unit.serializer())
}

