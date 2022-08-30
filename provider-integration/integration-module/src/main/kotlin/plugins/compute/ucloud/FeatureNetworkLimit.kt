package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.Job
import kotlinx.serialization.json.JsonObject

object FeatureNetworkLimit : JobFeature {
    override suspend fun JobManagement.onCreate(job: Job, builder: ContainerBuilder) {
        /*
        builder.upsertAnnotation("kubernetes.io/ingress-bandwidth", "1M")
        builder.upsertAnnotation("kubernetes.io/egress-bandwidth", "1M")
         */
    }
}
