package dk.sdu.cloud.audit.ingestion

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.audit.ingestion.api.AuditIngestionServiceDescription
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

object AuditIngestionService : Service {
    override val description: ServiceDescription = AuditIngestionServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)
        micro.install(ElasticFeature)
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    AuditIngestionService.runAsStandalone(args)
}
