package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.app.kubernetes.api.AppKubernetesServiceDescription
import dk.sdu.cloud.micro.*

data class Configuration(
    val cookieName: String = "appRefreshToken",
    val prefix: String = "app-",
    val domain: String = "cloud.sdu.dk",
    val performAuthentication: Boolean = true
)

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(AppKubernetesServiceDescription, args)
        install(HibernateFeature)
        install(RefreshingJWTCloudFeature)
    }

    if (micro.runScriptHandler()) return

    val configuration = micro.configuration.requestChunkAtOrNull("app", "kubernetes") ?: Configuration()

    Server(micro, configuration).start()
}
