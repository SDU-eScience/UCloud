package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.k8.*

/**
 * Volcano uses namespaces to control fair-share (used to differentiate between different users). We
 * will use a namespace for every type of application owner. That is, if the owner is a user we will create a
 * namespace for them. If the application has a project attached to it we will use a namespace dedicated to
 * the project. Namespace creation is done, as needed, by the [FairSharePlugin].
 */
class FairSharePlugin : JobManagementPlugin {
    override suspend fun JobManagement.onCreate(job: VerifiedJob, builder: VolcanoJob) {
        val namespace = k8.nameAllocator.jobIdToNamespace(job.id)
        @Suppress("BlockingMethodInNonBlockingContext")
        try {
            k8.client.createResource(
                KubernetesResources.namespaces,
                defaultMapper.writeValueAsString(Namespace(metadata = ObjectMeta(namespace)))
            )
        } catch (ex: KubernetesException) {
            if (ex.statusCode.value in setOf(400, 404, 409)) {
                // Expected
            } else {
                throw ex
            }
        }
    }
}
