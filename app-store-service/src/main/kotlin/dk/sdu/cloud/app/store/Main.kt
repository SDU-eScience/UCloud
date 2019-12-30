package dk.sdu.cloud.app.store

import dk.sdu.cloud.app.store.api.AppStoreServiceDescription
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.micro.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(AppStoreServiceDescription, args)
        install(HibernateFeature)
        install(RefreshingJWTCloudFeature)
        install(ElasticFeature)
        install(HealthCheckFeature)
    }

    if (micro.runScriptHandler()) return

    GlobalScope.launch {
        Server(micro).start()
    }
}
