package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.app.kubernetes.api.AppKubernetesServiceDescription
import dk.sdu.cloud.micro.*

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(AppKubernetesServiceDescription, args)
        install(HibernateFeature)
        install(RefreshingJWTCloudFeature)
    }

    if (micro.runScriptHandler()) return

    Server(micro).start()
}
