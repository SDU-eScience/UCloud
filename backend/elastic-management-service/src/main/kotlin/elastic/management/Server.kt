package dk.sdu.cloud.elastic.management

import dk.sdu.cloud.elastic.management.services.AutoSettingsService
import dk.sdu.cloud.elastic.management.services.BackupService
import dk.sdu.cloud.elastic.management.services.CustomScripts
import dk.sdu.cloud.elastic.management.services.ExpiredEntriesDeleteService
import dk.sdu.cloud.elastic.management.services.ReindexService
import dk.sdu.cloud.elastic.management.services.ShrinkService
import dk.sdu.cloud.elastic.management.services.deleteIndex
import dk.sdu.cloud.elastic.management.services.getAllEmptyIndices
import dk.sdu.cloud.elastic.management.services.getShardCount
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.commandLineArguments
import dk.sdu.cloud.micro.elasticHighLevelClient
import dk.sdu.cloud.micro.elasticLowLevelClient
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.startServices
import elastic.management.services.ElasticEntryCleanupService
import kotlin.system.exitProcess


class Server(
    private val config: Configuration,
    override val micro: Micro
) : CommonServer {

    override val log = logger()

    override fun start() {
        val elasticHighLevelClient = micro.elasticHighLevelClient
        val elasticLowLevelClient = micro.elasticLowLevelClient

        startServices(wait = false)

        if (micro.commandLineArguments.contains("--entryDelete")) {
            @Suppress("TooGenericExceptionCaught")
            try {
                val eecService = ElasticEntryCleanupService(elasticHighLevelClient)
                eecService.removeEntriesContaining(listOf("STATEMENT"), "stolon")
                exitProcess(0)
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                exitProcess(1)
            }
        }

        if (micro.commandLineArguments.contains("--setup")) {
            @Suppress("TooGenericExceptionCaught")
            try {
                val settingService = AutoSettingsService(elasticHighLevelClient)
                settingService.setup()
                exitProcess(0)
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                exitProcess(1)
            }
        }

        if (micro.commandLineArguments.contains("--removeFlood")) {
            @Suppress("TooGenericExceptionCaught")
            try {
                log.info("removing flood limitation")
                val settingService = AutoSettingsService(elasticHighLevelClient)
                settingService.removeFloodLimitationOnAll()
                exitProcess(0)
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                exitProcess(1)
            }
        }

        if (micro.commandLineArguments.contains("--cleanup")) {
            @Suppress("TooGenericExceptionCaught")
            try {
                val deleteService = ExpiredEntriesDeleteService(elasticHighLevelClient)
                deleteService.deleteExpiredAllIndices()
                val shrinkService = ShrinkService(elasticHighLevelClient, config.gatherNode)
                shrinkService.shrink()
                deleteService.deleteOldRancherLogs()
                deleteService.deleteOldFileBeatLogs()
                deleteService.deleteOldInfrastructureLogs()
                exitProcess(0)
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                exitProcess(1)
            }
        }

        if (micro.commandLineArguments.contains("--reindex")) {
            @Suppress("TooGenericExceptionCaught")
            try {
                val reindexService = ReindexService(elasticHighLevelClient)
                reindexService.reindexLogsWithPrefixAWeekBackFrom(7, "http_logs", elasticLowLevelClient)
                exitProcess(0)
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                exitProcess(1)
            }
        }

        if (micro.commandLineArguments.contains("--monthlyReduce")) {
            @Suppress("TooGenericExceptionCaught")
            try {
                val reindexService = ReindexService(elasticHighLevelClient)
                reindexService.reduceLastMonth("http_logs", lowLevelClient = elasticLowLevelClient)
                exitProcess(0)
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                exitProcess(1)
            }
        }

        if (micro.commandLineArguments.contains("--reduceLastQuarter")) {
            @Suppress("TooGenericExceptionCaught")
            try {
                val reindexService = ReindexService(elasticHighLevelClient)
                reindexService.reduceLastQuarter("http_logs", lowLevelClient = elasticLowLevelClient)
                exitProcess(0)
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                exitProcess(1)
            }
        }

        if (micro.commandLineArguments.contains("--deleteEmptyIndices")) {
            @Suppress("TooGenericExceptionCaught")
            try {
                getAllEmptyIndices(elasticHighLevelClient, elasticLowLevelClient).forEach {
                    deleteIndex(it, elasticHighLevelClient)
                }
                exitProcess(0)
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                exitProcess(1)
            }
        }

        if (micro.commandLineArguments.contains("--backup")) {
            @Suppress("TooGenericExceptionCaught")
            try {
                val backupService = BackupService(elasticHighLevelClient, config.mount)
                backupService.start()
                exitProcess(0)
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                exitProcess(1)
            }
        }
    }
}
