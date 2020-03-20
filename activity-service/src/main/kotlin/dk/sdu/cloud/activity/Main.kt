package dk.sdu.cloud.activity

import dk.sdu.cloud.activity.api.ActivityServiceDescription
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.micro.ElasticFeature
import dk.sdu.cloud.micro.HealthCheckFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.initWithDefaultFeatures
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.runScriptHandler

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(ActivityServiceDescription, args)
        install(RefreshingJWTCloudFeature)
        install(ElasticFeature)
        install(HealthCheckFeature)
    }

    if (micro.runScriptHandler()) return

    Server(micro).start()
}
