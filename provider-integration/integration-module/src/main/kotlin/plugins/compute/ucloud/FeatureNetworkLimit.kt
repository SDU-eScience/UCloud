package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.Job
import kotlinx.serialization.json.JsonObject

object FeatureNetworkLimit : JobFeature {
    override suspend fun JobManagement.onCreate(job: Job, builder: VolcanoJob) {
        val tasks = builder.spec?.tasks ?: error("no volcano tasks")

        tasks.forEach { task ->
            val template = task.template ?: error("no task template")

            val pMetadata = template.metadata ?: error("no metadata for pod template")
            (pMetadata.annotations?.toMutableMap() ?: HashMap()).let { annotations ->
                /*
                annotations["kubernetes.io/ingress-bandwidth"] = "1M"
                annotations["kubernetes.io/egress-bandwidth"] = "1M"
                 */
                pMetadata.annotations = JsonObject(annotations)
            }
        }
    }
}
