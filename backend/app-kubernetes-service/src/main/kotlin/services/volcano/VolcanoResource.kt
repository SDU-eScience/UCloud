package dk.sdu.cloud.app.kubernetes.services.volcano

import dk.sdu.cloud.service.k8.*

val KubernetesResources.volcanoJob get() = KubernetesResourceLocator("batch.volcano.sh", "v1alpha1", "jobs")

data class VolcanoJob(
    var apiVersion: String = "batch.volcano.sh/v1alpha1",
    var kind: String = "Job",
    var metadata: ObjectMeta? = null,
    var spec: Spec? = null,
    var status: Status? = null
) {
    data class Spec(
        var schedulerName: String? = null,
        var minAvailable: Int? = null,
        var volumes: List<VolumeSpec>? = emptyList(),
        var tasks: List<TaskSpec>? = emptyList(),
        var policies: List<LifeCyclePolicy>? = emptyList(),
        var plugins: Map<String, List<Any?>>? = null,
        var queue: String? = null,
        var maxRetry: Int? = null,
        var ttlSecondsAfterFinished: Int? = null,
        var priorityClassName: String? = null,
    )

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
        var controlledResources: Map<String, Any?>? = null,
    )

    data class State(
        var phase: String? = null,
        var reason: String? = null,
        var message: String? = null,
        var lastTransitionTime: KubernetesTimestamp? = null,
    )

    data class TaskSpec(
        var name: String? = null,
        var replicas: Int? = null,
        var template: Pod.SpecTemplate? = null,
        var policies: List<LifeCyclePolicy>? = emptyList(),
    )

    data class LifeCyclePolicy(
        var action: String? = null,
        var event: String? = null,
        var events: List<String>? = emptyList(),
        var exitCode: Int? = null,
        var timeout: String? = null,
    )

    data class VolumeSpec(
        var mountPath: String? = null,
        var volumeClaimName: String? = null,
    )
}

val KubernetesResources.volcanoQueue get() = KubernetesResourceLocator("scheduling.volcano.sh", "v1beta1", "queues")

data class VolcanoQueue(
    var apiVersion: String = "scheduling.volcano.sh/v1beta",
    var kind: String = "Queue",
    var metadata: ObjectMeta? = null,
    var spec: Spec? = null,
    var status: Status? = null,
) {
    data class Spec(
        var weight: Int? = null,
        var capability: ResourceList? = null,
        var reclaimable: Boolean? = null,
    )

    data class Status(
        var state: String? = null,
        var unknown: Int? = null,
        var pending: Int? = null,
        var inqueue: Int? = null,
    )
}