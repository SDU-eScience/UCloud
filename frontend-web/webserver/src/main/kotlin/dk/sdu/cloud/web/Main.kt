package dk.sdu.cloud.web

import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.SDUCloud
import dk.sdu.cloud.service.CloudFeature
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.initWithDefaultFeatures
import dk.sdu.cloud.service.kafka
import dk.sdu.cloud.service.runScriptHandler
import dk.sdu.cloud.service.serverProvider
import dk.sdu.cloud.web.api.WebServiceDescription
import io.ktor.client.request.HttpRequestBuilder

class UnauthenticatedCloud(override val parent: CloudContext) : AuthenticatedCloud {
    override fun HttpRequestBuilder.configureCall() {
        // Do nothing
    }
}

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(WebServiceDescription, args)
        feature(CloudFeature).addAuthenticatedCloud(1, UnauthenticatedCloud(SDUCloud("https://cloud.sdu.dk")))
    }

    if (micro.runScriptHandler()) return

    Server(
        micro.kafka,
        micro.serverProvider,
        micro
    ).start()
}
