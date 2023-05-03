package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time

object FeatureExpiry : JobFeature, Loggable {
    override val log = logger()
    const val MAX_TIME_ANNOTATION = "ucloud.dk/maxTime"
    const val EXPIRY_ANNOTATION = "ucloud.dk/expiry"
    const val JOB_START = "ucloud.dk/jobStart"

    override suspend fun JobManagement.onCreate(job: Job, builder: ContainerBuilder) {
        val maxTimeMillis = job.specification.timeAllocation?.toMillis() ?: return
        builder.upsertAnnotation(MAX_TIME_ANNOTATION, "$maxTimeMillis")
    }

    override suspend fun JobManagement.onJobStart(rootJob: Container, children: List<Container>) {
        val maxTime = rootJob.maxTime ?: return
        if (rootJob.jobStart != null) return // Job had already been started earlier

        val start = Time.now()
        val expiry = (start + maxTime)
        rootJob.upsertAnnotation(EXPIRY_ANNOTATION, "$expiry")
        rootJob.upsertAnnotation(JOB_START, "$start")
    }

    override suspend fun JobManagement.onJobMonitoring(jobBatch: Collection<Container>) {
        val now = Time.now()
        for (job in jobBatch) {
            if (job.rank != 0) continue
            val expiry = job.expiry ?: continue
            log.trace("expiry in ${expiry - now}")
            if (now >= expiry) {
                job.cancel()
            }
        }
    }

    suspend fun JobManagement.extendJob(rootJob: Container, extendBy: SimpleDuration) {
        val maxTime = rootJob.maxTime
        if (maxTime != null) {
            k8.updateTimeAllocation(rootJob.jobId, maxTime + extendBy.toMillis())
            rootJob.upsertAnnotation(MAX_TIME_ANNOTATION, (maxTime + extendBy.toMillis()).toString())
        }

        val jobExpiry = rootJob.expiry
        if (jobExpiry != null) {
            rootJob.upsertAnnotation(EXPIRY_ANNOTATION, (jobExpiry + extendBy.toMillis()).toString())
        }
    }
}

val Container.maxTime: Long?
    get() {
        return annotations[FeatureExpiry.MAX_TIME_ANNOTATION]?.replace("\\\"", "")?.replace("\"", "")?.toLongOrNull()
    }

val Container.jobStart: Long?
    get() {
        return annotations[FeatureExpiry.JOB_START]?.replace("\\\"", "")?.replace("\"", "")?.toLongOrNull()
    }

val Container.expiry: Long?
    get() {
        return annotations[FeatureExpiry.EXPIRY_ANNOTATION]?.replace("\\\"", "")?.replace("\"", "")?.toLongOrNull()
    }
