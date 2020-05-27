package dk.sdu.cloud.redis.cleaner

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.redis.cleaner.api.RedisCleanerServiceDescription
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

object RedisCleanerService : Service {
    override val description: ServiceDescription = RedisCleanerServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    RedisCleanerService.runAsStandalone(args)
}
