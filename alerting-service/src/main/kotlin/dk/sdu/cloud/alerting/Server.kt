package dk.sdu.cloud.alerting

import dk.sdu.cloud.alerting.services.ElasticAlerting
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.startServices
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
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
        GlobalScope.launch {
            try {
                ElasticAlerting(elastic).start(5000)
            } catch (ex: Exception) {
                println("finaly1")
            }
        }
    }
}
