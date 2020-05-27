package dk.sdu.cloud.app.store

import dk.sdu.cloud.app.store.api.AppStoreServiceDescription
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

object AppStoreService : Service {
    override val description = AppStoreServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)
        micro.install(ElasticFeature)
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    AppStoreService.runAsStandalone(args)
}
