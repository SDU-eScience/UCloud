package dk.sdu.cloud.bare

import dk.sdu.cloud.bare.api.BareServiceDescription
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.JWTAuthenticatedCloud
import dk.sdu.cloud.client.PreparedRESTCall
import dk.sdu.cloud.client.ServiceDescription
import dk.sdu.cloud.service.*
import org.hibernate.Hibernate
import org.slf4j.LoggerFactory
import java.net.ConnectException

private val log = LoggerFactory.getLogger("dk.sdu.cloud.bare.MainKt")

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
        K8CloudContext(),
        refreshToken
    )

    val server = Server(micro.kafka, cloud, micro.serverProvider)
    server.start()
}

class K8CloudContext : CloudContext {
    override fun resolveEndpoint(call: PreparedRESTCall<*, *>): String {
        return resolveEndpoint(call.owner)
    }

    override fun resolveEndpoint(service: ServiceDescription): String {
        return "http://${service.name}:8080"
    }

    override fun tryReconfigurationOnConnectException(call: PreparedRESTCall<*, *>, ex: ConnectException): Boolean {
        return false
    }
}