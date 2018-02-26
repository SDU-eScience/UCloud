package dk.sdu.cloud.service

import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.PreparedRESTCall
import dk.sdu.cloud.client.SDUCloud
import dk.sdu.cloud.client.ServiceDescription
import org.slf4j.LoggerFactory
import java.net.ConnectException

class DevelopmentServiceClient(
    private val directServiceClient: DirectServiceClient,
    private val sduCloud: SDUCloud
) : CloudContext {
    override fun resolveEndpoint(call: PreparedRESTCall<*, *>): String {
        return try {
            val result = directServiceClient.resolveEndpoint(call)
            log.debug("Using local service for call: $call")
            result
        } catch (ex: Exception) {
            log.debug("Using remote service for call: $call")
            sduCloud.resolveEndpoint(call)
        }
    }

    override fun resolveEndpoint(service: ServiceDescription): String {
        return try {
            val result = directServiceClient.resolveEndpoint(service)
            log.debug("Using local service for call: $service")
            result
        } catch (ex: Exception) {
            log.debug("Using remote service for call: $service")
            sduCloud.resolveEndpoint(service)
        }
    }

    override fun tryReconfigurationOnConnectException(call: PreparedRESTCall<*, *>, ex: ConnectException): Boolean {
        return directServiceClient.tryReconfigurationOnConnectException(call, ex)
    }

    companion object {
        private val log = LoggerFactory.getLogger(DevelopmentServiceClient::class.java)
    }
}

fun defaultServiceClient(
    cliArguments: Array<String>,
    serviceRegistry: ServiceRegistry,
    cloudEndpoint: String = "https://cloud.sdu.dk"
): CloudContext {
    return if (cliArguments.contains("--dev")) {
        DevelopmentServiceClient(DirectServiceClient(serviceRegistry), SDUCloud(cloudEndpoint))
    } else {
        DirectServiceClient(serviceRegistry)
    }
}