package dk.sdu.cloud.elastic.management

import dk.sdu.cloud.elastic.management.api.ElasticManagementServiceDescription
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer
import java.net.InetAddress
import java.net.UnknownHostException

data class Configuration(
    val mount: String,
    val gatherNode: String
)

object ElasticManagementService : Service {
    override val description = ElasticManagementServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(ElasticFeature)

        val config = micro.configuration.requestChunkAt<Configuration>("elasticmanagement")
        return Server(config, micro)
    }
}

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(ElasticManagementServiceDescription, args)
        install(HealthCheckFeature)
    }

    if (micro.runScriptHandler()) return

}
