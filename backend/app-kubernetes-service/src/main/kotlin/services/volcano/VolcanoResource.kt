package dk.sdu.cloud.app.kubernetes.services.volcano

import dk.sdu.cloud.service.k8.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

val KubernetesResources.volcanoJob get() = KubernetesResourceLocator("batch.volcano.sh", "v1alpha1", "jobs")

@Serializable
data class VolcanoJob(
    var apiVersion: String = "batch.volcano.sh/v1alpha1",
    var kind: String = "Job",
    var metadata: ObjectMeta? = null,
    var spec: Spec? = null,
    var status: Status? = null
) {
    @Serializable
    data class Spec(
        var schedulerName: String? = null,
        var minAvailable: Int? = null,
        var volumes: List<VolumeSpec>? = emptyList(),
        var tasks: List<TaskSpec>? = emptyList(),
        var policies: List<LifeCyclePolicy>? = emptyList(),
        var plugins: JsonObject? = null,
        var queue: String? = null,
        var maxRetry: Int? = null,
        var ttlSecondsAfterFinished: Int? = null,
        var priorityClassName: String? = null,
    )

    @Serializable
    data class Status(
        var state: State? = null,
        var minAvailable: Int? = null,
        var pending: Int? = null,
        var running: Int? = null,
        var succeeded: Int? = null,
        var failed: Int? = null,
        var terminating: Int? = null,
        var unknown: Int? = null,
        var version: Int? = null,
        var retryCount: Int? = null,
        var controlledResources: JsonObject? = null,
    )

    @Serializable
    data class State(
        var phase: String? = null,
        var reason: String? = null,
        var message: String? = null,
        var lastTransitionTime: KubernetesTimestamp? = null,
    )

    @Serializable
    data class TaskSpec(
        var name: String? = null,
        var replicas: Int? = null,
        var template: Pod.SpecTemplate? = null,
        var policies: List<LifeCyclePolicy>? = emptyList(),
    )

    @Serializable
    data class LifeCyclePolicy(
        var action: String? = null,
        var event: String? = null,
        var events: List<String>? = emptyList(),
        var exitCode: Int? = null,
        var timeout: String? = null,
    )

    @Serializable
    data class VolumeSpec(
        var mountPath: String? = null,
        var volumeClaimName: String? = null,
    )
}

val KubernetesResources.volcanoQueue get() = KubernetesResourceLocator("scheduling.volcano.sh", "v1beta1", "queues")

@Serializable
data class VolcanoQueue(
    var apiVersion: String = "scheduling.volcano.sh/v1beta",
    var kind: String = "Queue",
    var metadata: ObjectMeta? = null,
    var spec: Spec? = null,
    var status: Status? = null,
) {
    @Serializable
    data class Spec(
        var weight: Int? = null,
        var capability: ResourceList? = null,
        var reclaimable: Boolean? = null,
    )

    @Serializable
    data class Status(
        var state: String? = null,
        var unknown: Int? = null,
        var pending: Int? = null,
        var inqueue: Int? = null,
    )
}

/**
 * Contains well known Volcano job phases
 *
 * We are not using an enum here since these are likely not super stable. Code should handle job phases which do not
 * fall into one of well known phases.
 */
object VolcanoJobPhase {
    /** Pending is the phase that job is pending in the queue, waiting for scheduling decision */
    const val Pending = "Pending"
    /** Aborting is the phase that job is aborted, waiting for releasing pods */
    const val Aborting = "Aborting"
    /** Aborted is the phase that job is aborted by user or error handling */
    const val Aborted = "Aborted"
    /** Running is the phase that minimal available tasks of Job are running */
    const val Running = "Running"
    /** Restarting is the phase that the Job is restarted, waiting for pod releasing and recreating */
    const val Restarting = "Restarting"
    /** Completing is the phase that required tasks of job are completed, job starts to clean up */
    const val Completing = "Completing"
    /** Completed is the phase that all tasks of Job are completed */
    const val Completed = "Completed"
    /** Terminating is the phase that the Job is terminated, waiting for releasing pods */
    const val Terminating = "Terminating"
    /** Terminated is the phase that the job is finished unexpected, e.g. events */
    const val Terminated = "Terminated"
    /** Failed is the phase that the job is restarted failed reached the maximum number of retries. */
    const val Failed = "Failed"
}

const val VOLCANO_JOB_NAME_LABEL = "volcano.sh/job-name"
