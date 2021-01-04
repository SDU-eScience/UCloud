package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.kubernetes.services.volcano.volcanoJob
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.k8.*
import io.ktor.http.*

object ExpiryPlugin : JobManagementPlugin, Loggable {
    override val log = logger()
    const val MAX_TIME_ANNOTATION = "ucloud.dk/maxTime"
    const val EXPIRY_ANNOTATION = "ucloud.dk/expiry"
    const val JOB_START = "ucloud.dk/jobStart"

    override suspend fun JobManagement.onCreate(job: Job, builder: VolcanoJob) {
        // TODO Some jobs might actually not have a time allocation
        val maxTimeMillis = job.parameters.timeAllocation?.toMillis() ?: error("time allocation required")
        val jobMetadata = builder.metadata ?: error("no metadata")
        (jobMetadata.annotations?.toMutableMap() ?: HashMap()).let { annotations ->
            annotations[MAX_TIME_ANNOTATION] = "$maxTimeMillis"
            jobMetadata.annotations = annotations
        }
    }

    override suspend fun JobManagement.onJobStart(jobId: String, jobFromServer: VolcanoJob) {
        val metadata = jobFromServer.metadata ?: return
        val maxTime = jobFromServer.maxTime ?: error("no max time attached to job: $jobId")
        if (jobFromServer.jobStart != null) return // Job had already been started earlier

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
            log.debug("looking at $name")
            val expiry = metadata.annotations?.get(EXPIRY_ANNOTATION)?.toString()?.toLongOrNull() ?: continue
            log.debug("expiry in ${expiry - now}")
            if (now >= expiry) {
                k8.client.deleteResource(KubernetesResources.volcanoJob.withNameAndNamespace(name, namespace))
            } else {
                scheduleJobMonitoring(k8.nameAllocator.jobNameToJobId(name), expiry)
            }
        }
    }

    suspend fun extendJob(k8: K8Dependencies, jobId: String, newMaxTime: SimpleDuration) {
        val name = k8.nameAllocator.jobIdToJobName(jobId)
        val namespace = k8.nameAllocator.jobIdToNamespace(jobId)

        val job = k8.client.getResource<VolcanoJob>(
            KubernetesResources.volcanoJob.withNameAndNamespace(name, namespace)
        )

        val ops = ArrayList<Map<String, Any?>>()
        ops.add(
            mapOf(
                "op" to "replace",
                "path" to "/metadata/annotations/${MAX_TIME_ANNOTATION.replace("/", "~1")}",
                "value" to newMaxTime.toMillis().toString()
            )
        )

        val jobStart = job.jobStart
        if (jobStart != null) {
            ops.add(
                mapOf(
                    "op" to "replace",
                    "path" to "/metadata/annotations/${EXPIRY_ANNOTATION.replace("/", "~1")}",
                    "value" to (jobStart + newMaxTime.toMillis()).toString()
                )
            )
        }

        k8.client.patchResource(
            KubernetesResources.volcanoJob.withNameAndNamespace(
                name,
                namespace
            ),
            defaultMapper.writeValueAsString(ops),
            ContentType("application", "json-patch+json")
        )
    }
}

val VolcanoJob.maxTime: Long? get() {
    return metadata?.annotations?.get(ExpiryPlugin.MAX_TIME_ANNOTATION)?.toString()?.toLongOrNull()
}

val VolcanoJob.jobStart: Long? get() {
    return metadata?.annotations?.get(ExpiryPlugin.JOB_START)?.toString()?.toLongOrNull()
}

val VolcanoJob.expiry: Long? get() {
    return metadata?.annotations?.get(ExpiryPlugin.EXPIRY_ANNOTATION)?.toString()?.toLongOrNull()
}