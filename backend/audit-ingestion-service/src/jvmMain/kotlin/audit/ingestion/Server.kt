package dk.sdu.cloud.audit.ingestion

import dk.sdu.cloud.audit.ingestion.processors.AuditProcessor
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.elasticHighLevelClient
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.startServices
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import java.net.InetAddress
import java.net.UnknownHostException

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val client = micro.elasticHighLevelClient

        AuditProcessor(micro.eventStreamService, client).init()

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
