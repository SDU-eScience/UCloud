package dk.sdu.cloud.app.kubernetes.api

import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.app.orchestrator.api.JobsProvider
import dk.sdu.cloud.app.orchestrator.api.Shells
import dk.sdu.cloud.calls.*

object AppK8IntegrationTesting {
    var isKubernetesReady = true
    var kubernetesConfigFilePath: String? = null

    var isProviderReady = true
    var providerRefreshToken: String? = null
    var providerCertificate: String? = null
}

@TSNamespace("compute.ucloud.jobs")
object KubernetesCompute : JobsProvider(UCLOUD_PROVIDER)

@TSNamespace("compute.ucloud.shell")
object AppKubernetesShell : Shells(UCLOUD_PROVIDER)
