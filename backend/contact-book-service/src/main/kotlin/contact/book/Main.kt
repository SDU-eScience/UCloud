package dk.sdu.cloud.contact.book

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.contact.book.api.ContactBookServiceDescription
import dk.sdu.cloud.micro.*

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(ContactBookServiceDescription, args)
        install(RefreshingJWTCloudFeature)
        install(ElasticFeature)
        install(HealthCheckFeature)
    }

    if (micro.runScriptHandler()) return

    Server(micro).start()
}
