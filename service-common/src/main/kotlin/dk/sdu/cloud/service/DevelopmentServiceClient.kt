package dk.sdu.cloud.service

import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.PreparedRESTCall
import dk.sdu.cloud.client.SDUCloud
import dk.sdu.cloud.client.ServiceDescription
import org.slf4j.LoggerFactory
import java.net.ConnectException

class DevelopmentServiceClient(
    private val sduCloud: SDUCloud
) : CloudContext {
    override fun resolveEndpoint(call: PreparedRESTCall<*, *>): String {
        TODO()
    }

    override fun resolveEndpoint(service: ServiceDescription): String {
        TODO()
    }

    override fun tryReconfigurationOnConnectException(call: PreparedRESTCall<*, *>, ex: ConnectException): Boolean {
        TODO()
    }

    companion object : Loggable {
        override val log = logger()
    }
}

fun defaultServiceClient(
    cliArguments: Array<String>,
    cloudEndpoint: String = "https://cloud.sdu.dk"
): CloudContext {
    return if (cliArguments.contains("--dev")) {
        TODO()
    } else {
        DirectServiceClient()
    }
}