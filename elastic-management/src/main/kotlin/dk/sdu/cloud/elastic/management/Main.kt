package dk.sdu.cloud.elastic.management

import dk.sdu.cloud.elastic.management.api.ElasticManagementServiceDescription
import dk.sdu.cloud.micro.ElasticFeature
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.micro.initWithDefaultFeatures
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.runScriptHandler
import java.net.InetAddress
import java.net.UnknownHostException

data class Configuration(
    val mount: String,
    val gatherNode: String
)

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(ElasticManagementServiceDescription, args)
        install(ElasticFeature)
    }

    if (micro.runScriptHandler()) return

    val config = micro.configuration.requestChunkAt<Configuration>("elasticmanagement")

    Server(config, micro).start()
}
