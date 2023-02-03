package dk.sdu.cloud.integration

import com.sun.jna.Platform
import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.alerting.AlertingService
import dk.sdu.cloud.auth.api.AuthenticatorFeature
import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.contact.book.services.ContactBookElasticDao
import dk.sdu.cloud.elastic.management.ElasticManagementService
import dk.sdu.cloud.integration.backend.sampleStorage
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.redis.cleaner.RedisCleanerService
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.EnhancedPreparedStatement
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.services
import dk.sdu.cloud.test.UCloudTest
import kotlinx.coroutines.*
import org.apache.logging.log4j.core.config.ConfigurationFactory
import redis.embedded.RedisExecProvider
import redis.embedded.RedisServer
import redis.embedded.util.OS
import java.io.File
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.util.*
import kotlin.collections.ArrayList

val disableBuiltInDatabases: Boolean
    get() = System.getenv("UCLOUD_INTEGRATION_DB") == "false"
val disableBuiltInPostgres: Boolean
    get() = System.getenv("UCLOUD_INTEGRATION_PSQL") == "false"
val disableBuiltInRedis: Boolean
    get() = System.getenv("UCLOUD_INTEGRATION_REDIS") == "false"

fun findPreferredOutgoingIp(): String {
    return DatagramSocket().use { socket ->
        socket.connect(InetAddress.getByName("1.1.1.1"), 12345)
        socket.localAddress.hostAddress
    }
}

object UCloudLauncher : Loggable {
    init {
        ConfigurationFactory.setConfigurationFactory(Log4j2ConfigFactory)
    }

    override val log = logger()

    var isRunning = false
        private set
    lateinit var db: AsyncDBSessionFactory
    private lateinit var dbConfig: DatabaseConfig

    lateinit var serviceClient: AuthenticatedClient
    lateinit var adminClient: AuthenticatedClient
    private val adminRefreshToken = UUID.randomUUID().toString()

    private val tempDir = if (Platform.isMac()) {
        File(System.getProperty("user.home"), "temp-integration").also { it.deleteOnExit() }
    } else {
        Files.createTempDirectory("integration").toFile().also { it.deleteOnExit() }
    }
    val fsHome = File(tempDir, "cephfs").absolutePath
    private const val REDIS_PORT = 44231
    lateinit var micro: Micro
    private var redisServer: RedisServer? = null
    private lateinit var refreshToken: String

    private fun initializeDatabases() {
        @Suppress("BlockingMethodInNonBlockingContext") val job = GlobalScope.async {
            coroutineScope {
                launch {
                    // Postgres
                    if (!disableBuiltInDatabases && !disableBuiltInPostgres) {
                        TestDB.initializeWithoutService()
                    }
                }

                launch {
                    // Redis
                    if (!disableBuiltInDatabases && !disableBuiltInRedis) {
                        // For macos we always use the same file name as it appears to have some problems with macOS
                        // security features
                        val redisMacOS = File("/tmp/redis-macos").also { f ->
                            if (!f.exists()) {
                                f.outputStream().use { outs ->
                                    javaClass.classLoader.getResourceAsStream("redis-macos")!!
                                        .use { ins -> ins.copyTo(outs) }
                                }
                                f.setExecutable(true)
                            }
                        }

                        val redisLinux = File.createTempFile("redis-linux", "").also { f ->
                            f.outputStream().use { outs ->
                                javaClass.classLoader.getResourceAsStream("redis-linux")!!
                                    .use { ins -> ins.copyTo(outs) }
                            }
                        }
                        redisLinux.setExecutable(true)

                        redisServer = RedisServer(
                            RedisExecProvider.defaultProvider()
                                .override(OS.MAC_OS_X, redisMacOS.absolutePath)
                                .override(OS.UNIX, redisLinux.absolutePath),
                            REDIS_PORT
                        ).also { it.start() }
                    }
                }

                launch {
                    // Ceph or normal file system
                    File(fsHome).mkdirs()
                }
            }
        }

        runBlocking { job.await() }
    }

    data class ProviderTokens(
        val configPath: List<String>,
        val providerId: String,
        val domain: String,
        val port: Int,
        val https: Boolean,
        val publicKey: String,
        val privateKey: String,
        val refreshToken: String,
        val sharedSecret: String,
    ) {
        fun generateConfig(): String = configPath.joinToString("\n") { path ->
            buildString {
                var indent = ""
                path.split(".").forEachIndexed { index, component ->
                    append(indent)
                    append(component)
                    appendLine(":")
                    indent += "  "
                }
                append(indent)
                appendLine("providerRefreshToken: $refreshToken")
                append(indent)
                appendLine("ucloudCertificate: $publicKey")
            }
        }
    }

    private suspend fun generateProviderTokens(
        configPath: List<String>,
        providerId: String,
        domain: String,
        port: Int,
        https: Boolean,
    ): ProviderTokens {
        val random = SecureRandom()
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        val keyPair = keyGen.genKeyPair()
        val public = keyPair.public.encoded.let { Base64.getEncoder().encodeToString(it) }
        val private = keyPair.private.encoded.let { Base64.getEncoder().encodeToString(it) }

        val sharedSecret = ByteArray(64).also { random.nextBytes(it) }.let { Base64.getEncoder().encodeToString(it) }
        val refreshToken = ByteArray(64).also { random.nextBytes(it) }.let { Base64.getEncoder().encodeToString(it) }
        return ProviderTokens(
            configPath,
            providerId,
            domain,
            port,
            https,
            public,
            private,
            refreshToken,
            sharedSecret
        )
    }

    private suspend fun prepareProvider(
        providerTokens: ProviderTokens
    ): Unit = with(providerTokens) {
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("shared_secret", sharedSecret)
                    setParameter("requested_id", providerId)
                    setParameter("domain", domain)
                    setParameter("port", port)
                    setParameter("https", https)
                    setParameter("signed_by", "_ucloud")
                },
                """
                    insert into provider.approval_request (shared_secret, requested_id, domain, https, port, signed_by) 
                    values (:shared_secret, :requested_id, :domain, :https, :port, :signed_by)
                """
            )

            session.sendPreparedStatement(
                {
                    setParameter("shared_secret", sharedSecret)
                    setParameter("public_key", publicKey)
                    setParameter("private_key", privateKey)
                },
                """
                    select refresh_token
                    from provider.approve_request(:shared_secret, :public_key, :private_key, null)
                """
            )

            session.sendPreparedStatement(
                {
                    setParameter("name", providerId)
                    setParameter("refresh_token", refreshToken)
                },
                """
                    update provider.providers
                    set refresh_token = :refresh_token
                    where unique_name = :name
                """
            )

            session.sendPreparedStatement(
                {
                    setParameter("name", providerId)
                    setParameter("refresh_token", refreshToken)
                },
                """
                    update auth.providers
                    set refresh_token = :refresh_token
                    where id = :name
                """
            )
        }

        Unit
    }

    private fun createConfiguration(): File {
        val dir = Files.createTempDirectory("c").toFile().also { println("Configuration at $it") }

        initializeDatabases()

        File(dir, "client.yml").writeText(
            """
                rpc:
                  client:
                    host:
                      host: localhost
                      port: 8080
            """.trimIndent()
        )

        File(dir, "token.yml").writeText(
            """
                ---
                refreshToken: $refreshToken
                tokenValidation:
                  jwt:
                    sharedSecret: notverysecret
            """.trimIndent()
        )

        if (!disableBuiltInDatabases && !disableBuiltInPostgres) {
            val dbConfig = TestDB.getConfigForFeature()
            File(dir, "db.yml").writeText(
                """
                    ---
                    database:
                      profile: PERSISTENT_POSTGRES
                      hostname: ${dbConfig.hostname}
                      port: ${dbConfig.port}
                      database: ${dbConfig.database}
                      credentials:
                        username: ${dbConfig.credentials?.username}
                        password: ${dbConfig.credentials?.password}
                """.trimIndent()
            )
        }

        if (!disableBuiltInDatabases && !disableBuiltInRedis) {
            File(dir, "redis.yml").writeText(
                """
                    ---
                    redis:
                      hostname: localhost
                      port: $REDIS_PORT
                """.trimIndent()
            )
        }

        File(dir, "ceph.yml").writeText(
            """
                ---
                ceph:
                  cephfsBaseMount: $fsHome
            """.trimIndent()
        )

        File(dir, "storage.yml").writeText(
            """
                ---
                storage:
                  product:
                    id: ${sampleStorage.name}
                    category: ${sampleStorage.category.name}
                    provider: ${sampleStorage.category.provider}
                    pricePerGb: ${sampleStorage.pricePerUnit}
                    defaultQuota: -1
            """.trimIndent()
        )

        providers.forEach {
            File(dir, it.providerId + ".yaml").writeText(it.generateConfig())
        }

        return dir
    }

    private fun shutdown() {
        redisServer?.stop()
        TestDB.close()
    }

    suspend fun wipeDatabases() {
        File(fsHome, "home").apply {
            require(deleteRecursively())
            require(mkdirs()) { "Unable to create directories: ${this}" }
        }
        File(fsHome, "projects").apply {
            require(deleteRecursively())
            require(mkdirs())
        }

        db.withTransaction { session ->
            session.sendQuery("select public.truncate_tables()")
        }

        val sequenceNames = ArrayList<String>()

        db.withTransaction { session ->
            session.sendPreparedStatement("""
                SELECT sequence_schema, sequence_name 
                FROM information_schema.sequences 
                ORDER BY sequence_name
            """.trimIndent())
        }.rows.forEach {
            sequenceNames.add(""" "${it.getString(0)}".${it.getString(1)}""")
        }

        db.withTransaction { session ->
            sequenceNames.forEach {
                session.sendPreparedStatement(
                    """
                        ALTER SEQUENCE $it RESTART WITH 1
                    """.trimIndent()
                )
            }
        }

        SimpleCache.allCachesOnlyForTestingPlease.forEach {
            val cache = it.get()
            cache?.clearAll()
        }

        db.withTransaction { session ->
            val parameters: EnhancedPreparedStatement.() -> Unit = {
                setParameter("refreshToken", refreshToken)
                setParameter("scopes", "[\"all:write\"]")
            }

            session
                .sendPreparedStatement(
                    {},
                    """
                        insert into auth.principals 
                            (dtype, id, created_at, modified_at, role, first_names, last_name, orc_id, 
                            phone_number, title, hashed_password, salt, org_id, email) 
                            values 
                            ('SERVICE', '_ucloud', now(), now(), 'SERVICE', 'Admin', 'Dev', null, null, null, 
                            E'\\xDEADBEEF', E'\\xDEADBEEF', null, 'admin@dev');
                   """
                )

            session
                .sendPreparedStatement(
                    parameters,
                    """
                        insert into auth.refresh_tokens 
                            (token, associated_user_id, csrf, public_session_reference, extended_by, scopes, 
                            expires_after, refresh_token_expiry, extended_by_chain, created_at, ip, user_agent) 
                            values
                            (:refreshToken, '_ucloud', 'csrf', 'initial', null, :scopes::jsonb, 
                            31536000000, null, '[]'::jsonb, now(), '127.0.0.1', 'UCloud');
                    """
                )

            session
                .sendPreparedStatement(
                    {},
                    """
                        insert into auth.principals 
                            (dtype, id, created_at, modified_at, role, first_names, last_name, orc_id, 
                            phone_number, title, hashed_password, salt, org_id, email) 
                            values 
                            ('PASSWORD', 'admin@dev', now(), now(), 'ADMIN', 'Admin', 'Dev', null, null, null, 
                            E'\\xDEADBEEF', E'\\xDEADBEEF', null, 'admin@dev');
                   """
                )

            session
                .sendPreparedStatement(
                    {
                        parameters()
                        setParameter("refreshToken", adminRefreshToken)
                    },
                    """
                        insert into auth.refresh_tokens 
                            (token, associated_user_id, csrf, public_session_reference, extended_by, scopes, 
                            expires_after, refresh_token_expiry, extended_by_chain, created_at, ip, user_agent) 
                            values
                            (:refreshToken, 'admin@dev', 'csrf', 'someothereuniquereference', null, :scopes::jsonb, 
                            31536000000, null, '[]'::jsonb, now(), '127.0.0.1', 'UCloud');
                    """
                )
        }

        for (provider in providers) {
            prepareProvider(provider)
        }
    }

    private val providers = ArrayList<ProviderTokens>()

    fun launch() {
        runBlocking {
            if (isRunning) {
                wipeDatabases()
                return@runBlocking
            }

            isRunning = true
            Runtime.getRuntime().addShutdownHook(Thread { shutdown() })

            if (providers.isEmpty()) {
                providers.add(
                    generateProviderTokens(
                        configPath = listOf("app.kubernetes", "files.ucloud"),
                        providerId = "ucloud",
                        domain = "localhost",
                        port = 8080,
                        https = false
                    )
                )

                providers.add(
                    generateProviderTokens(
                        configPath = listOf("dummy"),
                        providerId = "dummy",
                        domain = "localhost",
                        port = 8080,
                        https = false
                    )
                )
            }

            val serviceRefreshToken = UUID.randomUUID().toString()
            refreshToken = serviceRefreshToken
            val reg = ServiceRegistry(
                buildList<String> {
                    add("--dev")
                    add("--no-implicit-config")
                    add("--no-scripts")
                    add("--no-debugger")
                    add("--config-dir")
                    add(createConfiguration().absolutePath)
                    add("--config-dir")
                    add(File(System.getProperty("user.home"), "ucloud-integration").absolutePath)
                    val cfg = System.getenv("UCLOUD_INTEGRATION_CFG")
                    if (cfg != null) {
                        if (!File(cfg).exists()) {
                            log.warn("Could not find external integration config: ${File(cfg).absolutePath}")
                        } else {
                            add("--config-dir")
                            add(cfg)
                        }

                    }
                }.toTypedArray(),
                PlaceholderServiceDescription
            )

            micro = reg.rootMicro

            // NOTE(Dan): Hack to retrieve a DB session
            reg.register(object : Service {
                override val description: ServiceDescription = PlaceholderServiceDescription
                override fun initializeServer(micro: Micro): CommonServer {
                    micro.install(DatabaseConfigurationFeature)
                    db = AsyncDBSessionFactory(micro)
                    dbConfig = micro.databaseConfig
                    return object : CommonServer {
                        override val micro: Micro = micro
                        override val log: Logger = logger()
                        override fun start() {
                            // Do nothing
                        }
                    }
                }
            })
            db.withTransaction { session ->
                session.sendPreparedStatement(
                    {},
                    """
                        create or replace function public.drop_schemas() returns void as $$
                        declare
                            statements cursor for
                                select schemaname from pg_tables
                                where
                                    schemaname != 'public' and
                                    schemaname != 'pg_catalog' and
                                    schemaname != 'information_schema';
                        begin
                            for stmt in statements loop
                                execute 'drop schema if exists ' || quote_ident(stmt.schemaname) || ' cascade;';
                            end loop;
                        end;
                        $$ language plpgsql
                    """
                )

                session.sendQuery("select public.drop_schemas()")
            }
            dbConfig.migrateAll()
            runCatching {
                // TODO Deal with elasticsearch
                val me = micro.createScope()
                ContactBookElasticDao(me.elasticClient).createIndex()
            }

            db.withTransaction { session ->
                // Create a truncate script
                session.sendPreparedStatement(
                    {},
                    """
                        create or replace function public.truncate_tables() returns void as $$
                        declare
                            statements cursor for
                                select schemaname, tablename from pg_tables
                                where
                                    schemaname != 'public' and
                                    schemaname != 'pg_catalog' and
                                    schemaname != 'information_schema';
                        begin
                            for stmt in statements loop
                                execute 'truncate table ' || quote_ident(stmt.schemaname) || '.' || 
                                    quote_ident(stmt.tablename) || ' cascade;';
                            end loop;
                        end;
                        $$ language plpgsql
                    """
                )
            }

            wipeDatabases()

            val m = micro.createScope()
            m.install(AuthenticatorFeature)
            serviceClient = m.authenticator.authenticateClient(OutgoingHttpCall)
            adminClient = RefreshingJWTAuthenticator(m.client, JwtRefresher.Normal(adminRefreshToken, OutgoingHttpCall))
                .authenticateClient(OutgoingHttpCall)

            val blacklist = setOf(
                // The following 'services' are all essentially scripts that run in UCloud
                // None of them are meant to be run as a normal service.
                //AuditIngestionService,
                RedisCleanerService,
                ElasticManagementService,
                AlertingService,
            )

            val servicesToRun = services + setOf(DummyProviderService)

            // Reflection is _way_ too slow
            servicesToRun.forEach { objectInstance ->
                if (objectInstance !in blacklist) {
                    try {
                        log.trace("Registering ${objectInstance.javaClass.canonicalName}")
                        reg.register(objectInstance)
                    } catch (ex: Throwable) {
                        log.error("Caught error: ${ex.stackTraceToString()}")
                    }
                }
            }

            GlobalScope.launch {
                reg.start()
            }

            while (!reg.isRunning) {
                delay(50)
            }
        }
    }
}

abstract class IntegrationTest : UCloudTest() {
    init {
        UCloudLauncher.launch()
        perCasePreparation = {
            Time.provider = SystemTimeProvider
            UCloudLauncher.wipeDatabases()
        }
    }
}
