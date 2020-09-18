package dk.sdu.cloud.app.kubernetes.services.volcano

import dk.sdu.cloud.service.k8.*

val KubernetesResources.volcanoJob get() = KubernetesResourceLocator("batch.volcano.sh", "v1alpha1", "jobs")

data class VolcanoJob(
    val apiVersion: String = "batch.volcano.sh/v1alpha1",
    val kind: String = "Job",
    val metadata: ObjectMeta? = null,
    val spec: Spec? = null,
    val status: Status? = null
) {
    data class Spec(
        val schedulerName: String? = null,
        val minAvailable: String? = null,
        val volumes: List<VolumeSpec>? = null,
        val tasks: TaskSpec? = null,
        val policies: List<LifeCyclePolicy>? = null,
        val plugins: List<Map<String, Any?>>? = null,
        val queue: String? = null,
        val maxRetry: Int? = null,
        val ttlSecondsAfterFinished: Int? = null,
        val priorityClassName: String? = null,
    )

    data class Status(
        val state: State? = null,
        val minAvailable: Int? = null,
        val pending: Int? = null,
        val running: Int? = null,
        val succeeded: Int? = null,
        val failed: Int? = null,
        val terminating: Int? = null,
        val unknown: Int? = null,
        val version: Int? = null,
        val retryCount: Int? = null,
        val controlledResources: Map<String, Any?>? = null,
    )

    data class State(
        val phase: String? = null,
        val reason: String? = null,
        val message: String? = null,
        val lastTransitionTime: KubernetesTimestamp? = null,
    )

    data class TaskSpec(
        val name: String? = null,
        val replicas: Int? = null,
        val template: Pod.SpecTemplate? = null,
        val policies: LifeCyclePolicy? = null,
    )

    data class LifeCyclePolicy(
        val action: String? = null,
        val event: String? = null,
        val events: List<String>? = null,
        val exitCode: Int? = null,
        val timeout: String? = null,
    )

    data class VolumeSpec(
        val mountPath: String? = null,
        val volumeClaimName: String? = null,
    )
}

val KubernetesResources.volcanoQueue get() = KubernetesResourceLocator("scheduling.volcano.sh", "v1beta1", "queues")

data class VolcanoQueue(
    val apiVersion: String = "scheduling.volcano.sh/v1beta",
    val kind: String = "Queue",
    val metadata: ObjectMeta? = null,
    val spec: Spec? = null,
    val status: Status? = null,
) {
    data class Spec(
        val weight: Int? = null,
        val capability: ResourceList? = null,
        val reclaimable: Boolean? = null,
    )

    data class Status(
        val state: String? = null,
        val unknown: Int? = null,
        val pending: Int? = null,
        val inqueue: Int? = null,
    )
}