package dk.sdu.cloud.indexing

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.indexing.http.LookupController
import dk.sdu.cloud.indexing.http.QueryController
import dk.sdu.cloud.indexing.processor.StorageEventProcessor
import dk.sdu.cloud.indexing.services.ElasticIndexingService
import dk.sdu.cloud.indexing.services.ElasticQueryService
import dk.sdu.cloud.indexing.services.FileIndexScanner
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.elasticHighLevelClient
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import kotlin.system.exitProcess

/**
 * The primary server class for indexing-service
 */
class Server(
    override val micro: Micro
) : CommonServer {
    private lateinit var elastic: RestHighLevelClient

    override val log = logger()

    override fun start() {
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val eventService = micro.eventStreamService

        elastic = micro.elasticHighLevelClient

        val indexingService = ElasticIndexingService(elastic)
        val queryService = ElasticQueryService(elastic)

        if (micro.commandLineArguments.contains("--scan")) {
            @Suppress("TooGenericExceptionCaught")
            try {
                val scanner = FileIndexScanner(client, elastic)
                scanner.scan()

                exitProcess(0)
            } catch (ex: Exception) {
                ex.printStackTrace()
                exitProcess(1)
            }
        }

        StorageEventProcessor(eventService, indexingService).init()

        with(micro.server) {
            configureControllers(
                LookupController(queryService),
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
