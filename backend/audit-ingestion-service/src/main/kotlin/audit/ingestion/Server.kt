package dk.sdu.cloud.audit.ingestion

import dk.sdu.cloud.audit.ingestion.processors.AuditProcessor
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.startServices

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        if (micro.featureOrNull(ElasticFeature) == null) return
        val client = micro.elasticHighLevelClient

        AuditProcessor(micro.eventStreamService, client, micro.developmentModeEnabled).init()

        startServices()
    }
}
