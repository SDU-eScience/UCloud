package dk.sdu.cloud.contact.book

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.contact.book.api.ContactBookServiceDescription
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

object ContactBookService : Service {
    override val description: ServiceDescription = ContactBookServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)
        micro.install(ElasticFeature)

        return Server(micro)
    }
}

fun main(args: Array<String>) {
    ContactBookService.runAsStandalone(args)
}
