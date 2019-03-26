package dk.sdu.cloud.app.abacus

import dk.sdu.cloud.app.abacus.api.AppAbacusServiceDescription
import dk.sdu.cloud.app.abacus.services.ssh.SimpleSSHConfig
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.micro.initWithDefaultFeatures
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.runScriptHandler

data class HPCConfig(
    val ssh: SimpleSSHConfig,
    val slurmPollIntervalSeconds: Long = 15L,
    val workingDirectory: String = "/scratch/sduescience/jobs",
    val slurmAccount: String = "sduescience_slim",
    val udockerBinary: String = "/home/sducloudapps/bin/udocker-prep",
    val backendName: String = "abacus",
    val reservation: String? = null
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
        configuration,
        micro
    ).start()
}
