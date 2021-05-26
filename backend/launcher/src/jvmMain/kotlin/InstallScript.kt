package dk.sdu.cloud

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.escapeHtml
import dk.sdu.cloud.service.findValidHostname
import dk.sdu.cloud.service.k8.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.*
import java.nio.file.Files
import kotlin.system.exitProcess

enum class InstallStep {
    INIT,
    DATABASE,
    MIGRATION,
    MIGRATION_OUTPUT,
    DATABASES,
    DONE
}

private var errorMessage: String? = null

private var hostname = "postgres"
private var port = 5432
private var username = "postgres"
private var password = "postgrespassword"
private var database = "postgres"

const val installerFile = "installing.yaml"

fun runInstaller(configDir: File) {
    var step = InstallStep.INIT

    suspend fun Application.checkDatabaseConfig(): Boolean {
        val dbConfig = retrieveDatabaseConfig()

        val db = AsyncDBSessionFactory(dbConfig)
        var success = false
        try {
            db.withTransaction { session ->
                session.sendPreparedStatement({}, "select 1").rows.map {
                    success = true
                }
            }
        } catch (ex: Throwable) {
            errorMessage = "Could not connect to postgresql (${ex.javaClass.simpleName} ${ex.message})"
            log.warn(ex.stackTraceToString())
        } finally {
            db.close()
        }

        if (success) {
            step = InstallStep.MIGRATION
            File(configDir, "db.yaml").writeText(
                """
                    database:
                        credentials:
                            username: ${username}
                            password: ${password}
                        database: ${database}
                        port: ${port}
                        hostname: ${hostname}
                            
                """.trimIndent()
            )
        }
        return success
    }

    embeddedServer(Netty, port = 8080) {
        File(configDir, installerFile).writeText("installing: true")
        File(configDir, "common.yaml").writeText(
            """
                refreshToken: theverysecretadmintoken
                tokenValidation:
                  jwt:
                    sharedSecret: notverysecret
                
                rpc:
                   client:
                      host:
                         host: localhost
                         port: 8080

            """.trimIndent()
        )

        val cephFs = File("/mnt/cephfs")
        if (cephFs.exists()) {
            File(cephFs, "home").mkdir()
            File(cephFs, "projects").mkdir()
            File(cephFs, "home/user").mkdir() // TODO Temporary
            File(cephFs, "home/user/Jobs").mkdir() // TODO Temporary
        }

        routing {
            get("/i") {
                when (step) {
                    InstallStep.INIT -> {
                        call.respondText(ContentType.Text.Html) {
                            page {
                                //language=HTML
                                """
                                    <h1>UCloud Installer</h1>
                                    <p>
                                        Welcome! It looks like this is a new UCloud installation running in development
                                        mode. If this is not the case, then please make sure that UCloud is pointing
                                        at the correct configuration (This can be changed with
                                        <code>--config-dir</code>).
                                    </p>
                                    
                                    <p>This installation wizard will take you through the process of configuring
                                       UCloud.</p>
                                       
                                    <a href='/i/next' style='width: 100%' class='button'>Continue</a>
                                """
                            }
                        }
                    }

                    InstallStep.DATABASE -> {
                        call.respondText(ContentType.Text.Html) {
                            if (hostname == "") hostname = findValidHostname(listOf("postgres", "localhost")) ?: ""

                            page {
                                //language=HTML
                                """
                                    <h1>Database Connection</h1>
                                    <p>UCloud depends on PostgreSQL as its database. Please fill in the correct
                                       details below (some values were auto-detected)</p>
                                       
                                    <form action='/i/next' method='post'>
                                    <label>
                                        Hostname:
                                        <input type='text' name='hostname' value='${escapeHtml(hostname ?: "")}'>
                                    </label>
                                    <label>
                                        Port:
                                        <input type='text' name='port' value='${escapeHtml(port.toString())}'>
                                    </label>
                                    <label>
                                        Database:
                                        <input type='text' name='database' value='${escapeHtml(database)}'>
                                    </label>
                                    <label>
                                        Username:
                                        <input type='text' name='username' value='${escapeHtml(username)}'>
                                    </label>
                                    <label>
                                        Password:
                                        <input type='password' name='password' value='${escapeHtml(password)}'>
                                    </label>
                                    <button style='margin-top: 16px;' type='submit' class='button'>Submit</button>
                                    </form>
                                """
                            }
                        }
                    }

                    InstallStep.MIGRATION -> {
                        call.respondText(ContentType.Text.Html) {
                            page {
                                //language=html
                                """
                                    <h1>Database Migrations</h1>
                                    <p>Database successfully configured! Click the button below to initialize the
                                       UCloud database</p>
                                    <a href='/i/next' class='button' style='margin-top: 16px;'>
                                        Initialize UCloud database
                                    </a>
                                """
                            }
                        }
                    }

                    InstallStep.MIGRATION_OUTPUT -> {
                        call.respondText(ContentType.Text.Html) {
                            page {
                                //language=html
                                """
                                     <meta http-equiv="refresh" content="5; URL=/i">
                                    <h1>Database Migrations</h1>
                                    <p>The UCloud database is currently being initialized. 
                                        See log for details.</p>
                                    <a href='/i/next' class='button' style='margin-top: 16px;'>
                                        Continue
                                    </a>
                                """
                            }
                        }
                    }

                    InstallStep.DATABASES -> {

                        call.respondText(ContentType.Text.Html) {
                            page {
                                //language=html
                                """
                                    <h1>External Services</h1>
                                    <p>UCloud will now verify that it can establish a connection to all required
                                        services.</p>
                                    <a href='/i/next' class='button' style='margin-top: 16px;'>
                                        Continue
                                    </a>
                                """
                            }
                        }
                    }

                    InstallStep.DONE -> {
                        call.respondText(ContentType.Text.Html) {
                            launch {
                                File(configDir, installerFile).writeText("installing: false\npostInstalling: true")
                                repeat(30) { println() }
                                println("Please restart UCloud if this doesn't happen automatically")
                                delay(3000)
                                exitProcess(0)
                            }

                            page {
                                //language=html
                                """
                                    <h1>Database migration</h1>
                                    <p>Database has been successfully initialized! UCloud will now restart.</p>
                                    <p>You should reload this page until the UCloud interface becomes ready. 
                                       This usually takes around 30 seconds. Consult the backend logs for more
                                       information.</p>
                                """
                            }
                        }
                    }
                }
            }

            get("/i/next") {
                step = when (step) {
                    InstallStep.INIT -> {
                        if (checkDatabaseConfig()) {
                            InstallStep.MIGRATION
                        } else {
                            InstallStep.DATABASE
                        }
                    }
                    InstallStep.MIGRATION -> {
                        Thread {
                            val micro = Micro().apply {
                                init(
                                    object : ServiceDescription {
                                        override val name: String = "launcher"
                                        override val version: String = "1"
                                    },
                                    arrayOf("--dev", "--config-dir", configDir.absolutePath)
                                )
                                install(DeinitFeature)
                                install(ScriptFeature)
                                install(ConfigurationFeature)
                                install(DatabaseConfigurationFeature)
                                install(FlywayFeature)
                            }

                            micro.databaseConfig.migrateAll()

                            val db = AsyncDBSessionFactory(micro.databaseConfig)
                            runBlocking {
                                try {
                                    db.withTransaction { session ->
                                        session.sendPreparedStatement(
                                            """
                                                insert into auth.principals
                                                    (dtype, id, created_at, modified_at, role, first_names, last_name, 
                                                    orc_id,phone_number, title, hashed_password, salt, org_id, email)
                                                values
                                                    ('PASSWORD', 'admin@dev', now(), now(), 'ADMIN', 'Admin', 'Dev',
                                                    null, null, null, E'\\xDEADBEEF', E'\\xDEADBEEF', null, 'admin@dev')
                                            """
                                        )

                                        session.sendPreparedStatement(
                                            """
                                                insert into auth.refresh_tokens
                                                    (token, associated_user_id, csrf, public_session_reference, 
                                                    extended_by, scopes, expires_after, refresh_token_expiry,
                                                    extended_by_chain, created_at, ip, user_agent)
                                                values
                                                    ('theverysecretadmintoken', 'admin@dev', 'csrf', 'initial', null,
                                                    '["all:write"]'::jsonb, 31536000000, null, '[]'::jsonb, now(),
                                                    '127.0.0.1', 'UCloud');                                                
                                            """
                                        )
                                    }
                                } finally {
                                    db.close()
                                }
                            }

                            step = InstallStep.DATABASES
                        }.start()

                        InstallStep.MIGRATION_OUTPUT
                    }
                    InstallStep.DATABASES -> {
                        var success = true
                        errorMessage = ""

                        // Kubernetes
                        try {
                            KubernetesClient().listResources<Node>(KubernetesResources.node)
                        } catch (ex: Throwable) {
                            success = false
                            errorMessage = "Could not connect connect to Kubernetes: " +
                                "${ex.javaClass.simpleName} ${ex.message}"
                        }


                        // ElasticSearch
                        try {
                            val micro = Micro().apply {
                                init(
                                    object : ServiceDescription {
                                        override val name: String = "launcher"
                                        override val version: String = "1"
                                    },
                                    arrayOf("--dev", "--config-dir", configDir.absolutePath)
                                )

                                install(DeinitFeature)
                                install(ScriptFeature)
                                install(ConfigurationFeature)
                                install(ElasticFeature)
                            }

                            micro.elasticLowLevelClient.nodes
                        } catch (ex: Throwable) {
                            success = false
                            errorMessage = "Could not connect connect to ElasticSearch: " +
                                "${ex.javaClass.simpleName} ${ex.message}"
                        }

                        // Redis
                        try {
                            val micro = Micro().apply {
                                init(
                                    object : ServiceDescription {
                                        override val name: String = "launcher"
                                        override val version: String = "1"
                                    },
                                    arrayOf("--dev", "--config-dir", configDir.absolutePath)
                                )

                                install(DeinitFeature)
                                install(ScriptFeature)
                                install(ServiceInstanceFeature)
                                install(ConfigurationFeature)
                                install(RedisFeature)
                            }

                            val conn = micro.redisConnectionManager.getSync()
                            val s = "testing"
                            conn.set(s, s)
                            require(conn.get(s) == s) { "Redis returned invalid output" }
                            conn.del(s)
                        } catch (ex: Throwable) {
                            success = false
                            errorMessage = "Could not connect connect to Redis: " +
                                "${ex.javaClass.simpleName} ${ex.message}"
                        }

                        if (success) {
                            errorMessage = null

                            val tempFile = Files.createTempFile("volcano", ".yml").toFile()
                            tempFile.writeBytes(
                                javaClass.classLoader.getResourceAsStream("volcano.yml").readAllBytes()
                            )
                            var volcanoSuccess = false
                            try {
                                val k8Client = KubernetesClient()
                                val authenticationMethod = k8Client.conn.authenticationMethod
                                if (authenticationMethod is KubernetesAuthenticationMethod.Proxy) {
                                    with(authenticationMethod) {
                                        val statusCode = ProcessBuilder(
                                            *buildList {
                                                add("kubectl")
                                                if (context != null) {
                                                    add("--context")
                                                    add(context)
                                                }
                                                if (configFile != null) {
                                                    add("--kubeconfig")
                                                    add(configFile)
                                                }
                                                add("create")
                                                add("-f")
                                                add(tempFile.absolutePath)
                                            }.toTypedArray()
                                        ).start().also { process ->
                                            Runtime.getRuntime().addShutdownHook(object : Thread() {
                                                override fun run() {
                                                    process.destroyForcibly()
                                                }
                                            })
                                        }.waitFor()

                                        if (statusCode == 0) volcanoSuccess = true
                                    }
                                }
                            } catch (ex: Throwable) {
                                ex.printStackTrace()
                            }

                            if (!volcanoSuccess) {
                                errorMessage = "Please run the following command to finish the installation: " +
                                    "kubectl create -f \"${tempFile.absolutePath}\""
                            }
                        }

                        if (success) InstallStep.DONE else step
                    }
                    else -> step
                }

                call.respondRedirect("/i")
            }

            post("/i/next") {
                when (step) {
                    InstallStep.DATABASE -> {
                        val params = call.receiveParameters()
                        username = params["username"] ?: ""
                        password = params["password"] ?: ""
                        hostname = params["hostname"] ?: ""
                        port = params["port"]?.toIntOrNull() ?: 0
                        database = params["database"] ?: ""

                        if (username == "" || password == "" || hostname == "" || port == 0) {
                            errorMessage = "Missing value in DB config"
                            call.respondRedirect("/i")
                            return@post
                        }

                        checkDatabaseConfig()
                        call.respondRedirect("/i")
                    }

                    else -> call.respondRedirect("/i")
                }
            }
        }
    }.start(wait = true)
}



private fun retrieveDatabaseConfig(): DatabaseConfig {
    return DatabaseConfig(
        postgresJdbcUrl(hostname, database, port),
        username,
        password,
        "public",
        false,
    )
}

private fun page(body: () -> String): String {
    return buildString {
        append(installTemplatePre)
        if (errorMessage != null) {
            //language=html
            append("<div class='box'>${escapeHtml(errorMessage ?: "")}</div>")
            errorMessage = null
        }
        append(body())
        append(installTemplatePost)
    }
}

private val logo = "data:image/webp;base64,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" +
    "An62LwAAA="

//language=html
private val installTemplatePre = """
<!DOCTYPE html>
<html lang="en">
<head>
     <meta charset="UTF-8">
     <meta name="viewport" content="width=device-width, user-scalable=no, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0">
     <meta http-equiv="X-UA-Compatible" content="ie=edge">
     <title>UCloud Installer</title>
     <link rel="preconnect" href="https://fonts.gstatic.com">
     <link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;700&family=JetBrains+Mono&display=swap" rel="stylesheet"> 
     <style>
        body {
            font-family: 'IBM Plex Sans', sans-serif;
            font-size: 16px;
            margin: 2em auto;
            padding: 1em;
            max-width: 1000px;
            line-height: 1.5;
            color: #454545;
        }
        
        a, a:visited {
            color: #0077aa;
        }
        
        .box {
            margin: 16px 0;
            border: 2px rgb(102, 102, 102) dashed;
            padding: 16px;
            border-radius: 5px;
            font-family: 'JetBrains Mono', monospace;
        }
        
        input {
            display: block;
            font-family: inherit;
            color: #454545;
            background-color: transparent;
            margin: 0;
            border-width: 2px;
            border-color: rgb(201, 211, 223);
            border-style: solid;
            padding: 7px 12px;
            border-radius: 5px;
            width: calc(100% - 30px);
        }
        
        .button {
            display: inline-flex;
            -webkit-box-pack: center;
            -webkit-justify-content: center;
            -ms-flex-pack: center;
            justify-content: center;
            -webkit-align-items: center;
            -webkit-box-align: center;
            -ms-flex-align: center;
            align-items: center;
            text-align: center;
            -webkit-text-decoration: none;
            text-decoration: none;
            font-family: inherit;
            font-weight: 700;
            line-height: 1.5;
            cursor: pointer;
            border-radius: 5px;
            background-color: #006aff;
            color: #fff !important;
            border-width: 0;
            border-style: solid;
            -webkit-transition: cubic-bezier(0.5,0,0.25,1) 60ms;
            transition: cubic-bezier(0.5,0,0.25,1) 60ms;
            font-size: 14px;
            padding: 9.5px 18px;
            width: 100%;
        }
     </style>
</head>
<body>
    <div style='display: flex; justify-content: center'>
        <img alt='logo' src='$logo'>
    </div>

"""

private val installTemplatePost = """
</body>
</html>
"""
