package dk.sdu.cloud.web

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.auth.api.refreshingJwtCloud
import dk.sdu.cloud.service.*
import dk.sdu.cloud.web.api.WebServiceDescription

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(WebServiceDescription, args)
        install(RefreshingJWTCloudFeature)
    }

    if (micro.runScriptHandler()) return

    Server(
        micro.kafka,
        micro.serverProvider,
        micro.refreshingJwtCloud,
        micro.serviceInstance
    ).start()
}