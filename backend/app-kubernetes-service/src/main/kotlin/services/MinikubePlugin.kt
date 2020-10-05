package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.services.volcano.VOLCANO_JOB_NAME_LABEL
import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.k8.*

/**
 * A plugin which adds better support for minikube by adding a LoadBalancer service for the Volcano job
 */
class MinikubePlugin : JobManagementPlugin {
    override suspend fun JobManagement.onCreate(job: VerifiedJob, builder: VolcanoJob) {
        val namespace = k8.nameAllocator.jobIdToNamespace(job.id)
        val name = k8.nameAllocator.jobIdToJobName(job.id)

        val target = job.application.invocation.web?.port ?: job.application.invocation.vnc?.port ?: 80

        @Suppress("BlockingMethodInNonBlockingContext")
        k8.client.createResource(
            KubernetesResources.services.withNamespace(namespace),
            defaultMapper.writeValueAsString(
                Service(
                    metadata = ObjectMeta(
                        name + SERVICE_SUFFIX,
                        namespace
                    ),
                    spec = Service.Spec(
                        type = "LoadBalancer",
                        ports = listOf(
                            ServicePort(
                                name = "placeholder",
                                port = 80,
                                targetPort = target,
                                protocol = "TCP"
                            )
                        ),
                        selector = mapOf(
                            VOLCANO_JOB_NAME_LABEL to name,
                        )
                    )
                )
            )
        )
    }

    override suspend fun JobManagement.onCleanup(jobId: String) {
        val namespace = k8.nameAllocator.jobIdToNamespace(jobId)
        val name = k8.nameAllocator.jobIdToJobName(jobId)

        runCatching {
            k8.client.deleteResource(
                KubernetesResources.services.withNameAndNamespace(
                    name + SERVICE_SUFFIX,
                    namespace
                )
            )
        }
    }

    companion object {
        const val SERVICE_SUFFIX = "-mk"
    }
}