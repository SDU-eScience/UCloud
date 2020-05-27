package dk.sdu.cloud.activity

import dk.sdu.cloud.activity.api.ActivityServiceDescription
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

object ActivityService : Service {
    override val description = ActivityServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(ElasticFeature)
        micro.install(RefreshingJWTCloudFeature)

        return Server(micro)
    }
}

fun main(args: Array<String>) {
    ActivityService.runAsStandalone(args)
}
