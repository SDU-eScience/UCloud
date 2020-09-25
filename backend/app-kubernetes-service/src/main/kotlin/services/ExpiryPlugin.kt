package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.kubernetes.services.volcano.volcanoJob
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.k8.KubernetesException
import dk.sdu.cloud.service.k8.KubernetesResources
import dk.sdu.cloud.service.k8.deleteResource
import dk.sdu.cloud.service.k8.patchResource
import io.ktor.http.*

class ExpiryPlugin : JobManagementPlugin {
    override suspend fun JobManagement.onCreate(job: VerifiedJob, builder: VolcanoJob) {
        val maxTimeMillis = job.maxTime.toMillis()
        val jobMetadata = builder.metadata ?: error("no metadata")
        (jobMetadata.annotations?.toMutableMap() ?: HashMap()).let { annotations ->
            annotations[MAX_TIME_ANNOTATION] = "$maxTimeMillis"
            jobMetadata.annotations = annotations
        }
    }

    override suspend fun JobManagement.onJobStart(jobId: String, jobFromServer: VolcanoJob) {
        val metadata = jobFromServer.metadata ?: return
        val maxTime = metadata.annotations?.get(MAX_TIME_ANNOTATION)?.toString()?.toLongOrNull()
            ?: error("no max time annotation on volcano job! $jobId $jobFromServer")

        val start = Time.now()
        val expiry = (start + maxTime)
        k8.client.patchResource(
            KubernetesResources.volcanoJob.withNameAndNamespace(
                metadata.name ?: error("no name"),
                metadata.namespace ?: error("no namespace")
            ),
            defaultMapper.writeValueAsString(
                // http://jsonpatch.com/
                listOf(
                    mapOf(
                        "op" to "add",
                        // https://tools.ietf.org/html/rfc6901#section-3
                        "path" to "/metadata/annotations/${EXPIRY_ANNOTATION.replace("/", "~1")}",
                        "value" to expiry.toString()
                    ),
                    mapOf(
                        "op" to "add",
                        "path" to "/metadata/annotations/${JOB_START.replace("/", "~1")}",
                        "value" to start.toString()
                    )
                )
            ),
            ContentType("application", "json-patch+json")
        )

        scheduleJobMonitoring(jobId, expiry)
    }

    override suspend fun JobManagement.onJobMonitoring(jobBatch: Collection<VolcanoJob>) {
        val now = Time.now()
        for (job in jobBatch) {
            val metadata = job.metadata ?: continue
            val name = metadata.name ?: error("no name")
            val namespace = metadata.namespace ?: error("no namespace")
            val expiry = metadata.annotations?.get(EXPIRY_ANNOTATION)?.toString()?.toLongOrNull() ?: continue
            if (now >= expiry) {
                k8.client.deleteResource(KubernetesResources.volcanoJob.withNameAndNamespace(name, namespace))
            } else {
                scheduleJobMonitoring(k8.nameAllocator.jobNameToJobId(name), expiry)
            }
        }
    }

    companion object : Loggable {
        const val MAX_TIME_ANNOTATION = "ucloud.dk/maxTime"
        const val EXPIRY_ANNOTATION = "ucloud.dk/expiry"
        const val JOB_START = "ucloud.dk/jobStart"
        override val log = logger()
    }
}