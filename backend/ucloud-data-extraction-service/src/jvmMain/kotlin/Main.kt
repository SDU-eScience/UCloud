package dk.sdu.cloud.ucloud.data.extraction

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.ucloud.data.extraction.api.UcloudDataExtractionServiceDescription

object UcloudDataExtractionService : Service {
    override val description = UcloudDataExtractionServiceDescription
    
    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)
        micro.install(ElasticFeature)
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    UcloudDataExtractionService.runAsStandalone(args)
}
