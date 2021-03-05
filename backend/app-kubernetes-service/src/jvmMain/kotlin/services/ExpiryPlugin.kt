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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object ExpiryPlugin : JobManagementPlugin, Loggable {
    override val log = logger()
    const val MAX_TIME_ANNOTATION = "ucloud.dk/maxTime"
    const val EXPIRY_ANNOTATION = "ucloud.dk/expiry"
    const val JOB_START = "ucloud.dk/jobStart"

    override suspend fun JobManagement.onCreate(job: Job, builder: VolcanoJob) {
        // TODO Some jobs might actually not have a time allocation
        val maxTimeMillis = job.specification.timeAllocation?.toMillis() ?: error("time allocation required")
        val jobMetadata = builder.metadata ?: error("no metadata")
        (jobMetadata.annotations?.toMutableMap() ?: HashMap()).let { annotations ->
            annotations[MAX_TIME_ANNOTATION] = JsonPrimitive("$maxTimeMillis")
            jobMetadata.annotations = JsonObject(annotations)
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
            defaultMapper.encodeToString(
                // http://jsonpatch.com/
                listOf(
                    JsonObject(
                        mapOf(
                            "op" to JsonPrimitive("add"),
                            // https://tools.ietf.org/html/rfc6901#section-3
                            "path" to JsonPrimitive("/metadata/annotations/${EXPIRY_ANNOTATION.replace("/", "~1")}"),
                            "value" to JsonPrimitive(expiry.toString())
                        )
                    ),
                    JsonObject(
                        mapOf(
                            "op" to JsonPrimitive("add"),
                            "path" to JsonPrimitive("/metadata/annotations/${JOB_START.replace("/", "~1")}"),
                            "value" to JsonPrimitive(start.toString())
                        )
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
            val expiry = (metadata.annotations?.get(EXPIRY_ANNOTATION) as? JsonPrimitive)?.content?.toLongOrNull()
                ?: continue
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

        val ops = ArrayList<JsonObject>()
        ops.add(
            JsonObject(
                mapOf(
                    "op" to JsonPrimitive("replace"),
                    "path" to JsonPrimitive("/metadata/annotations/${MAX_TIME_ANNOTATION.replace("/", "~1")}"),
                    "value" to JsonPrimitive(newMaxTime.toMillis().toString())
                )
            )
        )

        val jobStart = job.jobStart
        if (jobStart != null) {
            ops.add(
                JsonObject(
                    mapOf(
                        "op" to JsonPrimitive("replace"),
                        "path" to JsonPrimitive("/metadata/annotations/${EXPIRY_ANNOTATION.replace("/", "~1")}"),
                        "value" to JsonPrimitive((jobStart + newMaxTime.toMillis()).toString())
                    )
                )
            )
        }

        k8.client.patchResource(
            KubernetesResources.volcanoJob.withNameAndNamespace(
                name,
                namespace
            ),
            defaultMapper.encodeToString(ops),
            ContentType("application", "json-patch+json")
        )
    }
}

val VolcanoJob.maxTime: Long?
    get() {
        return (metadata?.annotations?.get(ExpiryPlugin.MAX_TIME_ANNOTATION) as? JsonPrimitive)?.content?.toLongOrNull()
    }

val VolcanoJob.jobStart: Long?
    get() {
        return (metadata?.annotations?.get(ExpiryPlugin.JOB_START) as? JsonPrimitive)?.content?.toLongOrNull()
    }

val VolcanoJob.expiry: Long?
    get() {
        return (metadata?.annotations?.get(ExpiryPlugin.EXPIRY_ANNOTATION) as? JsonPrimitive)?.content?.toLongOrNull()
    }