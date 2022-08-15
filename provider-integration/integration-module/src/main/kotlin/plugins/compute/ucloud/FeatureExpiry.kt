package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import io.ktor.http.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object FeatureExpiry : JobFeature, Loggable {
    override val log = logger()
    const val MAX_TIME_ANNOTATION = "ucloud.dk/maxTime"
    const val EXPIRY_ANNOTATION = "ucloud.dk/expiry"
    const val JOB_START = "ucloud.dk/jobStart"

    override suspend fun JobManagement.onCreate(job: Job, builder: VolcanoJob) {
        val maxTimeMillis = job.specification.timeAllocation?.toMillis() ?: return
        val jobMetadata = builder.metadata ?: error("no metadata")
        (jobMetadata.annotations?.toMutableMap() ?: HashMap()).let { annotations ->
            annotations[MAX_TIME_ANNOTATION] = JsonPrimitive("$maxTimeMillis")
            jobMetadata.annotations = JsonObject(annotations)
        }
    }

    override suspend fun JobManagement.onJobStart(jobId: String, jobFromServer: VolcanoJob) {
        val metadata = jobFromServer.metadata ?: return
        val maxTime = jobFromServer.maxTime ?: return
        if (jobFromServer.jobStart != null) return // Job had already been started earlier

        val start = Time.now()
        val expiry = (start + maxTime)
        k8.client.patchResource(
            KubernetesResources.volcanoJob.withNameAndNamespace(
                metadata.name ?: error("no name"),
                metadata.namespace ?: error("no namespace")
            ),
            defaultMapper.encodeToString(
                ListSerializer(JsonObject.serializer()),
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
    }

    override suspend fun JobManagement.onJobMonitoring(jobBatch: Collection<VolcanoJob>) {
        val now = Time.now()
        for (job in jobBatch) {
            val metadata = job.metadata ?: continue
            val name = metadata.name ?: error("no name")
            val namespace = metadata.namespace ?: error("no namespace")
            log.trace("looking at $name")
            val expiry = (metadata.annotations?.get(EXPIRY_ANNOTATION) as? JsonPrimitive)?.content?.toLongOrNull()
                ?: continue
            log.trace("expiry in ${expiry - now}")
            if (now >= expiry) {
                k8.client.deleteResource(KubernetesResources.volcanoJob.withNameAndNamespace(name, namespace))
            }
        }
    }

    suspend fun extendJob(k8: K8Dependencies, jobId: String, extendBy: SimpleDuration) {
        val name = k8.nameAllocator.jobIdToJobName(jobId)
        val namespace = k8.nameAllocator.namespace()

        val job = k8.client.getResource(
            VolcanoJob.serializer(),
            KubernetesResources.volcanoJob.withNameAndNamespace(name, namespace)
        )

        val ops = ArrayList<JsonObject>()

        val maxTime = job.maxTime
        if (maxTime != null) {
            k8.updateTimeAllocation(jobId, maxTime + extendBy.toMillis())
            ops.add(
                JsonObject(
                    mapOf(
                        "op" to JsonPrimitive("replace"),
                        "path" to JsonPrimitive("/metadata/annotations/${MAX_TIME_ANNOTATION.replace("/", "~1")}"),
                        "value" to JsonPrimitive((maxTime + extendBy.toMillis()).toString())
                    )
                )
            )
        }

        val jobExpiry = job.expiry
        if (jobExpiry != null) {
            ops.add(
                JsonObject(
                    mapOf(
                        "op" to JsonPrimitive("replace"),
                        "path" to JsonPrimitive("/metadata/annotations/${EXPIRY_ANNOTATION.replace("/", "~1")}"),
                        "value" to JsonPrimitive((jobExpiry + extendBy.toMillis()).toString())
                    )
                )
            )
        }

        k8.client.patchResource(
            KubernetesResources.volcanoJob.withNameAndNamespace(
                name,
                namespace
            ),
            defaultMapper.encodeToString(ListSerializer(JsonObject.serializer()), ops),
            ContentType("application", "json-patch+json")
        )
    }
}

val VolcanoJob.maxTime: Long?
    get() {
        return (metadata?.annotations?.get(FeatureExpiry.MAX_TIME_ANNOTATION) as? JsonPrimitive)?.content?.toLongOrNull()
    }

val VolcanoJob.jobStart: Long?
    get() {
        return (metadata?.annotations?.get(FeatureExpiry.JOB_START) as? JsonPrimitive)?.content?.toLongOrNull()
    }

val VolcanoJob.expiry: Long?
    get() {
        return (metadata?.annotations?.get(FeatureExpiry.EXPIRY_ANNOTATION) as? JsonPrimitive)?.content?.toLongOrNull()
    }
