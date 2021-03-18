package dk.sdu.cloud.elastic.management

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.elastic.management.services.*
import dk.sdu.cloud.elastic.management.services.deleteIndex
import dk.sdu.cloud.elastic.management.services.getListOfIndices
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.commandLineArguments
import dk.sdu.cloud.micro.elasticHighLevelClient
import dk.sdu.cloud.micro.elasticLowLevelClient
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.startServices
import elastic.management.services.ElasticEntryCleanupService
import elastic.management.services.Grafana
import kotlin.system.exitProcess


class Server(
    private val config: Configuration,
    override val micro: Micro
) : CommonServer {

    override val log = logger()

    override fun start() {
        val elasticHighLevelClient = micro.elasticHighLevelClient
        val elasticLowLevelClient = micro.elasticLowLevelClient
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        startServices(wait = false)

        if (micro.commandLineArguments.contains("--reindex")) {
            try {
                val reindexService = ReindexService(elasticHighLevelClient)
                reindexService.reindexToMonthly("http_logs", elasticLowLevelClient, serviceClient)
                exitProcess(0)
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                exitProcess(1)
            }
        }

        if (micro.commandLineArguments.contains("--grafanaAliases")) {
            @Suppress("TooGenericExceptionCaught")
            try {
                val grafana = Grafana(elasticHighLevelClient)
                grafana.createAlias("grafana", "http_logs")
                exitProcess(0)
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                exitProcess(1)
            }
        }

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

        if (micro.commandLineArguments.contains("--reindexSpecific")) {
            @Suppress("TooGenericExceptionCaught")
            try {
                val reindexService = ReindexService(elasticHighLevelClient)
                //SPECIFY HERE WHAT TO REINDEX NOT IN ARGS
                val fromIndices = getListOfIndices(elasticHighLevelClient, "*2021.12*")
                val toIndices = fromIndices.map { it.replace("-2021.", "-2020.") }
                println(fromIndices)
                println(toIndices)

                reindexService.reindexSpecificIndices(fromIndices, toIndices, elasticLowLevelClient)
                exitProcess(0)
            } catch (ex: Exception) {
                log.warn(ex.stackTraceToString())
                exitProcess(1)
            }
        }

        if (micro.commandLineArguments.contains("--deleteEmptyIndices")) {
            @Suppress("TooGenericExceptionCaught")
            try {
                getAllEmptyIndicesWithRegex(elasticHighLevelClient, elasticLowLevelClient, "*").forEach {
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
