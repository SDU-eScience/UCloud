package dk.sdu.cloud.elastic.management

import dk.sdu.cloud.elastic.management.api.ElasticManagementServiceDescription
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

data class Configuration(
    val mount: String,
    val gatherNode: String
)

object ElasticManagementService : Service {
    override val description = ElasticManagementServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(ElasticFeature)
        micro.install(HealthCheckFeature)

        val config = micro.configuration.requestChunkAtOrNull<Configuration>("elasticmanagement") ?:
            Configuration(
                mount =  "/tmp/mount",
                gatherNode = "elasticsearch-data-0"
            )
        return Server(config, micro)
    }
}

fun main(args: Array<String>) {
    ElasticManagementService.runAsStandalone(args)
}
