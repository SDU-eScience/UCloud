package dk.sdu.cloud.extract.data

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.extract.data.api.ExtractDataServiceDescription
import dk.sdu.cloud.service.CommonServer

object ExtractDataService : Service {
    override val description = ExtractDataServiceDescription
    
    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)
        micro.install(ElasticFeature)
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    ExtractDataService.runAsStandalone(args)
}
