package dk.sdu.cloud.indexing

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.indexing.api.IndexingServiceDescription
import dk.sdu.cloud.micro.ElasticFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.micro.initWithDefaultFeatures
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.runScriptHandler
import java.net.InetAddress
import java.net.UnknownHostException

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(IndexingServiceDescription, args)
        install(RefreshingJWTCloudFeature)
        install(ElasticFeature)
    }

    if (micro.runScriptHandler()) return

    Server(micro).start()
}
