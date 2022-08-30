package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.Job
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Volcano uses namespaces to control fair-share (used to differentiate between different users). We
 * will use a namespace for every type of application owner. That is, if the owner is a user we will create a
 * namespace for them. If the application has a project attached to it we will use a namespace dedicated to
 * the project. Namespace creation is done, as needed, by the [FeatureFairShare].
 */
object FeatureFairShare : JobFeature {
    override suspend fun JobManagement.onCreate(job: Job, builder: ContainerBuilder) {
        builder.upsertAnnotation("ucloud.dk/user", job.owner.project ?: job.owner.createdBy)
    }
}
