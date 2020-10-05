package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.service.k8.Pod
import kotlin.math.max

/**
 * A plugin which enables support for Kata Containers
 */
object KataContainerPlugin : JobManagementPlugin {
    override suspend fun JobManagement.onCreate(job: VerifiedJob, builder: VolcanoJob) {
        val tasks = builder.spec?.tasks ?: error("no volcano tasks")
        tasks.forEach { task ->
            val pTemplate = task.template ?: error("no template")
            val pSpec = pTemplate.spec ?: error("no pod spec in task")

            // All kata containers have a small overhead which is hardcoded into the initial container.
            // After the initial container is created new resources are hot-plugged into the VM. However, Kata
            // containers does not take into account the initial container's resources. As a result, we end up with too
            // large VMs. In this small snippet we remove the size of the initial container.
            //
            // NOTE(Dan): The size of the initial container depends on how much memory we need to support. The initial
            // container must have enough memory to support hot-plugging the new memory (The Linux kernel requires
            // memory to manage memory).
            pSpec.containers?.forEach { c ->
                val resources = c.resources ?: Pod.Container.ResourceRequirements()
                resources.limits = removeKataOverhead(job, resources.limits ?: emptyMap())
                resources.requests = removeKataOverhead(job, resources.requests ?: emptyMap())
                c.resources = resources
            }

            // Enable kata containers by setting an annotation on the pod itself
            val pMetadata = pTemplate.metadata ?: error("no metadata for pod template")
            (pMetadata.annotations?.toMutableMap() ?: HashMap()).let { annotations ->
                annotations["io.kubernetes.cri.untrusted-workload"] = "true"
                pMetadata.annotations = annotations
            }
        }
    }

    private fun removeKataOverhead(job: VerifiedJob, request: Map<String, Any?>): HashMap<String, Any?> {
        val clone = HashMap(request)
        val cpu = job.reservation.cpu
        if (cpu != null) {
            clone["cpu"] = "${max(1000, (cpu * 1000) - 1000)}m"
        }
        val mem = job.reservation.memoryInGigs
        if (mem != null) {
            clone["memory"] = "${max(1, mem - 6)}Gi"
        }
        return clone
    }
}