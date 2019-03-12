package dk.sdu.cloud.elastic.management

import dk.sdu.cloud.elastic.management.services.AutoSettingsService
import dk.sdu.cloud.elastic.management.services.BackupService
import dk.sdu.cloud.elastic.management.services.ExpiredEntriesDeleteService
import dk.sdu.cloud.elastic.management.services.ReindexService
import dk.sdu.cloud.elastic.management.services.ShrinkService
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.startServices
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import kotlin.system.exitProcess
import org.elasticsearch.client.RestHighLevelClient


class Server(
    private val elasticHostAndPort: ElasticHostAndPort,
    private val config: Configuration,
    override val micro: Micro
) : CommonServer {

    override val log = logger()

    override fun start() {
        val elastic = RestHighLevelClient(
            RestClient.builder(
                HttpHost(
                    elasticHostAndPort.host,
                    elasticHostAndPort.port,
                    "http"
                )
            )
        )

        if (micro.commandLineArguments.contains("--setup")) {
            @Suppress("TooGenericExceptionCaught")
            try {
                val settingService = AutoSettingsService(elastic)
                settingService.setup()
                exitProcess(0)
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                exitProcess(1)
            }
        }

        if (micro.commandLineArguments.contains("--cleanup")) {
            @Suppress("TooGenericExceptionCaught")
            try {
                val deleteService = ExpiredEntriesDeleteService(elastic)
                deleteService.cleanUp()
                val shrinkService = ShrinkService(elastic, config.gatherNode)
                shrinkService.shrink()
                exitProcess(0)
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                exitProcess(1)
            }
        }

        if (micro.commandLineArguments.contains("--reindex")) {
            @Suppress("TooGenericExceptionCaught")
            try {
                val reindexService = ReindexService(elastic)
                reindexService.reindexLogsWithPrefixAWeekBackFrom(7, "http_logs")
                exitProcess(0)
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                exitProcess(1)
            }
        }

        if (micro.commandLineArguments.contains("--backup")) {
            @Suppress("TooGenericExceptionCaught")
            try {
                val backupService = BackupService(elastic, config.mount)
                backupService.start()
                exitProcess(0)
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                exitProcess(1)
            }
        }

        startServices()
    }
}
