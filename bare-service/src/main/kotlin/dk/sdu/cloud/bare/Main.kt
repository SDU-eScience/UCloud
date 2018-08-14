package dk.sdu.cloud.bare

import dk.sdu.cloud.bare.api.BareServiceDescription
import dk.sdu.cloud.client.JWTAuthenticatedCloud
import dk.sdu.cloud.service.*

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(BareServiceDescription, args)
        install(HibernateFeature)
    }

    val refreshToken = micro.configuration.requestChunkAtOrNull("refreshToken") ?: "not-a-real-token" // TODO
//    val cloud = RefreshingJWTAuthenticatedCloud(
//        K8CloudContext(),
//        configuration.refreshToken
//    )

    val cloud = JWTAuthenticatedCloud(
        micro.cloudContext,
        refreshToken
    )

    val server = Server(micro.kafka, cloud, micro.serverProvider)
    server.start()
}
