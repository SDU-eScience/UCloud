package dk.sdu.cloud.elastic.management

import dk.sdu.cloud.elastic.management.services.DeleteService
import dk.sdu.cloud.elastic.management.services.ShrinkService
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.startServices
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import kotlin.system.exitProcess
import org.elasticsearch.client.RestHighLevelClient


class Server(
    private val elasticHostAndPort: ElasticHostAndPort,
    override val micro: Micro
) : CommonServer {
    private lateinit var elastic: RestHighLevelClient

    override val log = logger()

    override fun start() {

        elastic = RestHighLevelClient(
            RestClient.builder(
                HttpHost(
                    elasticHostAndPort.host,
                    elasticHostAndPort.port,
                    "http"
                )
            )
        )

        if (micro.commandLineArguments.contains("--cleanup")) {
            @Suppress("TooGenericExceptionCaught")
            try {
                val deleteService = DeleteService(elastic)
                deleteService.cleanUp()
                val shrinkService = ShrinkService(elastic)
                shrinkService.shrink()
                exitProcess(0)
            } catch (ex: Exception) {
                ex.printStackTrace()
                exitProcess(1)
            }
        }

        startServices()
    }
}
