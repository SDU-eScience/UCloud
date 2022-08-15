package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A plugin which adds better support for minikube by adding a LoadBalancer service for the Volcano job
 */
object FeatureMinikube : JobFeature, Loggable {
    override val log = logger()
    const val SERVICE_SUFFIX = "-mk"

    override suspend fun JobManagement.onCreate(job: Job, builder: VolcanoJob) {
        val namespace = k8.nameAllocator.namespace()
        val name = k8.nameAllocator.jobIdToJobName(job.id)
        val application = resources.findResources(job).application

        val target = application.invocation.web?.port ?: application.invocation.vnc?.port ?: 80

        runCatching {
            k8.client.deleteResource(KubernetesResources.services.withNameAndNamespace(name + SERVICE_SUFFIX, namespace))
        }

        @Suppress("BlockingMethodInNonBlockingContext")
        k8.client.createResource(
            KubernetesResources.services.withNamespace(namespace),
            defaultMapper.encodeToString(
                Service.serializer(),
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
                                targetPort = JsonPrimitive(target),
                                protocol = "TCP"
                            )
                        ),
                        selector = JsonObject(
                            mapOf(
                                VOLCANO_JOB_NAME_LABEL to JsonPrimitive(name),
                            )
                        )
                    )
                )
            )
        )
    }

    override suspend fun JobManagement.onCleanup(jobId: String) {
        val namespace = k8.nameAllocator.namespace()
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

}
