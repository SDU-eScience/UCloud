package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.Job
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.max

/**
 * A plugin which enables support for Kata Containers
 */
object FeatureKataContainer : JobFeature {
    override suspend fun JobManagement.onCreate(job: Job, builder: ContainerBuilder) {
        if (builder !is VolcanoContainerBuilder) return

        val tasks = builder.job.spec?.tasks ?: error("no volcano tasks")
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
                resources.limits = JsonObject(removeKataOverhead(job, resources.limits ?: emptyMap()))
                resources.requests = JsonObject(removeKataOverhead(job, resources.requests ?: emptyMap()))
                c.resources = resources
            }

            // Enable kata containers by setting an annotation on the pod itself
            val pMetadata = pTemplate.metadata ?: error("no metadata for pod template")
            (pMetadata.annotations?.toMutableMap() ?: HashMap()).let { annotations ->
                annotations["io.kubernetes.cri.untrusted-workload"] = JsonPrimitive("true")
                pMetadata.annotations = JsonObject(annotations)
            }
        }
    }

    private suspend fun JobManagement.removeKataOverhead(
        job: Job,
        request: Map<String, JsonElement>,
    ): HashMap<String, JsonElement> {
        val product = resources.findResources(job).product

        val clone = HashMap(request)
        val cpu = product.cpu
        if (cpu != null) {
            clone["cpu"] = JsonPrimitive("${max(1000, (cpu * 1000) - 1000)}m")
        }
        val mem = product.memoryInGigs
        if (mem != null) {
            clone["memory"] = JsonPrimitive("${max(1, mem - 6)}Gi")
        }
        return clone
    }
}
