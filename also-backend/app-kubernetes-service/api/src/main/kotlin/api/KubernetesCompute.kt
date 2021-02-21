package dk.sdu.cloud.app.kubernetes.api

import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.app.orchestrator.api.Compute
import dk.sdu.cloud.app.orchestrator.api.Shells
import dk.sdu.cloud.calls.*

var integrationTestingIsKubernetesReady = true
var integrationTestingKubernetesFilePath: String? = null

@TSNamespace("compute.ucloud.jobs")
object KubernetesCompute : Compute(UCLOUD_PROVIDER)

@TSNamespace("compute.ucloud.shell")
object AppKubernetesShell : Shells(UCLOUD_PROVIDER)
