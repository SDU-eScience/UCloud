package dk.sdu.cloud.transactions.dev

import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.ServiceDescription
import dk.sdu.cloud.transactions.ServiceDefinition
import dk.sdu.cloud.transactions.ServiceManifest
import io.netty.handler.codec.http.HttpMethod

object HttpBin : ServiceDescription {
    override val name: String = "httpbin"
    override val version: String = "1.0.0"

    val definition = ServiceDefinition(
            ServiceManifest(name, version),
            listOf(
                    object : RESTDescriptions(this) {
                        init {
                            register("{...}", HttpMethod.OPTIONS)
                            register("{...}", HttpMethod.GET)
                            register("{...}", HttpMethod.POST)
                            register("{...}", HttpMethod.HEAD)
                            register("{...}", HttpMethod.PUT)
                            register("{...}", HttpMethod.DELETE)
                        }
                    }
            ),
            emptyList()
    )
}


object TransferSh : ServiceDescription {
    override val name = "transfer.sh"
    override val version = "1.0.0"

    val definition = ServiceDefinition(
            ServiceManifest(name, version),
            listOf(
                    object : RESTDescriptions(this) {
                        init {
                            register("{...}", HttpMethod.OPTIONS)
                            register("{...}", HttpMethod.GET)
                            register("{...}", HttpMethod.POST)
                            register("{...}", HttpMethod.HEAD)
                            register("{...}", HttpMethod.PUT)
                            register("{...}", HttpMethod.DELETE)
                        }
                    }
            ),
            emptyList()
    )
}

