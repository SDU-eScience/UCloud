package dk.sdu.cloud.web

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.web.api.WebServiceDescription

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(WebServiceDescription, args)
        install(HealthCheckFeature)
    }

    if (micro.runScriptHandler()) return

    Server(
        micro
    ).start()
}
