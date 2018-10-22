package dk.sdu.cloud.app.abacus

import dk.sdu.cloud.app.abacus.api.AppAbacusServiceDescription
import dk.sdu.cloud.app.abacus.services.ssh.SimpleSSHConfig
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.auth.api.refreshingJwtCloud
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.configuration
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.initWithDefaultFeatures
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.kafka
import dk.sdu.cloud.service.runScriptHandler
import dk.sdu.cloud.service.serverProvider
import dk.sdu.cloud.service.serviceInstance

data class HPCConfig(
    val ssh: SimpleSSHConfig,
    val slurmPollIntervalSeconds: Long = 15L,
    val workingDirectory: String = "/scratch/sduescience/jobs"
)

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(AppAbacusServiceDescription, args)
        install(HibernateFeature)
        install(RefreshingJWTCloudFeature)
    }

    if (micro.runScriptHandler()) return

    val configuration = micro.configuration.requestChunkAt<HPCConfig>("hpc")

    Server(
        micro.kafka,
        micro.refreshingJwtCloud,
        configuration,
        micro.serverProvider,
        micro.hibernateDatabase,
        micro.serviceInstance
    ).start()
}
