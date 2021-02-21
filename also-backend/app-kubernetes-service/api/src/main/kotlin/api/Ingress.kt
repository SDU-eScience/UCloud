package dk.sdu.cloud.app.kubernetes.api

import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.app.orchestrator.api.IngressProvider
import dk.sdu.cloud.calls.TSNamespace

@TSNamespace("compute.ucloud.ingresses")
object KubernetesIngresses : IngressProvider(UCLOUD_PROVIDER)
