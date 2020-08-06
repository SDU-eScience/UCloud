package dk.sdu.cloud.integration

import com.sun.jna.Platform
import dk.sdu.cloud.accounting.AccountingService
import dk.sdu.cloud.activity.ActivityService
import dk.sdu.cloud.app.kubernetes.AppKubernetesService
import dk.sdu.cloud.app.kubernetes.api.AppKubernetesDescriptions
import dk.sdu.cloud.app.kubernetes.api.ReloadRequest
import dk.sdu.cloud.app.kubernetes.watcher.AppKubernetesWatcherService
import dk.sdu.cloud.app.kubernetes.watcher.api.AppKubernetesWatcher
import dk.sdu.cloud.app.license.AppLicenseService
import dk.sdu.cloud.app.orchestrator.AppOrchestratorService
import dk.sdu.cloud.app.store.AppStoreService
import dk.sdu.cloud.audit.ingestion.AuditIngestionService
import dk.sdu.cloud.auth.AuthService
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.avatar.AvatarService
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.contact.book.ContactBookService
import dk.sdu.cloud.contact.book.services.ContactBookElasticDao
import dk.sdu.cloud.downtime.management.DowntimeManagementService
import dk.sdu.cloud.elastic.management.ElasticManagementService
import dk.sdu.cloud.file.StorageService
import dk.sdu.cloud.file.favorite.FileFavoriteService
import dk.sdu.cloud.file.stats.FileStatsService
import dk.sdu.cloud.file.trash.FileTrashService
import dk.sdu.cloud.filesearch.FileSearchService
import dk.sdu.cloud.grant.GrantService
import dk.sdu.cloud.indexing.IndexingService
import dk.sdu.cloud.integration.backend.sampleStorage
import dk.sdu.cloud.kubernetes.monitor.KubernetesMonitorService
import dk.sdu.cloud.mail.MailService
import dk.sdu.cloud.micro.DatabaseConfig
import dk.sdu.cloud.micro.ElasticFeature
import dk.sdu.cloud.micro.Log4j2ConfigFactory
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.ServiceRegistry
import dk.sdu.cloud.micro.elasticHighLevelClient
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.migrateAll
import dk.sdu.cloud.news.NewsService
import dk.sdu.cloud.notification.NotificationService
import dk.sdu.cloud.password.reset.PasswordResetService
import dk.sdu.cloud.project.ProjectService
import dk.sdu.cloud.project.repository.ProjectRepositoryService
import dk.sdu.cloud.redis.cleaner.RedisCleanerService
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.EnhancedPreparedStatement
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.share.ShareService
import dk.sdu.cloud.support.SupportService
import dk.sdu.cloud.task.TaskService
import dk.sdu.cloud.webdav.WebdavService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.utility.Base58
import redis.embedded.RedisExecProvider
import redis.embedded.RedisServer
import redis.embedded.util.OS
import java.io.File
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.file.Files
import java.util.*
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPasswordField

fun findPreferredOutgoingIp(): String {
    return DatagramSocket().use { socket ->
        socket.connect(InetAddress.getByName("1.1.1.1"), 12345)
        socket.localAddress.hostAddress
    }
}

class CephContainer : GenericContainer<CephContainer?>("ceph/daemon") {
    val hostIp: String

    init {
        logger().info("Starting an ceph container using [{}]", dockerImageName)
        withNetworkAliases("ceph-" + Base58.randomString(6))

        hostIp = findPreferredOutgoingIp()

        logger().info("Using the following IP: $hostIp")
        withEnv("CEPH_PUBLIC_NETWORK", "$hostIp/32")
        withEnv("MON_IP", hostIp)
        withEnv("CEPH_DEMO_UID", "1000")
        withEnv("RGW_FRONTEND_PORT", "8000")
        withNetworkMode("host")
        withFileSystemBind("/etc/ceph", "/etc/ceph")

        withCommand("demo")

        // In case logging is needed:
        // withLogConsumer { print(it.utf8String) }
    }
}

class K3sContainer : GenericContainer<K3sContainer?>("rancher/k3s:v1.16.13-rc3-k3s1-amd64") {
    init {
        withPrivilegedMode(true)
        addExposedPort(6443)
        withTmpFs(
            mapOf(
                "/run" to "",
                "/var/run" to ""
            )
        )
        withCommand("server")
        withFileSystemBind(UCloudLauncher.cephfsHome, UCloudLauncher.cephfsHome)
    }
}

object UCloudLauncher : Loggable {
    init {
        ConfigurationFactory.setConfigurationFactory(Log4j2ConfigFactory)
    }

    override val log = logger()

    var isRunning = false
        private set

    var shouldRunCeph: Boolean = false

    lateinit var serviceClient: AuthenticatedClient

    var isK8sRunning: Boolean = false
        private set
    private lateinit var k3sContainer: K3sContainer

    private val tempDir = Files.createTempDirectory("integration").toFile().also { it.deleteOnExit() }
    val cephfsHome = File(tempDir, "cephfs").absolutePath
    private const val REDIS_PORT = 44231
    lateinit var micro: Micro
    private lateinit var redisServer: RedisServer
    private lateinit var elasticSearch: ElasticsearchContainer
    private lateinit var ceph: CephContainer
    private var isRunningCeph = false
    private var localSudoPassword: String? = null
    private lateinit var refreshToken: String
    private val uid: Int by lazy {
        if (!Platform.isLinux()) throw IllegalStateException()

        val process = ProcessBuilder()
            .command("id", "-u")
            .start()

        String(process.inputStream.readAllBytes()).trim().toInt(10)
    }

    private fun sudo(vararg command: String): Process? {
        if (uid == 0) {
            return ProcessBuilder().command(*command).start()
        } else {
            val localSudoPassword = this.localSudoPassword ?: return null

            val process = ProcessBuilder()
                .command(
                    "sudo",
                    "-S",
                    *command
                )
                .start()

            process.outputStream.write(localSudoPassword.toByteArray())
            process.outputStream.write(byteArrayOf('\n'.toByte()))
            process.outputStream.flush()
            return process
        }
    }

    private fun initializeDatabases() {
        @Suppress("BlockingMethodInNonBlockingContext") val job = GlobalScope.launch {
            coroutineScope {
                launch {
                    // Postgres
                    TestDB.initializeWithoutService()
                }

                launch {
                    // Redis

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
                            javaClass.classLoader.getResourceAsStream("redis-linux")!!.use { ins -> ins.copyTo(outs) }
                        }
                    }
                    redisLinux.setExecutable(true)

                    redisServer = RedisServer(
                        RedisExecProvider.defaultProvider()
                            .override(OS.MAC_OS_X, redisMacOS.absolutePath)
                            .override(OS.UNIX, redisLinux.absolutePath),
                        REDIS_PORT
                    )

                    redisServer.start()
                }

                launch {
                    // ElasticSearch
                    elasticSearch = ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:7.6.0")
                    elasticSearch.start()
                }

                launch {
                    // Ceph or normal file system
                    File(cephfsHome).mkdirs()

                    if (Platform.isLinux() && shouldRunCeph) {
                        isRunningCeph = true

                        val console = System.console()
                        if (uid != 0) {
                            localSudoPassword = if (console != null) {
                                String(console.readPassword("Local sudo password"))
                            } else {
                                val panel = JPanel()
                                val label = JLabel("Local sudo password:")
                                val pass = JPasswordField(10)
                                panel.add(label)
                                panel.add(pass)
                                val options = arrayOf("OK", "Cancel")
                                val option = JOptionPane.showOptionDialog(
                                    null, panel, "Local sudo password",
                                    JOptionPane.NO_OPTION, JOptionPane.PLAIN_MESSAGE,
                                    null, options, options[0]
                                )

                                String(pass.password)
                            }
                        }

                        val c = CephContainer()
                        c.start()
                        ceph = c
                        while (true) {
                            if (c.execInContainer("curl", "http://localhost:5000").exitCode == 0) break
                            Thread.sleep(100)
                        }

                        if (sudo("ceph-fuse", cephfsHome)!!.waitFor() != 0) throw IllegalStateException()
                        if (sudo(
                                "chown",
                                "$uid:$uid",
                                cephfsHome,
                                "-R"
                            )!!.waitFor() != 0
                        ) throw IllegalStateException()
                    }
                }
            }
        }

        runBlocking { job.join() }
    }

    private fun createConfiguration(): File {
        val dir = Files.createTempDirectory("c").toFile()

        initializeDatabases()

        File(dir, "token.yml").writeText(
            """
                ---
                refreshToken: $refreshToken
                tokenValidation:
                  jwt:
                    sharedSecret: notverysecret
            """.trimIndent()
        )

        File(dir, "db.yml").writeText(
            """
                ---
                database:
                  profile: PERSISTENT_POSTGRES
                  hostname: localhost
                  port: ${TestDB.db.port}
                  credentials:
                    username: postgres
                    password: postgres
            """.trimIndent()
        )

        File(dir, "redis.yml").writeText(
            """
                ---
                redis:
                  hostname: localhost
                  port: $REDIS_PORT
            """.trimIndent()
        )

        File(dir, "elasticsearch.yml").writeText(
            """
                ---
                elk:
                  elasticsearch:
                    hostname: localhost
                    port: ${elasticSearch.getMappedPort(9200)}
            """.trimIndent()
        )

        File(dir, "ceph.yml").writeText(
            """
                ---
                ceph:
                  cephfsBaseMount: $cephfsHome
            """.trimIndent()
        )

        File(dir, "storage.yml").writeText(
            """
                ---
                storage:
                  product:
                    id: ${sampleStorage.id}
                    category: ${sampleStorage.category.id}
                    provider: ${sampleStorage.category.provider}
                    pricePerGb: ${sampleStorage.pricePerUnit}
                    defaultQuota: -1
            """.trimIndent()
        )

        File(dir, "k8s.yml").writeText(
            """
                ---
                app:
                  kubernetes:
                    reloadableK8Config: ${File(tempDir, "k3s.yml").absolutePath}
            """.trimIndent()
        )

        return dir
    }

    private fun migrateAll() {
        val username = "postgres"
        val password = "postgres"
        val jdbcUrl = TestDB.getEmbeddedPostgresInfo()
        DatabaseConfig(jdbcUrl, username, password, "public", false, false).migrateAll()
    }

    private fun shutdown() {
        elasticSearch.close()
        redisServer.stop()
        TestDB.db.close()
        if (isRunningCeph) {
            sudo("umount", "-f", cephfsHome)
            ceph.close()
        }
    }

    suspend fun wipeDatabases() {
        File(cephfsHome, "home").apply {
            require(deleteRecursively())
            require(mkdirs())
        }
        File(cephfsHome, "projects").apply {
            require(deleteRecursively())
            require(mkdirs())
        }

        TestDB.dbSessionFactory("public").withSession { session ->
            session.sendQuery("select truncate_tables()")
        }

        TestDB.dbSessionFactory("auth").withSession { session ->
            val parameters: EnhancedPreparedStatement.() -> Unit = {
                setParameter("refreshToken", refreshToken)
                setParameter("scopes", "[\"all:write\"]")
            }

            session
                .sendPreparedStatement(
                    parameters,
                    """
                        insert into principals 
                            (dtype, id, created_at, modified_at, role, first_names, last_name, orc_id, 
                            phone_number, title, hashed_password, salt, org_id, email) 
                            values 
                            ('PASSWORD', 'admin@dev', now(), now(), 'ADMIN', 'Admin', 'Dev', null, null, null, 
                            E'\\xDEADBEEF', E'\\xDEADBEEF', null, 'admin@dev');
                   """
                )

            session
                .sendPreparedStatement(
                    parameters,
                    """
                        insert into refresh_tokens 
                            (token, associated_user_id, csrf, public_session_reference, extended_by, scopes, 
                            expires_after, refresh_token_expiry, extended_by_chain, created_at, ip, user_agent) 
                            values
                            (:refreshToken, 'admin@dev', 'csrf', 'initial', null, :scopes::jsonb, 
                            31536000000, null, '[]'::jsonb, now(), '127.0.0.1', 'UCloud');
                    """
                )
        }
    }

    fun launch() {
        runBlocking {
            if (isRunning) {
                wipeDatabases()
                return@runBlocking
            }

            isRunning = true
            Runtime.getRuntime().addShutdownHook(Thread { shutdown() })

            val serviceRefreshToken = UUID.randomUUID().toString()
            refreshToken = serviceRefreshToken
            val reg = ServiceRegistry(
                arrayOf(
                    "--dev",
                    "--no-implicit-config",
                    "--config-dir",
                    createConfiguration().absolutePath,
                    "--config-dir",
                    File(System.getProperty("user.home"), "sducloud-integration").absolutePath
                )
            )

            micro = reg.rootMicro

            migrateAll()
            run {
                // TODO Deal with elasticsearch
                val me = micro.createScope()
                me.install(ElasticFeature)
                ContactBookElasticDao(me.elasticHighLevelClient).createIndex()
            }

            TestDB.dbSessionFactory("public").withSession { session ->
                // Create a truncate script
                session.sendPreparedStatement(
                    {},
                    """
                        create or replace function truncate_tables() returns void as $$
                        declare
                            statements cursor for
                                select schemaname, tablename from pg_tables
                                where schemaname != 'public' and schemaname != 'pg_catalog';
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
            m.install(RefreshingJWTCloudFeature)
            serviceClient = m.authenticator.authenticateClient(OutgoingHttpCall)

            val blacklist = setOf(
                // WebDav needs to run as a standalone server
                WebdavService,

                // The following 'services' are all essentially scripts that run in UCloud
                // None of them are meant to be run as a normal service.
                //AuditIngestionService,
                RedisCleanerService,
                ElasticManagementService,
                KubernetesMonitorService
            )

            val services = setOf(
                AccountingService,
                ActivityService,
                AppKubernetesService,
                AppKubernetesWatcherService,
                AppLicenseService,
                AppOrchestratorService,
                AppStoreService,
                AuditIngestionService,
                AuthService,
                AvatarService,
                ContactBookService,
                DowntimeManagementService,
                ElasticManagementService,
                FileFavoriteService,
                FileStatsService,
                FileTrashService,
                FileSearchService,
                GrantService,
                IndexingService,
                MailService,
                NewsService,
                NotificationService,
                PasswordResetService,
                ProjectRepositoryService,
                ProjectService,
                ShareService,
                StorageService,
                SupportService,
                TaskService
            )

            // Reflection is _way_ too slow
            services.forEach { objectInstance ->
                if (objectInstance !in blacklist) {
                    try {
                        log.info("Registering ${objectInstance.javaClass.canonicalName}")
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

    fun requireK8s() {
        if (isK8sRunning) return
        k3sContainer = K3sContainer()
        k3sContainer.start()
        while (true) {
            if (k3sContainer.execInContainer("kubectl", "get", "node").exitCode == 0) break
            Thread.sleep(100)
        }
        while (true) {
            if (k3sContainer.execInContainer("stat", "/etc/rancher/k3s/k3s.yaml").exitCode == 0) break
            Thread.sleep(100)
        }
        val target = File(tempDir, "k3s.yml") // .also { it.deleteOnExit() }
        k3sContainer.copyFileFromContainer("/etc/rancher/k3s/k3s.yaml", target.absolutePath)
        val correctConfig = target.readText().replace("127.0.0.1:6443", "127.0.0.1:${k3sContainer.getMappedPort(6443)}")
        target.writeText(correctConfig)
        isK8sRunning = true

        runBlocking {
            AppKubernetesDescriptions.reload.call(
                ReloadRequest(cephfsHome),
                serviceClient
            ).orThrow()

            AppKubernetesWatcher.reload.call(
                Unit,
                serviceClient
            ).orThrow()
        }
    }
}

abstract class IntegrationTest {
    init {
        UCloudLauncher.launch()
    }
}
