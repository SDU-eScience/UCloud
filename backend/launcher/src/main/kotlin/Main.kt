package dk.sdu.cloud

import dk.sdu.cloud.accounting.AccountingService
import dk.sdu.cloud.activity.ActivityService
import dk.sdu.cloud.app.kubernetes.AppKubernetesService
import dk.sdu.cloud.app.orchestrator.AppOrchestratorService
import dk.sdu.cloud.app.store.AppStoreService
import dk.sdu.cloud.audit.ingestion.AuditIngestionService
import dk.sdu.cloud.auth.AuthService
import dk.sdu.cloud.avatar.AvatarService
import dk.sdu.cloud.contact.book.ContactBookService
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
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.news.NewsService
import dk.sdu.cloud.notification.NotificationService
import dk.sdu.cloud.password.reset.PasswordResetService
import dk.sdu.cloud.project.ProjectService
import dk.sdu.cloud.project.repository.ProjectRepositoryService
import dk.sdu.cloud.provider.ProviderService
import dk.sdu.cloud.redis.cleaner.RedisCleanerService
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.share.ShareService
import dk.sdu.cloud.support.SupportService
import dk.sdu.cloud.task.TaskService
import dk.sdu.cloud.webdav.WebdavService
import org.apache.logging.log4j.Level
import kotlin.system.exitProcess

object Launcher : Loggable {
    override val log = logger()
}

val services = setOf(
    AccountingService,
    ActivityService,
    AppKubernetesService,
    AppOrchestratorService,
    AppStoreService,
    AuditIngestionService,
    AuthService,
    AvatarService,
    ContactBookService,
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
    TaskService,
    ProviderService
)

suspend fun main(args: Array<String>) {
    if (args.contains("--run-script") && args.contains("migrate-db")) {
        val micro = Micro().apply {
            initWithDefaultFeatures(object : ServiceDescription {
                override val name: String = "launcher"
                override val version: String = "1"
            }, args)
        }

        micro.databaseConfig.migrateAll()
        exitProcess(0)
    }

    val reg = ServiceRegistry(args, PlaceholderServiceDescription)
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

    val loader = Launcher::class.java.classLoader

    services.forEach { objectInstance ->
        try {
            Launcher.log.trace("Registering ${objectInstance.javaClass.canonicalName}")
            reg.register(objectInstance)
        } catch (ex: Throwable) {
            Launcher.log.error("Caught error: ${ex.stackTraceToString()}")
        }
    }

    reg.rootMicro.feature(LogFeature).configureLevels(
        mapOf(
            // Don't display same information twice
            "dk.sdu.cloud.calls.client.OutgoingHttpRequestInterceptor" to Level.INFO
        )
    )

    reg.start()
}
