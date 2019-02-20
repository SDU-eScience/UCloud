package dk.sdu.cloud.elastic.management

import dk.sdu.cloud.elastic.management.api.ElasticManagementServiceDescription
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.initWithDefaultFeatures
import dk.sdu.cloud.micro.runScriptHandler

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(ElasticManagementServiceDescription, args)
    }

    if (micro.runScriptHandler()) return

    Server(

        micro
    ).start()
}
