package dk.sdu.cloud.indexing

import dk.sdu.cloud.indexing.http.QueryController
import dk.sdu.cloud.indexing.services.ElasticQueryService
import dk.sdu.cloud.indexing.services.FileSystemScanner
import dk.sdu.cloud.indexing.services.FilesIndex
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.elasticHighLevelClient
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.startServices
import kotlinx.coroutines.runBlocking
import org.elasticsearch.client.RestHighLevelClient
import kotlin.system.exitProcess

/**
 * The primary server class for indexing-service
 */
class Server(
    override val micro: Micro,
    private val cephConfig: CephConfiguration
) : CommonServer {
    private lateinit var elastic: RestHighLevelClient

    override val log = logger()

    override fun start() {
        elastic = micro.elasticHighLevelClient
        val queryService = ElasticQueryService(elastic)

        if (micro.commandLineArguments.contains("--scan")) {
            runBlocking {
                try {
                    FileSystemScanner(
                        elastic,
                        queryService,
                        run {
                            if (micro.developmentModeEnabled) TODO()
                            else "/mnt/cephfs/" + cephConfig.subfolder
                        },
                        cephConfig.useCephDirectoryStats
                    ).runScan()
                } catch (ex: Throwable) {
                    log.error(ex.stackTraceToString())
                    exitProcess(1)
                }
            }
            exitProcess(0)
        }

        val indexArgIdx = micro.commandLineArguments.indexOf("--create-index")
        if (indexArgIdx != -1) {
            val numberOfShards = micro.commandLineArguments.getOrNull(indexArgIdx + 1)?.toInt() ?: 5
            val numberOfReplicas = micro.commandLineArguments.getOrNull(indexArgIdx + 1)?.toInt() ?: 2
            try {
                FilesIndex.create(elastic, numberOfShards, numberOfReplicas)
                exitProcess(0)
            } catch (ex: Throwable) {
                ex.printStackTrace()
                exitProcess(1)
            }
        }

        with(micro.server) {
            configureControllers(
                QueryController(queryService)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
        elastic.close()
    }
}
