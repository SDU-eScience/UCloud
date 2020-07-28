package dk.sdu.cloud.integration

import dk.sdu.cloud.Role
import dk.sdu.cloud.accounting.AccountingService
import dk.sdu.cloud.activity.ActivityService
import dk.sdu.cloud.app.kubernetes.AppKubernetesService
import dk.sdu.cloud.app.kubernetes.watcher.AppKubernetesWatcherService
import dk.sdu.cloud.app.license.AppLicenseService
import dk.sdu.cloud.app.orchestrator.AppOrchestratorService
import dk.sdu.cloud.app.store.AppStoreService
import dk.sdu.cloud.audit.ingestion.AuditIngestionService
import dk.sdu.cloud.auth.AuthService
import dk.sdu.cloud.auth.api.CreateSingleUserRequest
import dk.sdu.cloud.auth.api.CreateUserRequest
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.avatar.AvatarService
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.contact.book.ContactBookService
import dk.sdu.cloud.downtime.management.DowntimeManagementService
import dk.sdu.cloud.elastic.management.ElasticManagementService
import dk.sdu.cloud.file.StorageService
import dk.sdu.cloud.file.favorite.FileFavoriteService
import dk.sdu.cloud.file.stats.FileStatsService
import dk.sdu.cloud.file.trash.FileTrashService
import dk.sdu.cloud.filesearch.FileSearchService
import dk.sdu.cloud.grant.GrantService
import dk.sdu.cloud.indexing.IndexingService
import dk.sdu.cloud.kubernetes.monitor.KubernetesMonitorService
import dk.sdu.cloud.mail.MailService
import dk.sdu.cloud.micro.Log4j2ConfigFactory
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.Service
import dk.sdu.cloud.micro.ServiceRegistry
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.news.NewsService
import dk.sdu.cloud.notification.NotificationService
import dk.sdu.cloud.password.reset.PasswordResetService
import dk.sdu.cloud.project.ProjectService
import dk.sdu.cloud.project.repository.ProjectRepositoryService
import dk.sdu.cloud.redis.cleaner.RedisCleanerService
import dk.sdu.cloud.service.ClassDiscovery
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.EnhancedPreparedStatement
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.share.ShareService
import dk.sdu.cloud.support.SupportService
import dk.sdu.cloud.task.TaskService
import dk.sdu.cloud.webdav.WebdavService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.Location
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

object UCloudLauncher : Loggable {
    init {
        ConfigurationFactory.setConfigurationFactory(Log4j2ConfigFactory)
    }

    override val log = logger()
    var isRunning = false
        private set
    lateinit var micro: Micro

    private fun createConfiguration(refreshToken: String): File {
        val dir = Files.createTempDirectory("c").toFile()

        TestDB.initializeWithoutService()

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

        return dir
    }

    private fun migrateAll() {
        val username = "postgres"
        val password = "postgres"
        val jdbcUrl = TestDB.getEmbeddedPostgresInfo()

        javaClass.classLoader.resources("db/migration").forEach outer@{ migrationUrl ->
           val tempDirectory = Files.createTempDirectory("migration").toFile()

            Files.newDirectoryStream(pathInResources(migrationUrl, "db/migration")).use { dirStream ->
                dirStream.forEach {
                    println(it)
                    if (it.fileName.toString().endsWith(".class")) return@outer
                    Files.newInputStream(it).use { ins ->
                        FileOutputStream(File(tempDirectory, it.fileName.toString())).use { fos ->
                            ins.copyTo(fos)
                        }
                    }
                }
            }

            val schema = try {
                URL("$migrationUrl/schema.txt").readText()
            } catch (ex: Throwable) {
                throw RuntimeException("Could not find 'schema.txt' in $migrationUrl ${URL("$migrationUrl/schema.txt")}", ex)
            }

            val flyway = Flyway.configure().apply {
                dataSource(jdbcUrl, username, password)
                schemas(schema)
                locations(Location(Location.FILESYSTEM_PREFIX + tempDirectory.absolutePath))
            }.load()

            flyway.migrate()
        }
    }

    private fun pathInResources(url: URL, internalPath: String): Path {
        val uri = url.toURI()
        return if (uri.scheme == "jar") {
            val fs = FileSystems.newFileSystem(uri, emptyMap<String, Any?>())
            fs.getPath(internalPath)
        } else {
            Paths.get(uri)
        }
    }

    fun launch() {
        if (isRunning) return
        isRunning = true

        runBlocking {
            val serviceRefreshToken = UUID.randomUUID().toString()
            val reg = ServiceRegistry(
                arrayOf(
                    "--dev",
                    "--no-implicit-config",
                    "--config-dir",
                    createConfiguration(serviceRefreshToken).absolutePath
                )
            )

            micro = reg.rootMicro

            migrateAll()

            TestDB.dbSessionFactory("auth").withSession { session ->
                val parameters: EnhancedPreparedStatement.() -> Unit = {
                    setParameter("refreshToken", serviceRefreshToken)
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
                            31536000000, null, '[]'::jsonb,now(), '127.0.0.1', 'UCloud');
                    """
                    )
            }


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
        }
    }
}

class F {
    companion object {
        @BeforeClass @JvmStatic fun beforeClass() {
            UCloudLauncher.launch()
        }
    }

    @Test
    fun testing(): Unit = runBlocking {
        println("Hello, World!")
        val m = UCloudLauncher.micro.createScope()
        m.install(RefreshingJWTCloudFeature)
        val serviceClient = m.authenticator.authenticateClient(OutgoingHttpCall)
        println(UserDescriptions.createNewUser.call(
            listOf(CreateSingleUserRequest("user", "testing", "user@dev", Role.USER)),
            serviceClient
        ).orThrow())

        return@runBlocking
    }
}
