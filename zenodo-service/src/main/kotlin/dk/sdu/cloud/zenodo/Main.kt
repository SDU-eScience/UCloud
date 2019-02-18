package dk.sdu.cloud.zenodo

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.micro.initWithDefaultFeatures
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.runScriptHandler
import dk.sdu.cloud.zenodo.api.ZenodoServiceDescription

data class Configuration(
    val zenodo: ZenodoAPIConfiguration
)

data class ZenodoAPIConfiguration(
    val clientId: String,
    val clientSecret: String,
    val useSandbox: Boolean = true
) {
    override fun toString(): String {
        return "ZenodoAPIConfiguration()"
    }
}

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(ZenodoServiceDescription, args)
        install(HibernateFeature)
        install(RefreshingJWTCloudFeature)
    }

    if (micro.runScriptHandler()) return

    val configuration = micro.configuration.requestChunkAt<Configuration>("service")

    Server(configuration, micro).start()
}
