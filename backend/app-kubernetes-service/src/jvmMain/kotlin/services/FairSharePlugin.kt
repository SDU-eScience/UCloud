package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.k8.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Volcano uses namespaces to control fair-share (used to differentiate between different users). We
 * will use a namespace for every type of application owner. That is, if the owner is a user we will create a
 * namespace for them. If the application has a project attached to it we will use a namespace dedicated to
 * the project. Namespace creation is done, as needed, by the [FairSharePlugin].
 */
object FairSharePlugin : JobManagementPlugin {
    override suspend fun JobManagement.onCreate(job: Job, builder: VolcanoJob) {
        val jobMetadata = builder.metadata ?: error("no metadata")
        (jobMetadata.annotations?.toMutableMap() ?: HashMap()).let { annotations ->
            annotations["ucloud.dk/user"] = JsonPrimitive(job.owner.project ?: job.owner.createdBy)
            jobMetadata.annotations = JsonObject(annotations)
        }
    }
}
